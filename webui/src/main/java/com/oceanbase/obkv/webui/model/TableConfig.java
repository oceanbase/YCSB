package com.oceanbase.obkv.webui.model;

import java.util.Map;

/**
 * Parameters for table creation, passed to POST /api/table/generate.
 */
public class TableConfig {

    private String module;          // obkv-hbase or obkv-table
    private Map<String, String> params;  // all script parameters as key=value

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params; }
}
