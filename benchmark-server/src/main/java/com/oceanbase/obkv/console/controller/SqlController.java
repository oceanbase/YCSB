package com.oceanbase.obkv.console.controller;

import com.oceanbase.obkv.console.dto.ApiResponse;
import com.oceanbase.obkv.console.model.SqlConnection;
import com.oceanbase.obkv.console.service.SqlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * Controller for SQL connection operations
 */
@Slf4j
@RestController
@RequestMapping("/api/sql")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SqlController {

    private final SqlService sqlService;

    /**
     * Test database connection
     */
    @PostMapping("/test-connection")
    public ApiResponse<Boolean> testConnection(@Valid @RequestBody SqlConnection connection) {
        log.info("Testing connection to {}:{}", connection.getIp(), connection.getPort());
        
        try {
            boolean success = sqlService.testConnection(connection);
            if (success) {
                return ApiResponse.success(true, "连接成功");
            } else {
                return ApiResponse.error("连接失败");
            }
        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());
            return ApiResponse.error("连接失败: " + e.getMessage());
        }
    }
}

