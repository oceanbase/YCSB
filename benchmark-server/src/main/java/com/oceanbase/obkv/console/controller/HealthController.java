package com.oceanbase.obkv.console.controller;

import com.oceanbase.obkv.console.config.YcsbConfig;
import com.oceanbase.obkv.console.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Health check controller
 */
@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class HealthController {

    private final YcsbConfig ycsbConfig;

    /**
     * Health check endpoint
     */
    @GetMapping({"/health", "/api/health"})
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", System.currentTimeMillis());
        data.put("version", "1.0.0");
        return ApiResponse.success(data, "服务运行正常");
    }

    /**
     * Diagnostic endpoint - shows configuration and path info
     */
    @GetMapping({"/diag", "/api/diag"})
    public ApiResponse<Map<String, Object>> diagnostic() {
        Map<String, Object> data = new HashMap<>();
        
        // System info
        data.put("javaVersion", System.getProperty("java.version"));
        data.put("workingDir", System.getProperty("user.dir"));
        
        // YCSB config
        Map<String, Object> ycsbInfo = new HashMap<>();
        ycsbInfo.put("jarPath", ycsbConfig.getJarPath());
        ycsbInfo.put("jarExists", new File(ycsbConfig.getJarPath()).exists());
        ycsbInfo.put("workloadsPath", ycsbConfig.getWorkloadsPath());
        ycsbInfo.put("workloadsExists", new File(ycsbConfig.getWorkloadsPath()).exists());
        ycsbInfo.put("tempPath", ycsbConfig.getTempPath());
        ycsbInfo.put("tempExists", new File(ycsbConfig.getTempPath()).exists());
        data.put("ycsbConfig", ycsbInfo);
        
        // List workload files
        try {
            Path workloadsPath = Paths.get(ycsbConfig.getWorkloadsPath());
            if (Files.exists(workloadsPath)) {
                List<String> workloadFiles = new ArrayList<>();
                Files.list(workloadsPath)
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .forEach(p -> workloadFiles.add(p.getFileName().toString()));
                data.put("workloadFiles", workloadFiles);
            } else {
                data.put("workloadFiles", "目录不存在: " + workloadsPath.toAbsolutePath());
            }
        } catch (Exception e) {
            data.put("workloadFiles", "读取失败: " + e.getMessage());
        }
        
        // Environment variables
        Map<String, String> envVars = new HashMap<>();
        envVars.put("YCSB_JAR_PATH", System.getenv("YCSB_JAR_PATH"));
        envVars.put("YCSB_WORKLOADS_PATH", System.getenv("YCSB_WORKLOADS_PATH"));
        data.put("envVars", envVars);
        
        return ApiResponse.success(data, "诊断信息");
    }

    /**
     * API info endpoint
     */
    @GetMapping({"/info", "/api/info"})
    public ApiResponse<Map<String, Object>> info() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "OBKV-HBase Benchmark Console");
        data.put("version", "1.0.0");
        data.put("description", "Backend server for OBKV-HBase benchmark control console");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("SQL Connection", "POST /api/sql/test-connection");
        endpoints.put("DDL - Generate SQL", "POST /api/ddl/generate-sql");
        endpoints.put("DDL - Create Table", "POST /api/ddl/create-table");
        endpoints.put("DDL - Truncate Table", "POST /api/ddl/truncate-table");
        endpoints.put("DDL - Drop Table", "POST /api/ddl/drop-table");
        endpoints.put("Task - Load", "POST /api/task/load");
        endpoints.put("Task - Run", "POST /api/task/run");
        endpoints.put("Task - Status", "GET /api/task/status/{taskId}");
        endpoints.put("Task - Stop", "POST /api/task/stop/{taskId}");
        endpoints.put("Task - List", "GET /api/task/list");
        endpoints.put("Task - Workloads", "GET /api/task/workloads");
        endpoints.put("WebSocket", "ws://host:port/ws/task");
        data.put("endpoints", endpoints);
        
        return ApiResponse.success(data);
    }
}

