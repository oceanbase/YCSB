package com.oceanbase.obkv.webui.model;

/**
 * JDBC connection parameters for table creation execution.
 */
public class DbConnConfig {

    private String host;
    private int port = 3306;
    private String username;
    private String password;
    private String database;
    private String sql;
    private Boolean dropIfExists = true;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public Boolean getDropIfExists() { return dropIfExists != null ? dropIfExists : true; }
    public void setDropIfExists(Boolean dropIfExists) { this.dropIfExists = dropIfExists; }
}
