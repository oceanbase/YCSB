package com.oceanbase.obkv.console.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.Properties;

/**
 * OBKV connection configuration for benchmark operations
 */
@Data
public class ObkvConnection {

    /**
     * Connection mode: "odp" or "direct"
     */
    @NotBlank(message = "Connection mode is required")
    private String mode = "odp";

    // ========== ODP Mode Parameters ==========

    /**
     * ODP server IP address
     */
    private String ip;

    /**
     * ODP server port
     */
    private Integer port;

    // ========== Direct Mode Parameters ==========

    /**
     * Parameter URL for direct connection
     */
    private String paramUrl;

    /**
     * System username for direct connection
     */
    private String sysUserName;

    /**
     * System password for direct connection
     */
    private String sysPassword;

    // ========== Common Parameters ==========

    /**
     * Full username (format: user@tenant#cluster)
     */
    @NotBlank(message = "Full username is required")
    private String fullUserName;

    /**
     * User password (optional, can be empty for passwordless login)
     */
    private String password;

    /**
     * Database name
     */
    @NotBlank(message = "Database is required")
    private String database;

    /**
     * Convert to properties for workload file
     */
    public Properties toProperties(String modelType) {
        Properties props = new Properties();
        boolean isTable = "table".equalsIgnoreCase(modelType);

        if ("odp".equalsIgnoreCase(mode)) {
            props.setProperty(isTable ? "obkv.isOdpMode" : "hbase.oceanbase.odpMode", "true");
            props.setProperty(isTable ? "obkv.odpAddr" : "hbase.oceanbase.odpAddr", ip != null ? ip : "");
            props.setProperty(isTable ? "obkv.odpPort" : "hbase.oceanbase.odpPort", port != null ? String.valueOf(port) : "");
        } else {
            props.setProperty(isTable ? "obkv.isOdpMode" : "hbase.oceanbase.odpMode", "false");
            props.setProperty(isTable ? "obkv.configUrl" : "hbase.oceanbase.paramURL", paramUrl != null ? paramUrl : "");
            props.setProperty(isTable ? "obkv.sysUserName" : "hbase.oceanbase.sysUserName", sysUserName != null ? sysUserName : "");
            props.setProperty(isTable ? "obkv.sysPassword" : "hbase.oceanbase.sysPassword", sysPassword != null ? sysPassword : "");
        }

        props.setProperty(isTable ? "obkv.fullUserName" : "hbase.oceanbase.fullUserName", fullUserName != null ? fullUserName : "");
        props.setProperty(isTable ? "obkv.password" : "hbase.oceanbase.password", password != null ? password : "");
        props.setProperty(isTable ? "obkv.database" : "hbase.oceanbase.database", database != null ? database : "");

        return props;
    }

    /**
     * Convert to properties for workload file (backward compatibility)
     */
    public Properties toProperties() {
        return toProperties("hbase");
    }

    /**
     * Check if this is ODP mode
     */
    public boolean isOdpMode() {
        return "odp".equalsIgnoreCase(mode);
    }
}

