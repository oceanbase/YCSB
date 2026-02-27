package com.oceanbase.obkv.webui.model;

/**
 * Request body for POST /api/tests (launch a new test).
 * workloadContent is the full text of the workload.properties file.
 */
public class TestConfig {

    private String module;
    private String testType;
    private String tableMode;
    private String workloadContent;
    private String configName;

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }
    public String getTableMode() { return tableMode; }
    public void setTableMode(String tableMode) { this.tableMode = tableMode; }
    public String getWorkloadContent() { return workloadContent; }
    public void setWorkloadContent(String workloadContent) { this.workloadContent = workloadContent; }
    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }
}
