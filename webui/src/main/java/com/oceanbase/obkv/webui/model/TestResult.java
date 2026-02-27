package com.oceanbase.obkv.webui.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Structured test result, stored as result.json (final) or result_snapshot.json (intermediate).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestResult {

    private double throughput;
    private long runTimeMs;
    private long totalOps;
    private List<OperationResult> operations;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OperationResult {
        private String type;
        private long count;
        private double avgLatencyUs;
        private double p95LatencyUs;
        private double p99LatencyUs;
        private double minLatencyUs;
        private double maxLatencyUs;
        private long returnOK;
        private long returnError;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
        public double getAvgLatencyUs() { return avgLatencyUs; }
        public void setAvgLatencyUs(double avgLatencyUs) { this.avgLatencyUs = avgLatencyUs; }
        public double getP95LatencyUs() { return p95LatencyUs; }
        public void setP95LatencyUs(double p95LatencyUs) { this.p95LatencyUs = p95LatencyUs; }
        public double getP99LatencyUs() { return p99LatencyUs; }
        public void setP99LatencyUs(double p99LatencyUs) { this.p99LatencyUs = p99LatencyUs; }
        public double getMinLatencyUs() { return minLatencyUs; }
        public void setMinLatencyUs(double minLatencyUs) { this.minLatencyUs = minLatencyUs; }
        public double getMaxLatencyUs() { return maxLatencyUs; }
        public void setMaxLatencyUs(double maxLatencyUs) { this.maxLatencyUs = maxLatencyUs; }
        public long getReturnOK() { return returnOK; }
        public void setReturnOK(long returnOK) { this.returnOK = returnOK; }
        public long getReturnError() { return returnError; }
        public void setReturnError(long returnError) { this.returnError = returnError; }
    }

    public double getThroughput() { return throughput; }
    public void setThroughput(double throughput) { this.throughput = throughput; }
    public long getRunTimeMs() { return runTimeMs; }
    public void setRunTimeMs(long runTimeMs) { this.runTimeMs = runTimeMs; }
    public long getTotalOps() { return totalOps; }
    public void setTotalOps(long totalOps) { this.totalOps = totalOps; }
    public List<OperationResult> getOperations() { return operations; }
    public void setOperations(List<OperationResult> operations) { this.operations = operations; }
}
