package com.sunny.paintfactory.inventory;

import com.sunny.paintfactory.common.ApiResponse;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/inventory-import")
public class InventoryImportController {
    private static final int MAX_ROWS = 20_000;
    private static final int PREVIEW_LIMIT = 100;
    private static final List<String> CODE_HEADERS = List.of("货品编号", "编号", "skuCode");
    private static final List<String> STOCK_HEADERS = List.of("盘点库存", "总库存量", "总库存", "库存", "totalStock");
    private final JdbcTemplate jdbc;

    public InventoryImportController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String,Object>> preview(@RequestPart("file") MultipartFile file) {
        ParsedFile parsed=parse(file);
        return ApiResponse.success(buildPreview(parsed, false));
    }

    @Transactional
    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String,Object>> confirm(@RequestPart("file") MultipartFile file,
        @RequestParam String previewToken, @RequestParam String reason, Authentication auth) {
        if(reason==null||reason.isBlank())throw bad("请填写本次库存盘点导入原因");
        ParsedFile parsed=parse(file);
        Map<String,Object> preview=buildPreview(parsed, true);
        Map<?,?> summary=(Map<?,?>)preview.get("summary");
        if(((Number)summary.get("invalid")).intValue()>0)throw bad("文件中仍有问题数据，请处理后重新预检");
        if(!String.valueOf(preview.get("previewToken")).equals(previewToken))
            throw conflict("库存已发生变化，请重新预检后再确认导入");
        long uid=userId(auth);LocalDateTime now=LocalDateTime.now();
        String referenceNo="PD"+now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int updated=0,skipped=0,ignored=0;
        for(StockRow row:parsed.rows()){
            Map<String,Object> current=lockEligibleProduct(row.code());
            if(current==null){ignored++;continue;}
            BigDecimal before=(BigDecimal)current.get("total_stock"),after=row.stock(),change=after.subtract(before);
            if(change.signum()==0){skipped++;continue;}
            long productId=((Number)current.get("id")).longValue();
            jdbc.update("UPDATE product_sku SET total_stock=?,version=version+1,updated_by=?,updated_at=? WHERE id=?",after,uid,now,productId);
            jdbc.update("INSERT INTO inventory_movement(product_sku_id,movement_type,quantity_change,before_quantity,after_quantity,reason,reference_type,reference_id,reference_line_id,reference_no,created_by,created_at) VALUES(?,'ADJUSTMENT',?,?,?,?, 'STOCK_TAKE_IMPORT',NULL,?,?,?,?)",
                productId,change,before,after,reason.trim(),row.rowNumber(),referenceNo,uid,now);
            updated++;
        }
        return ApiResponse.success(Map.of("total",parsed.rows().size(),"updated",updated,"skipped",skipped,"ignored",ignored,"referenceNo",referenceNo));
    }

    Map<String,Object> buildPreview(ParsedFile parsed,boolean lock) {
        List<Map<String,Object>> rows=new ArrayList<>(),issues=new ArrayList<>();
        List<String> signatureParts=new ArrayList<>();int valid=0,invalid=0,ignored=0,changed=0,unchanged=0;
        for(StockRow incoming:parsed.rows()){
            List<Map<String,Object>> products=jdbc.queryForList((lock?"SELECT id,sku_code,product_name,total_stock,version,enabled,saleable FROM product_sku WHERE sku_code=? FOR UPDATE":"SELECT id,sku_code,product_name,total_stock,version,enabled,saleable FROM product_sku WHERE sku_code=?"),incoming.code());
            Map<String,Object> item=new LinkedHashMap<>();item.put("货品编号",incoming.code());item.put("盘点库存",incoming.stock());
            String validation;
            if(products.isEmpty()){validation="已跳过：系统不存在此编号";ignored++;item.put("品名","");item.put("当前库存","");item.put("差额","");signatureParts.add(incoming.code()+"|"+incoming.stock().toPlainString()+"|MISSING");}
            else{
                Map<String,Object> product=products.get(0);BigDecimal before=(BigDecimal)product.get("total_stock"),difference=incoming.stock().subtract(before);
                item.put("品名",product.get("product_name"));item.put("当前库存",before);item.put("差额",difference);
                boolean enabled=asBoolean(product.get("enabled")),saleable=asBoolean(product.get("saleable"));
                if(!enabled){validation="已跳过：货品已停用";ignored++;}
                else if(!saleable){validation="已跳过：货品已停卖";ignored++;}
                else{validation=difference.signum()==0?"无需调整":"通过";valid++;if(difference.signum()==0)unchanged++;else changed++;}
                signatureParts.add(incoming.code()+"|"+incoming.stock().toPlainString()+"|"+before.toPlainString()+"|"+product.get("version")+"|"+enabled+"|"+saleable);
            }
            item.put("校验结果",validation);if(rows.size()<PREVIEW_LIMIT)rows.add(item);
        }
        invalid+=parsed.issues().size();issues.addAll(parsed.issues());
        Map<String,Object> summary=new HashMap<>();summary.put("total",parsed.total());summary.put("valid",valid);summary.put("invalid",invalid);summary.put("ignored",ignored);summary.put("duplicates",parsed.duplicates());summary.put("newRecords",0);summary.put("updateRecords",changed);summary.put("unchangedRecords",unchanged);
        Map<String,Object> result=new HashMap<>();result.put("sheetName",parsed.sheetName());result.put("headerRow",parsed.headerRow());result.put("headers",List.of("货品编号","品名","当前库存","盘点库存","差额"));result.put("rows",rows);result.put("issues",issues.stream().limit(100).toList());result.put("summary",summary);result.put("previewLimit",PREVIEW_LIMIT);result.put("importSupported",true);result.put("canImportValidRows",false);result.put("previewToken",sha256(String.join("\n",signatureParts)));return result;
    }

