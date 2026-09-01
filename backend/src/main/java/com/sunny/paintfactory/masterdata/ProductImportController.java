package com.sunny.paintfactory.masterdata;

import com.sunny.paintfactory.common.ApiResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/products")
public class ProductImportController {
    private final JdbcTemplate jdbc;
    public ProductImportController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String,Object>> importCsv(@RequestPart("file") MultipartFile file,Authentication auth)throws Exception{
        if(file.isEmpty())throw bad("请选择 CSV 文件");
        String text=new String(file.getBytes(),StandardCharsets.UTF_8).replace("\uFEFF","");
        List<List<String>> records=parseCsv(text);
        if(records.size()<2)throw bad("CSV 至少需要表头和一行数据");
        if(records.size()>5001)throw bad("单次最多导入 5000 行");
        Map<String,Integer> columns=columns(records.get(0));
        for(String required:List.of("skuCode","productName","salesUnit"))if(!columns.containsKey(required))throw bad("CSV 缺少必填列："+required);
        long uid=userId(auth);int inserted=0,updated=0,failed=0;List<Map<String,Object>> errors=new ArrayList<>();
        for(int i=1;i<records.size();i++){
            List<String> row=records.get(i);if(row.stream().allMatch(String::isBlank))continue;
            try{
                String code=value(row,columns,"skuCode"),name=value(row,columns,"productName"),unit=value(row,columns,"salesUnit");
                if(code.isBlank()||name.isBlank()||unit.isBlank())throw new IllegalArgumentException("编号、品名和单位不能为空");
                Object[] values={name,blank(value(row,columns,"specification")),blank(value(row,columns,"color")),unit,decimal(row,columns,"packageSpec"),blank(value(row,columns,"packageUnit")),decimal(row,columns,"wholesalePrice"),decimal(row,columns,"retailPrice"),zero(decimal(row,columns,"totalStock")),zero(decimal(row,columns,"stockLowerLimit")),decimal(row,columns,"lastPurchasePrice")};
                List<Long> ids=jdbc.query("SELECT id FROM product_sku WHERE sku_code=?",(rs,n)->rs.getLong(1),code);
                LocalDateTime now=LocalDateTime.now();
                if(ids.isEmpty()){
                    jdbc.update("INSERT INTO product_sku(sku_code,product_name,specification,color,sales_unit,package_spec,package_unit,wholesale_price,retail_price,total_stock,stock_lower_limit,last_purchase_price,enabled,version,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,1,0,?,?,?,?)",code,values[0],values[1],values[2],values[3],values[4],values[5],values[6],values[7],values[8],values[9],values[10],uid,now,uid,now);inserted++;
                }else{
                    jdbc.update("UPDATE product_sku SET product_name=?,specification=?,color=?,sales_unit=?,package_spec=?,package_unit=?,wholesale_price=?,retail_price=?,stock_lower_limit=?,last_purchase_price=?,enabled=1,version=version+1,updated_by=?,updated_at=? WHERE id=?",values[0],values[1],values[2],values[3],values[4],values[5],values[6],values[7],values[9],values[10],uid,now,ids.get(0));updated++;
                }
            }catch(Exception ex){failed++;if(errors.size()<50)errors.add(Map.of("row",i+1,"message",ex.getMessage()==null?"数据格式错误":ex.getMessage()));}
        }
        return ApiResponse.success(Map.of("inserted",inserted,"updated",updated,"failed",failed,"errors",errors));
    }

    private static Map<String,Integer> columns(List<String> header){Map<String,Integer> result=new HashMap<>();for(int i=0;i<header.size();i++)result.put(header.get(i).trim(),i);return result;}
    private static String value(List<String> row,Map<String,Integer> columns,String name){Integer index=columns.get(name);return index==null||index>=row.size()?"":row.get(index).trim();}
    private static BigDecimal decimal(List<String> row,Map<String,Integer> columns,String name){String value=value(row,columns,name);return value.isBlank()?null:new BigDecimal(value);}
    private static BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private static String blank(String value){return value==null||value.isBlank()?null:value;}
    private long userId(Authentication auth){Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,auth.getName());if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}

    static List<List<String>> parseCsv(String text){List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"'){if(quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}else if(c==','&&!quoted){row.add(cell.toString());cell.setLength(0);}else if((c=='\n'||c=='\r')&&!quoted){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(cell.toString());cell.setLength(0);rows.add(row);row=new ArrayList<>();}else cell.append(c);}if(cell.length()>0||!row.isEmpty()){row.add(cell.toString());rows.add(row);}return rows;}
}
