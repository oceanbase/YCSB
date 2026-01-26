package com.oceanbase.obkv.console.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * YCSB configuration properties
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ycsb")
public class YcsbConfig {

    /**
     * Path to the YCSB jar file
     */
    private String jarPath = "obkv-hbase/target/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar";

    /**
     * Path to workloads directory
     */
    private String workloadsPath = "workloads/workloads_a_f";

    /**
     * Temp directory for generated workload files
     */
    private String tempPath = "temp";
}

