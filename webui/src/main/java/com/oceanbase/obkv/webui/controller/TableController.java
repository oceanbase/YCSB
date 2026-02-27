package com.oceanbase.obkv.webui.controller;

import com.oceanbase.obkv.webui.model.DbConnConfig;
import com.oceanbase.obkv.webui.model.SqlResult;
import com.oceanbase.obkv.webui.model.TableConfig;
import com.oceanbase.obkv.webui.service.TableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/table")
public class TableController {

    @Autowired
    private TableService tableService;

    @PostMapping("/generate")
    public ResponseEntity<SqlResult> generateSql(@RequestBody TableConfig config) {
        SqlResult result = tableService.generateSql(config);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/execute")
    public ResponseEntity<SqlResult> executeSql(@RequestBody DbConnConfig conn) {
        SqlResult result = tableService.executeSql(conn);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/test-connection")
    public ResponseEntity<SqlResult> testConnection(@RequestBody DbConnConfig conn) {
        SqlResult result = tableService.testConnection(conn);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }
}
