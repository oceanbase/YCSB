package com.oceanbase.obkv.webui.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Represents a module descriptor loaded from modules/*.json.
 * All module-specific knowledge (parameters, test types, connection modes) lives here,
 * keeping Java/JS code free of per-module hardcoding.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModuleDescriptor {

    private String moduleId;
    private String displayName;
    private String jarPathConfig;
    private String workdirConfig;
    private List<String> commandFlags;
    private String loadFlag;

    private Map<String, ConnectionMode> connectionModes;
    private List<TableMode> tableModes;
    private List<PartitionType> partitionTypes;
    private List<TestType> testTypes;
    private List<String> knownParams;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConnectionMode {
        private List<FieldDef> fields;

        public List<FieldDef> getFields() { return fields; }
        public void setFields(List<FieldDef> fields) { this.fields = fields; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldDef {
        private String key;
        private String label;
        private String type;
        private boolean required;
        private String defaultValue;
        private List<String> options;
        private String value;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableMode {
        private String id;
        private String label;
        private List<FieldDef> fields;
        private Map<String, String> fixedValues;
        private List<Map<String, String>> validation;
        private String dbClass;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public List<FieldDef> getFields() { return fields; }
        public void setFields(List<FieldDef> fields) { this.fields = fields; }
        public Map<String, String> getFixedValues() { return fixedValues; }
        public void setFixedValues(Map<String, String> fixedValues) { this.fixedValues = fixedValues; }
        public List<Map<String, String>> getValidation() { return validation; }
        public void setValidation(List<Map<String, String>> validation) { this.validation = validation; }
        public String getDbClass() { return dbClass; }
        public void setDbClass(String dbClass) { this.dbClass = dbClass; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PartitionType {
        private String id;
        private String label;
        private List<FieldDef> fields;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public List<FieldDef> getFields() { return fields; }
        public void setFields(List<FieldDef> fields) { this.fields = fields; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TestType {
        private String id;
        private String label;
        @JsonProperty("isLoad")
        private boolean isLoad;
        private Map<String, Object> proportions;
        private List<FieldDef> extraFields;
        private String dbClass;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public boolean isLoad() { return isLoad; }
        public void setLoad(boolean load) { isLoad = load; }
        public Map<String, Object> getProportions() { return proportions; }
        public void setProportions(Map<String, Object> proportions) { this.proportions = proportions; }
        public List<FieldDef> getExtraFields() { return extraFields; }
        public void setExtraFields(List<FieldDef> extraFields) { this.extraFields = extraFields; }
        public String getDbClass() { return dbClass; }
        public void setDbClass(String dbClass) { this.dbClass = dbClass; }
    }

    public String getModuleId() { return moduleId; }
    public void setModuleId(String moduleId) { this.moduleId = moduleId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getJarPathConfig() { return jarPathConfig; }
    public void setJarPathConfig(String jarPathConfig) { this.jarPathConfig = jarPathConfig; }
    public String getWorkdirConfig() { return workdirConfig; }
    public void setWorkdirConfig(String workdirConfig) { this.workdirConfig = workdirConfig; }
    public List<String> getCommandFlags() { return commandFlags; }
    public void setCommandFlags(List<String> commandFlags) { this.commandFlags = commandFlags; }
    public String getLoadFlag() { return loadFlag; }
    public void setLoadFlag(String loadFlag) { this.loadFlag = loadFlag; }
    public Map<String, ConnectionMode> getConnectionModes() { return connectionModes; }
    public void setConnectionModes(Map<String, ConnectionMode> connectionModes) { this.connectionModes = connectionModes; }
    public List<TableMode> getTableModes() { return tableModes; }
    public void setTableModes(List<TableMode> tableModes) { this.tableModes = tableModes; }
    public List<PartitionType> getPartitionTypes() { return partitionTypes; }
    public void setPartitionTypes(List<PartitionType> partitionTypes) { this.partitionTypes = partitionTypes; }
    public List<TestType> getTestTypes() { return testTypes; }
    public void setTestTypes(List<TestType> testTypes) { this.testTypes = testTypes; }
    public List<String> getKnownParams() { return knownParams; }
    public void setKnownParams(List<String> knownParams) { this.knownParams = knownParams; }
}
