package com.sunny.paintfactory.masterdata;

import com.sunny.paintfactory.common.ApiResponse;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
@RequestMapping("/api/v1/master-data-import")
public class MasterDataImportController {
    private static final int PREVIEW_LIMIT = 100;
    private static final int MAX_ROWS = 20_000;
    private static final Map<String, List<String>> REQUIRED_HEADERS = Map.of(
        "PRODUCT_CATEGORY", List.of("Category Code", "Category Name"),
        "ROUTE", List.of("Code", "Name"),
        "EMPLOYEE_TYPE", List.of("Code", "Name"),
        "DEPARTMENT", List.of("Code", "Name"),
        "SUPPLIER", List.of("Code", "Short Name"),
        "EMPLOYEE", List.of("Code", "Name"),
        "VEHICLE", List.of("License Plate", "Vehicle Type")
    );
    private static final Map<String,String> HEADER_ALIASES = Map.ofEntries(
        Map.entry("\u5927\u7c7b\u7f16\u53f7","Category Code"), Map.entry("\u5927\u7c7b\u540d\u79f0","Category Name"),
        Map.entry("\u7f16\u53f7","Code"), Map.entry("\u540d\u79f0","Name"), Map.entry("\u7b80\u79f0","Short Name"),
        Map.entry("\u59d3\u540d","Name"), Map.entry("\u8f66\u724c","License Plate"), Map.entry("\u8f66\u578b","Vehicle Type"),
        Map.entry("\u5907\u6ce8","Remark"), Map.entry("\u7535\u8bdd","Phone"), Map.entry("\u624b\u673a","Mobile"),
        Map.entry("\u4f20\u771f","Fax"), Map.entry("\u5730\u5740","Address"), Map.entry("\u5458\u5de5\u7c7b\u522b","Employee Type"),
        Map.entry("\u6027\u522b","Gender"), Map.entry("\u804c\u52a1","Position"), Map.entry("\u5b66\u5386","Education"),
        Map.entry("\u8eab\u4efd\u8bc1\u53f7","ID Number"), Map.entry("\u4f4f\u5740","Address"), Map.entry("\u662f\u5426\u4e1a\u52a1\u5458","Salesperson"),
        Map.entry("\u7c4d\u8d2f","Hometown"), Map.entry("\u90ae\u7f16","Postal Code"), Map.entry("\u5165\u804c\u65e5\u671f","Hire Date")
    );
    private final JdbcTemplate jdbc;

    public MasterDataImportController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> preview(@RequestParam String dataType, @RequestPart("file") MultipartFile file) {
        String type = normalizeType(dataType);
        ParsedSheet parsed = parse(file, REQUIRED_HEADERS.get(type));
        Map<String, Object> response = parsed.toResponse();
        if (supportsImport(type) && (parsed.invalid() == 0 || canSkipInvalid(type, parsed))) {
            ((Map<String, Object>) response.get("summary")).putAll(compareWithDatabase(type, parsed.validRows()));
        }
        response.put("importSupported", supportsImport(type));
        response.put("canImportValidRows", canSkipInvalid(type, parsed));
        return ApiResponse.success(response);
    }