    ParsedFile parse(MultipartFile file){
        if(file.isEmpty())throw bad("请选择 Excel 文件");String name=file.getOriginalFilename()==null?"":file.getOriginalFilename().toLowerCase();
        if(!name.endsWith(".xlsx")&&!name.endsWith(".xls"))throw bad("库存盘点导入仅支持 .xlsx 或 .xls 文件");
        try(InputStream input=file.getInputStream();Workbook workbook=WorkbookFactory.create(input)){
            if(workbook.getNumberOfSheets()==0)throw bad("Excel 中没有工作表");return readSheet(workbook.getSheetAt(0));
        }catch(ResponseStatusException e){throw e;}catch(Exception e){throw bad("无法读取 Excel，请确认文件未损坏且不是加密文件");}
    }

    ParsedFile readSheet(Sheet sheet){
        DataFormatter formatter=new DataFormatter();int headerRow=findHeaderRow(sheet,formatter);List<String> headers=readRow(sheet.getRow(headerRow),formatter,sheet.getRow(headerRow).getLastCellNum());
        int codeIndex=findHeader(headers,CODE_HEADERS),stockIndex=findHeader(headers,STOCK_HEADERS);if(codeIndex<0||stockIndex<0)throw bad("缺少必填列：货品编号（或编号）和盘点库存（或总库存/库存）");
        List<StockRow> rows=new ArrayList<>();List<Map<String,Object>> issues=new ArrayList<>();Set<String> seen=new HashSet<>();int total=0,duplicates=0;
        for(int i=headerRow+1;i<=sheet.getLastRowNum();i++){
            Row row=sheet.getRow(i);if(row==null)continue;List<String> values=readRow(row,formatter,headers.size());if(values.stream().allMatch(String::isBlank))continue;
            total++;if(total>MAX_ROWS)throw bad("单次最多导入 "+MAX_ROWS+" 条库存");String code=values.get(codeIndex).trim(),stockText=values.get(stockIndex).trim();
            if(code.isBlank()){issues.add(Map.of("row",i+1,"message","货品编号为空"));continue;}
            if(!seen.add(code)){duplicates++;issues.add(Map.of("row",i+1,"message","货品编号重复"));continue;}
            try{rows.add(new StockRow(i+1,code,new BigDecimal(stockText)));}
            catch(Exception e){issues.add(Map.of("row",i+1,"message","盘点库存必须是数字"));}
        }
        return new ParsedFile(sheet.getSheetName(),headerRow+1,total,duplicates,rows,issues);
    }

    private Map<String,Object> lockEligibleProduct(String code){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,total_stock FROM product_sku WHERE sku_code=? AND enabled=1 AND saleable=1 FOR UPDATE",code);return rows.isEmpty()?null:rows.get(0);}
    private static boolean asBoolean(Object value){return value instanceof Boolean b?b:value instanceof Number n&&n.intValue()!=0;}
    private int findHeaderRow(Sheet sheet,DataFormatter formatter){for(int i=0;i<=Math.min(sheet.getLastRowNum(),19);i++){Row row=sheet.getRow(i);if(row==null)continue;List<String> values=readRow(row,formatter,row.getLastCellNum());if(findHeader(values,CODE_HEADERS)>=0&&findHeader(values,STOCK_HEADERS)>=0)return i;}throw bad("前 20 行中找不到货品编号和盘点库存表头");}
    private static int findHeader(List<String> headers,List<String> aliases){for(int i=0;i<headers.size();i++)if(aliases.contains(headers.get(i).trim()))return i;return -1;}
    private static List<String> readRow(Row row,DataFormatter formatter,int size){List<String> result=new ArrayList<>();for(int i=0;i<size;i++){Cell cell=row.getCell(i,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);result.add(cell==null?"":formatter.formatCellValue(cell).trim());}return result;}
    private long userId(Authentication auth){if(auth==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,auth.getName());if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    private static String sha256(String text){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}

    record StockRow(int rowNumber,String code,BigDecimal stock){}
    record ParsedFile(String sheetName,int headerRow,int total,int duplicates,List<StockRow> rows,List<Map<String,Object>> issues){}
}
