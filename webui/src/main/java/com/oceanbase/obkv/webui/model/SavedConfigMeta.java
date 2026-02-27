package com.oceanbase.obkv.webui.model;

/**
 * Summary entry for a saved configuration, returned in GET /api/configs list.
 */
public class SavedConfigMeta {

    private String name;
    private String module;
    private String testType;
    private String savedAt;

    public SavedConfigMeta() {}

    public SavedConfigMeta(String name, String module, String testType, String savedAt) {
        this.name = name;
        this.module = module;
        this.testType = testType;
        this.savedAt = savedAt;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }
    public String getSavedAt() { return savedAt; }
    public void setSavedAt(String savedAt) { this.savedAt = savedAt; }
}