    @Transactional
    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> confirm(@RequestParam String dataType,
        @RequestParam(defaultValue = "false") boolean skipInvalid,
        @RequestPart("file") MultipartFile file, Authentication authentication) {
        String type = normalizeType(dataType);
        if (!supportsImport(type)) throw bad("This master-data type currently supports preview only and cannot be imported");
        ParsedSheet parsed = parse(file, REQUIRED_HEADERS.get(type));
        if (parsed.invalid() > 0 && !(skipInvalid && canSkipInvalid(type, parsed)))
            throw bad("The file contains " + parsed.invalid() + " invalid rows; correct them and preview again");
        Map<String, Object> expected = compareWithDatabase(type, parsed.validRows());
        int inserted = 0, updated = 0, skipped = 0;
        for (Map<String, String> row : parsed.validRows()) {
            String code = row.get(parsed.required().get(0));
            String name = row.get(parsed.required().get(1));
            if ("PRODUCT_CATEGORY".equals(type)) {
                List<String> current = jdbc.query("SELECT category_name FROM product_category WHERE category_code=?", (rs, n) -> rs.getString(1), code);
                if (current.isEmpty()) { jdbc.update("INSERT INTO product_category(category_code,category_name,enabled,sort_order) VALUES(?,?,1,0)", code, name); inserted++; }
                else if (current.get(0).equals(name)) skipped++;
                else { jdbc.update("UPDATE product_category SET category_name=?,enabled=1 WHERE category_code=?", name, code); updated++; }
            } else if ("ROUTE".equals(type)) {
                String remark = row.getOrDefault("Remark", "");
                List<Map<String, Object>> current = jdbc.queryForList("SELECT route_name,COALESCE(remark,'') remark FROM route WHERE route_code=?", code);
                if (current.isEmpty()) { jdbc.update("INSERT INTO route(route_code,route_name,enabled,remark) VALUES(?,?,1,?)", code, name, blank(remark)); inserted++; }
                else if (name.equals(current.get(0).get("route_name")) && remark.equals(current.get(0).get("remark"))) skipped++;
                else { jdbc.update("UPDATE route SET route_name=?,remark=?,enabled=1 WHERE route_code=?", name, blank(remark), code); updated++; }
            } else if ("EMPLOYEE_TYPE".equals(type)) {
                List<String> current = jdbc.query("SELECT type_name FROM employee_type WHERE type_code=?", (rs, n) -> rs.getString(1), code);
                if (current.isEmpty()) { jdbc.update("INSERT INTO employee_type(type_code,type_name,enabled) VALUES(?,?,1)", code, name); inserted++; }
                else if (current.get(0).equals(name)) skipped++;
                else { jdbc.update("UPDATE employee_type SET type_name=?,enabled=1 WHERE type_code=?", name, code); updated++; }
            } else if ("DEPARTMENT".equals(type)) {
                List<String> current = jdbc.query("SELECT department_name FROM department WHERE department_code=?", (rs, n) -> rs.getString(1), code);
                if (current.isEmpty()) { jdbc.update("INSERT INTO department(department_code,department_name,enabled) VALUES(?,?,1)", code, name); inserted++; }
                else if (current.get(0).equals(name)) skipped++;
                else { jdbc.update("UPDATE department SET department_name=?,enabled=1 WHERE department_code=?", name, code); updated++;
                }
            } else if ("SUPPLIER".equals(type)) {
                List<Map<String, Object>> current = jdbc.queryForList("SELECT short_name,COALESCE(phone,'') phone,COALESCE(mobile,'') mobile,COALESCE(fax,'') fax,COALESCE(address,'') address,COALESCE(remark,'') remark FROM supplier WHERE supplier_code=?", code);
                String phone=row.getOrDefault("Phone",""), mobile=row.getOrDefault("Mobile",""), fax=row.getOrDefault("Fax",""), address=row.getOrDefault("Address",""), remark=row.getOrDefault("Remark","");
                if (current.isEmpty()) { jdbc.update("INSERT INTO supplier(supplier_code,short_name,phone,mobile,fax,address,remark,enabled,version) VALUES(?,?,?,?,?,?,?,1,0)",code,name,blank(phone),blank(mobile),blank(fax),blank(address),blank(remark)); inserted++; }
                else if (supplierValue(name,phone,mobile,fax,address,remark).equals(supplierDbValue(current.get(0)))) skipped++;
                else { jdbc.update("UPDATE supplier SET short_name=?,phone=?,mobile=?,fax=?,address=?,remark=?,enabled=1,version=version+1 WHERE supplier_code=?",name,blank(phone),blank(mobile),blank(fax),blank(address),blank(remark),code); updated++; }
            } else if ("VEHICLE".equals(type)) {
                String remark=row.getOrDefault("Remark","");
                List<Map<String,Object>> current=jdbc.queryForList("SELECT COALESCE(vehicle_type,'') vehicle_type,COALESCE(remark,'') remark FROM vehicle WHERE vehicle_code=?",code);
                if(current.isEmpty()){jdbc.update("INSERT INTO vehicle(vehicle_code,plate_no,vehicle_type,enabled,remark) VALUES(?,?,?,1,?)",code,code,blank(name),blank(remark));inserted++;}
                else if(join(name,remark).equals(join(value(current.get(0),"vehicle_type"),value(current.get(0),"remark"))))skipped++;
                else{jdbc.update("UPDATE vehicle SET plate_no=?,vehicle_type=?,remark=?,enabled=1 WHERE vehicle_code=?",code,blank(name),blank(remark),code);updated++;}
            } else {
                long userId=userId(authentication); LocalDateTime now=LocalDateTime.now();
                String typeName=row.getOrDefault("Employee Type",""); Long employeeTypeId=findEmployeeTypeId(typeName);
                String gender=row.getOrDefault("Gender",""),position=row.getOrDefault("Position",""),education=row.getOrDefault("Education",""),idCard=row.getOrDefault("ID Number",""),address=row.getOrDefault("Address",""),hometown=row.getOrDefault("Hometown",""),postal=row.getOrDefault("Postal Code",""),remark=row.getOrDefault("Remark","");
                boolean salesperson=yes(row.getOrDefault("Salesperson","")); LocalDate hireDate=parseDate(row.getOrDefault("Hire Date",""));
                List<Map<String,Object>> current=jdbc.queryForList("SELECT employee_name,COALESCE(gender,'') gender,COALESCE(et.type_name,'') type_name,COALESCE(position_name,'') position_name,COALESCE(education,'') education,COALESCE(id_card,'') id_card,COALESCE(address,'') address,is_salesperson,COALESCE(hometown,'') hometown,COALESCE(postal_code,'') postal_code,hire_date,COALESCE(e.remark,'') remark FROM employee e LEFT JOIN employee_type et ON et.id=e.employee_type_id WHERE employee_code=?",code);
                if(current.isEmpty()){jdbc.update("INSERT INTO employee(employee_code,employee_name,gender,employee_type_id,department_id,position_name,education,id_card,address,is_salesperson,hometown,postal_code,hire_date,phone,enabled,remark,version,created_by,created_at,updated_by,updated_at) VALUES(?,?,?,?,NULL,?,?,?,?,?,?,?,?,NULL,1,?,0,?,?,?,?)",code,name,blank(gender),employeeTypeId,blank(position),blank(education),blank(idCard),blank(address),salesperson,blank(hometown),blank(postal),hireDate,blank(remark),userId,now,userId,now);inserted++;}
                else if(employeeValue(name,gender,typeName,position,education,idCard,address,salesperson,hometown,postal,hireDate,remark).equals(employeeDbValue(current.get(0))))skipped++;
                else{jdbc.update("UPDATE employee SET employee_name=?,gender=?,employee_type_id=?,position_name=?,education=?,id_card=?,address=?,is_salesperson=?,hometown=?,postal_code=?,hire_date=?,remark=?,enabled=1,version=version+1,updated_by=?,updated_at=? WHERE employee_code=?",name,blank(gender),employeeTypeId,blank(position),blank(education),blank(idCard),blank(address),salesperson,blank(hometown),blank(postal),hireDate,blank(remark),userId,now,code);updated++;}
            }
        }
        return ApiResponse.success(Map.of("total", parsed.total(), "valid", parsed.validRows().size(),
            "skippedInvalid", parsed.invalid(), "inserted", inserted, "updated", updated,
            "skipped", skipped, "expected", expected));
    }

