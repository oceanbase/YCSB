package com.oceanbase.obkv.webui.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Persisted metadata for a single test run, stored as meta.json in webui-runs/{testId}/.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RunMeta {

    private String testId;
    private String module;
    private String testType;
    private String tableMode;
    private String status;      // RUNNING / COMPLETED / FAILED / STOPPED / UNKNOWN / DISK_FULL / ERROR
    private String startTime;
    private String endTime;
    private long durationMs;
    private long ycsbPid;
    private String configName;

    public String getTestId() { return testId; }
    public void setTestId(String testId) { this.testId = testId; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }
    public String getTableMode() { return tableMode; }
    public void setTableMode(String tableMode) { this.tableMode = tableMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public long getYcsbPid() { return ycsbPid; }
    public void setYcsbPid(long ycsbPid) { this.ycsbPid = ycsbPid; }
    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }
}
