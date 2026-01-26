package com.oceanbase.obkv.console.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Task entity representing a benchmark task
 */
@Data
public class Task {

    /**
     * Unique task identifier
     */
    private String taskId;

    /**
     * Workload name (workloada ~ workloadf)
     */
    private String workload;

    /**
     * Operation type: "load" or "run"
     */
    private String operation;

    /**
     * Current task status
     */
    private TaskStatus status;

    /**
     * Task start time (epoch millis)
     */
    private Long startTimeMillis;

    /**
     * Task start time (for file naming)
     */
    private LocalDateTime startTime;

    /**
     * Task end time (epoch millis)
     */
    private Long endTime;

    /**
     * Task end time (LocalDateTime)
     */
    private LocalDateTime endTimeLocal;

    /**
     * Result file path (if saved)
     */
    private String resultFile;

    /**
     * Task result (JSON format)
     */
    private String result;

    /**
     * Execution logs
     */
    private List<String> logs = new ArrayList<>();

    /**
     * Process handles for the running task (supports multiple processes for segmented load)
     */
    private transient List<Process> processes = new ArrayList<>();

    /**
     * Add a process handle
     */
    public void addProcess(Process process) {
        if (processes == null) {
            processes = new ArrayList<>();
        }
        processes.add(process);
    }

    /**
     * Full YCSB command executed
     */
    private String command;

    /**
     * Create a new task with generated ID
     */
    public static Task create(String workload, String operation) {
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setWorkload(workload);
        task.setOperation(operation);
        task.setStatus(TaskStatus.PENDING);
        task.setStartTimeMillis(System.currentTimeMillis());
        task.setStartTime(LocalDateTime.now());
        return task;
    }

    /**
     * Add a log line
     */
    public void addLog(String log) {
        if (logs == null) {
            logs = new ArrayList<>();
        }
        logs.add(log);
    }

    /**
     * Mark task as completed
     */
    public void complete(String result) {
        this.status = TaskStatus.COMPLETED;
        this.endTime = System.currentTimeMillis();
        this.endTimeLocal = LocalDateTime.now();
        this.result = result;
    }

    /**
     * Mark task as failed
     */
    public void fail(String error) {
        this.status = TaskStatus.FAILED;
        this.endTime = System.currentTimeMillis();
        this.endTimeLocal = LocalDateTime.now();
        this.result = error;
    }

    /**
     * Mark task as stopped
     */
    public void stop() {
        this.status = TaskStatus.STOPPED;
        this.endTime = System.currentTimeMillis();
        this.endTimeLocal = LocalDateTime.now();
    }

    /**
     * Get task duration in milliseconds
     */
    public Long getDuration() {
        if (startTimeMillis == null) {
            return null;
        }
        long end = endTime != null ? endTime : System.currentTimeMillis();
        return end - startTimeMillis;
    }
}

