package com.oceanbase.obkv.console.dto;

import com.oceanbase.obkv.console.model.TaskStatus;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for task operations
 */
@Data
public class TaskResponse {

    /**
     * Whether the operation was successful
     */
    private boolean success;

    /**
     * Response message
     */
    private String message;

    /**
     * Task ID
     */
    private String taskId;

    /**
     * Task status
     */
    private TaskStatus status;

    /**
     * Workload name
     */
    private String workload;

    /**
     * Operation type (load/run)
     */
    private String operation;

    /**
     * Task start time
     */
    private Long startTime;

    /**
     * Task end time
     */
    private Long endTime;

    /**
     * Task duration in milliseconds
     */
    private Long duration;

    /**
     * Task result
     */
    private String result;

    /**
     * Task logs
     */
    private List<String> logs;

    /**
     * Create a response for task started
     */
    public static TaskResponse started(String taskId, String workload, String operation) {
        TaskResponse response = new TaskResponse();
        response.setSuccess(true);
        response.setMessage("Task started");
        response.setTaskId(taskId);
        response.setWorkload(workload);
        response.setOperation(operation);
        response.setStatus(TaskStatus.RUNNING);
        response.setStartTime(System.currentTimeMillis());
        return response;
    }

    /**
     * Create an error response
     */
    public static TaskResponse error(String message) {
        TaskResponse response = new TaskResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}