    private Map<String, Object> compareWithDatabase(String type, List<Map<String, String>> rows) {
        Map<String, String> existing = new HashMap<>();
        if ("PRODUCT_CATEGORY".equals(type)) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT category_code,category_name FROM product_category"))
                existing.put(String.valueOf(row.get("category_code")), String.valueOf(row.get("category_name")));
        } else if ("ROUTE".equals(type)) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT route_code,CONCAT(route_name,'\u0000',COALESCE(remark,'')) combined_value FROM route"))
                existing.put(String.valueOf(row.get("route_code")), String.valueOf(row.get("combined_value")));
        } else if ("EMPLOYEE_TYPE".equals(type)) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT type_code,type_name FROM employee_type"))
                existing.put(String.valueOf(row.get("type_code")), String.valueOf(row.get("type_name")));
        } else if ("DEPARTMENT".equals(type)) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT department_code,department_name FROM department"))
                existing.put(String.valueOf(row.get("department_code")), String.valueOf(row.get("department_name")));
        } else if ("SUPPLIER".equals(type)) {
            for(Map<String,Object> row:jdbc.queryForList("SELECT supplier_code,short_name,COALESCE(phone,'') phone,COALESCE(mobile,'') mobile,COALESCE(fax,'') fax,COALESCE(address,'') address,COALESCE(remark,'') remark FROM supplier"))
                existing.put(value(row,"supplier_code"),supplierDbValue(row));
        } else if ("VEHICLE".equals(type)) {
            for(Map<String,Object> row:jdbc.queryForList("SELECT vehicle_code,COALESCE(vehicle_type,'') vehicle_type,COALESCE(remark,'') remark FROM vehicle"))
                existing.put(value(row,"vehicle_code"),join(value(row,"vehicle_type"),value(row,"remark")));
        } else {
            for(Map<String,Object> row:jdbc.queryForList("SELECT employee_code,employee_name,COALESCE(gender,'') gender,COALESCE(et.type_name,'') type_name,COALESCE(position_name,'') position_name,COALESCE(education,'') education,COALESCE(id_card,'') id_card,COALESCE(address,'') address,is_salesperson,COALESCE(hometown,'') hometown,COALESCE(postal_code,'') postal_code,hire_date,COALESCE(e.remark,'') remark FROM employee e LEFT JOIN employee_type et ON et.id=e.employee_type_id"))
                existing.put(value(row,"employee_code"),employeeDbValue(row));
        }
        int newRecords = 0, updateRecords = 0, unchangedRecords = 0;
        for (Map<String, String> row : rows) {
            String code = row.get(REQUIRED_HEADERS.get(type).get(0));
            String incoming = comparisonValue(type,row);
            if (!existing.containsKey(code)) newRecords++;
            else if (incoming.equals(existing.get(code))) unchangedRecords++;
            else updateRecords++;
        }
        return Map.of("newRecords", newRecords, "updateRecords", updateRecords, "unchangedRecords", unchangedRecords);
    }

    private ParsedSheet parse(MultipartFile file, List<String> required) {
        if (required == null) throw bad("This master-data type is not supported");
        if (file.isEmpty()) throw bad("Select an Excel file");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) throw bad("Only .xlsx or .xls files are supported");
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw bad("The Excel file contains no worksheets");
            return readSheet(workbook.getSheetAt(0), required);
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw bad("Unable to read the Excel file; make sure it is not damaged or encrypted"); }
    }

    ParsedSheet readSheet(Sheet sheet, List<String> required) {
        DataFormatter formatter = new DataFormatter();
        int headerRowIndex = findHeaderRow(sheet, formatter);
        Row headerRow = sheet.getRow(headerRowIndex);
        List<String> headers = readRow(headerRow, formatter, headerRow.getLastCellNum()).stream().map(MasterDataImportController::normalizeHeader).toList();
        List<String> missingHeaders = required.stream().filter(h -> !headers.contains(h)).toList();
        if (!missingHeaders.isEmpty()) throw bad("Missing required columns: " + String.join(", ", missingHeaders));
        int keyIndex = headers.indexOf(required.get(0)), nameIndex = headers.indexOf(required.get(1));
        Set<String> seenCodes = new HashSet<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, String>> previewRows = new ArrayList<>(), validRows = new ArrayList<>();
        int total = 0, duplicate = 0, emptyCode = 0, emptyName = 0;
        for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex); if (row == null) continue;
            List<String> values = readRow(row, formatter, headers.size());
            if (values.stream().allMatch(String::isBlank)) continue;
            total++; if (total > MAX_ROWS) throw bad("A single preview supports at most " + MAX_ROWS + " rows");
            String code = values.get(keyIndex).trim(), name = values.get(nameIndex).trim();
            List<String> problems = new ArrayList<>();
            if (code.isBlank()) { emptyCode++; problems.add("Code is blank"); }
            else if (!seenCodes.add(code)) { duplicate++; problems.add("Duplicate code"); }
            if (name.isBlank()) { emptyName++; problems.add("Name is blank"); }
            Map<String, String> item = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) item.put(headers.get(i), values.get(i));
            if (problems.isEmpty()) validRows.add(item);
            else if (issues.size() < 100) issues.add(Map.of("row", rowIndex + 1, "message", String.join("; ", problems)));
            if (previewRows.size() < PREVIEW_LIMIT) { item = new LinkedHashMap<>(item); item.put("Validation Result", problems.isEmpty() ? "Valid" : String.join("; ", problems)); previewRows.add(item); }
        }
        return new ParsedSheet(sheet.getSheetName(), headerRowIndex + 1, headers, required, total, duplicate, emptyCode, emptyName, previewRows, validRows, issues);
    }

    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int bestIndex = -1, bestScore = -1;
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 19); i++) {
            Row row = sheet.getRow(i); if (row == null) continue; int score = 0;
            for (Cell cell : row) if (!formatter.formatCellValue(cell).trim().isEmpty()) score++;
            if (score > bestScore) { bestScore = score; bestIndex = i; }
        }
        if (bestIndex < 0 || bestScore <= 0) throw bad("No readable header row was found in the Excel file");
        return bestIndex;
    }

    private List<String> readRow(Row row, DataFormatter formatter, int size) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < size; i++) { Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL); values.add(cell == null ? "" : formatter.formatCellValue(cell).trim()); }
        return values;
    }

    private String comparisonValue(String type,Map<String,String> row){
        String name=row.get(REQUIRED_HEADERS.get(type).get(1));
        return switch(type){
            case "ROUTE" -> join(name,row.getOrDefault("Remark",""));
            case "SUPPLIER" -> supplierValue(name,row.getOrDefault("Phone",""),row.getOrDefault("Mobile",""),row.getOrDefault("Fax",""),row.getOrDefault("Address",""),row.getOrDefault("Remark",""));
            case "VEHICLE" -> join(name,row.getOrDefault("Remark",""));
            case "EMPLOYEE" -> employeeValue(name,row.getOrDefault("Gender",""),row.getOrDefault("Employee Type",""),row.getOrDefault("Position",""),row.getOrDefault("Education",""),row.getOrDefault("ID Number",""),row.getOrDefault("Address",""),yes(row.getOrDefault("Salesperson","")),row.getOrDefault("Hometown",""),row.getOrDefault("Postal Code",""),parseDate(row.getOrDefault("Hire Date","")),row.getOrDefault("Remark",""));
            default -> name;
        };
    }
    private static String supplierValue(String name,String phone,String mobile,String fax,String address,String remark){return join(name,phone,mobile,fax,address,remark);}
    private static String supplierDbValue(Map<String,Object> row){return supplierValue(value(row,"short_name"),value(row,"phone"),value(row,"mobile"),value(row,"fax"),value(row,"address"),value(row,"remark"));}
    private static String employeeValue(String name,String gender,String typeName,String position,String education,String idCard,String address,boolean salesperson,String hometown,String postal,LocalDate hireDate,String remark){return join(name,gender,typeName,position,education,idCard,address,salesperson?"1":"0",hometown,postal,hireDate==null?"":hireDate.toString(),remark);}
    private static String employeeDbValue(Map<String,Object> row){Object raw=row.get("is_salesperson");boolean salesperson=raw instanceof Boolean b?b:raw instanceof Number n&&n.intValue()!=0;Object date=row.get("hire_date");return employeeValue(value(row,"employee_name"),value(row,"gender"),value(row,"type_name"),value(row,"position_name"),value(row,"education"),value(row,"id_card"),value(row,"address"),salesperson,value(row,"hometown"),value(row,"postal_code"),date==null?null:LocalDate.parse(String.valueOf(date)),value(row,"remark"));}
    private Long findEmployeeTypeId(String name){if(name==null||name.isBlank())return null;List<Long> ids=jdbc.query("SELECT id FROM employee_type WHERE type_name=? ORDER BY id LIMIT 1",(rs,n)->rs.getLong(1),name);if(ids.isEmpty())throw bad("Employee type does not exist: "+name+". Import employee types first");return ids.get(0);}
    private long userId(Authentication authentication){if(authentication==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,authentication.getName());if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    private static boolean yes(String value){String v=value==null?"":value.trim();return "\u221a".equals(v)||"\u662f".equals(v)||"1".equals(v)||"true".equalsIgnoreCase(v)||"yes".equalsIgnoreCase(v);}
    private static LocalDate parseDate(String value){if(value==null||value.isBlank())return null;for(String pattern:List.of("yyyy-MM-dd","yyyy/M/d","M/d/yyyy","M/d/yy")){try{return LocalDate.parse(value.trim(),DateTimeFormatter.ofPattern(pattern));}catch(DateTimeParseException ignored){}}throw bad("Unrecognized hire date: "+value);}
    private static String normalizeHeader(String header){return HEADER_ALIASES.getOrDefault(header,header);}
    private static String value(Map<String,Object> row,String key){Object v=row.get(key);return v==null?"":String.valueOf(v);}
    private static String join(Object... values){List<String> parts=new ArrayList<>();for(Object value:values)parts.add(value==null?"":String.valueOf(value));return String.join("\u0000",parts);}

    private String normalizeType(String dataType) { String type = dataType == null ? "" : dataType.trim().toUpperCase(); if (!REQUIRED_HEADERS.containsKey(type)) throw bad("This master-data type is not supported"); return type; }
    private boolean supportsImport(String type) { return REQUIRED_HEADERS.containsKey(type); }
    private boolean canSkipInvalid(String type, ParsedSheet parsed) {
        return "EMPLOYEE".equals(type) && parsed.invalid() > 0 && parsed.duplicates() == 0
            && parsed.emptyCode() == 0 && parsed.invalid() == parsed.emptyName();
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }

    record ParsedSheet(String sheetName, int headerRow, List<String> headers, List<String> required, int total,
        int duplicates, int emptyCode, int emptyName, List<Map<String, String>> previewRows,
        List<Map<String, String>> validRows, List<Map<String, Object>> issues) {
        int invalid() { return total - validRows.size(); }
        Map<String, Object> toResponse() {
            Map<String, Object> summary = new HashMap<>();
            summary.put("total", total); summary.put("valid", validRows.size()); summary.put("invalid", invalid()); summary.put("duplicates", duplicates); summary.put("emptyCode", emptyCode); summary.put("emptyName", emptyName);
            Map<String, Object> result = new HashMap<>(); result.put("sheetName", sheetName); result.put("headerRow", headerRow); result.put("headers", headers); result.put("summary", summary); result.put("rows", previewRows); result.put("issues", issues); result.put("previewLimit", PREVIEW_LIMIT); return result;
        }
    }
}
