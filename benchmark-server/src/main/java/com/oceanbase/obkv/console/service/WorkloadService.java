package com.oceanbase.obkv.console.service;

import com.oceanbase.obkv.console.config.YcsbConfig;
import com.oceanbase.obkv.console.dto.TaskRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;

/**
 * Service for managing workload files
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkloadService {

    private final YcsbConfig ycsbConfig;

    @PostConstruct
    public void init() {
        // Ensure temp directory exists
        try {
            Path tempPath = Paths.get(ycsbConfig.getTempPath());
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
                log.info("Created temp directory: {}", tempPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create temp directory: {}", e.getMessage());
        }
    }

    /**
     * Get the base workload file path
     */
    public Path getBaseWorkloadPath(String workloadName) {
        return Paths.get(ycsbConfig.getWorkloadsPath(), workloadName);
    }

    /**
     * Read base workload properties
     */
    public Properties readBaseWorkload(String workloadName) throws IOException {
        Path path = getBaseWorkloadPath(workloadName);
        Properties props = new Properties();
        
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String key = line.substring(0, equalIndex).trim();
                    String value = line.substring(equalIndex + 1).trim();
                    props.setProperty(key, value);
                }
            }
        }
        
        return props;
    }

    /**
     * Generate a complete workload file with all configurations
     */
    public Path generateWorkloadFile(TaskRequest request) throws IOException {
        String modelType = request.getModelType();
        // Read base workload
        Properties props = readBaseWorkload(request.getWorkload());

        // Add OBKV connection properties
        props.putAll(request.getObkvConnection().toProperties(modelType));

        // Add client configuration
        if (request.getClientConfig() != null) {
            props.putAll(request.getClientConfig().toProperties());
        }

        // Add workload parameters (override base values)
        if (request.getWorkloadParams() != null) {
            props.putAll(request.getWorkloadParams().toProperties(modelType));
        }

        // Add table configuration
        if ("table".equalsIgnoreCase(modelType)) {
            props.setProperty("table", request.getTableName());
            // Force 1 field for Table model (ycsb_key, ycsb_value)
            props.setProperty("fieldcount", "1");
        } else {
            props.setProperty("hbase.oceanbase.table", request.getTableName());
            props.setProperty("hbase.oceanbase.columnFamily", request.getColumnFamily());
        }

        // Generate temp file
        String fileName = String.format("%s_%s_%d.properties",
                request.getWorkload(),
                UUID.randomUUID().toString().substring(0, 8),
                System.currentTimeMillis());
        Path tempFile = Paths.get(ycsbConfig.getTempPath(), fileName);

        // Write properties to file
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            writer.write("# Generated workload file\n");
            writer.write("# Base: " + request.getWorkload() + "\n");
            writer.write("# Generated at: " + java.time.LocalDateTime.now() + "\n");
            writer.write("\n");

            for (String key : props.stringPropertyNames()) {
                writer.write(key + "=" + props.getProperty(key) + "\n");
            }
        }

        log.info("Generated workload file: {}", tempFile.toAbsolutePath());
        return tempFile;
    }

    /**
     * Delete a temp workload file
     */
    public void deleteTempFile(Path file) {
        try {
            Files.deleteIfExists(file);
            log.debug("Deleted temp file: {}", file);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", file);
        }
    }

    /**
     * Get available workloads
     */
    public String[] getAvailableWorkloads() {
        return new String[]{
                "workloada",
                "workloadb",
                "workloadc",
                "workloadd",
                "workloade",
                "workloadf",
                "workloadg"
        };
    }

    /**
     * Get workload description
     */
    public String getWorkloadDescription(String workloadName) {
        switch (workloadName) {
            case "workloada":
                return "Update Heavy - 50% Read / 50% Update, Zipfian distribution";
            case "workloadb":
                return "Read Mostly - 95% Read / 5% Update, Zipfian distribution";
            case "workloadc":
                return "Read Only - 100% Read, Zipfian distribution";
            case "workloadd":
                return "Read Latest - 95% Read / 5% Insert, Latest distribution";
            case "workloade":
                return "Short Ranges - 95% Scan / 5% Insert, Zipfian distribution";
            case "workloadf":
                return "Read-Modify-Write - 50% Read / 50% RMW, Zipfian distribution";
            case "workloadg":
                return "Custom - 自定义读/写/扫描/更新比例";
            default:
                return "Unknown workload";
        }
    }
}

