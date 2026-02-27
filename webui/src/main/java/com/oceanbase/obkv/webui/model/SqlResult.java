package com.oceanbase.obkv.webui.model;

/**
 * Result of SQL generation or execution.
 */
public class SqlResult {

    private boolean success;
    private String sql;
    private String message;

    public SqlResult() {}

    public SqlResult(boolean success, String sql, String message) {
        this.success = success;
        this.sql = sql;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
