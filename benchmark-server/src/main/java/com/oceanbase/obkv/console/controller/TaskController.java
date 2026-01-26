package com.oceanbase.obkv.console.controller;

import com.oceanbase.obkv.console.dto.ApiResponse;
import com.oceanbase.obkv.console.dto.TaskRequest;
import com.oceanbase.obkv.console.dto.TaskResponse;
import com.oceanbase.obkv.console.model.Task;
import com.oceanbase.obkv.console.service.TaskService;
import com.oceanbase.obkv.console.service.WorkloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for task operations
 */
@Slf4j
@RestController
@RequestMapping("/api/task")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final WorkloadService workloadService;

    /**
     * Execute load operation
     */
    @PostMapping("/load")
    public ApiResponse<TaskResponse> load(@Valid @RequestBody TaskRequest request) {
        log.info("Starting load task for workload: {}", request.getWorkload());
        
        try {
            Task task = taskService.executeLoad(request);
            TaskResponse response = TaskResponse.started(
                    task.getTaskId(),
                    task.getWorkload(),
                    task.getOperation()
            );
            return ApiResponse.success(response, "Load 任务已启动");
        } catch (Exception e) {
            log.error("Failed to start load task for workload '{}': {}", request.getWorkload(), e.getMessage(), e);
            return ApiResponse.error("启动 Load 任务失败: " + e.getMessage());
        }
    }

    /**
     * Execute segment load operation
     */
    @PostMapping("/segment-load")
    public ApiResponse<TaskResponse> segmentLoad(@Valid @RequestBody TaskRequest request) {
        log.info("Starting segment load task for workload: {}", request.getWorkload());
        
        try {
            Task task = taskService.executeSegmentLoad(request);
            TaskResponse response = TaskResponse.started(
                    task.getTaskId(),
                    task.getWorkload(),
                    task.getOperation()
            );
            return ApiResponse.success(response, "分段导入任务已启动");
        } catch (Exception e) {
            log.error("Failed to start segment load task for workload '{}': {}", request.getWorkload(), e.getMessage(), e);
            return ApiResponse.error("启动分段导入任务失败: " + e.getMessage());
        }
    }

    /**
     * Execute run operation
     */
    @PostMapping("/run")
    public ApiResponse<TaskResponse> run(@Valid @RequestBody TaskRequest request) {
        log.info("Starting run task for workload: {}", request.getWorkload());
        
        try {
            Task task = taskService.executeRun(request);
            TaskResponse response = TaskResponse.started(
                    task.getTaskId(),
                    task.getWorkload(),
                    task.getOperation()
            );
            return ApiResponse.success(response, "Run 任务已启动");
        } catch (Exception e) {
            log.error("Failed to start run task for workload '{}': {}", request.getWorkload(), e.getMessage(), e);
            return ApiResponse.error("启动 Run 任务失败: " + e.getMessage());
        }
    }

    /**
     * Get task status
     */
    @GetMapping("/status/{taskId}")
    public ApiResponse<TaskResponse> getStatus(@PathVariable String taskId) {
        Task task = taskService.getTask(taskId);
        if (task == null) {
            return ApiResponse.error("任务不存在: " + taskId);
        }

        TaskResponse response = new TaskResponse();
        response.setSuccess(true);
        response.setTaskId(task.getTaskId());
        response.setWorkload(task.getWorkload());
        response.setOperation(task.getOperation());
        response.setStatus(task.getStatus());
        response.setStartTime(task.getStartTimeMillis());
        response.setEndTime(task.getEndTime());
        response.setDuration(task.getDuration());
        response.setResult(task.getResult());
        response.setLogs(task.getLogs());

        return ApiResponse.success(response);
    }

    /**
     * Stop a running task
     */
    @PostMapping("/stop/{taskId}")
    public ApiResponse<Boolean> stopTask(@PathVariable String taskId) {
        log.info("Stopping task: {}", taskId);
        
        boolean stopped = taskService.stopTask(taskId);
        if (stopped) {
            return ApiResponse.success(true, "任务已停止");
        } else {
            return ApiResponse.error("无法停止任务，任务可能不存在或已完成");
        }
    }

    /**
     * Get all tasks
     */
    @GetMapping("/list")
    public ApiResponse<List<TaskResponse>> listTasks() {
        List<Task> tasks = taskService.getAllTasks();
        List<TaskResponse> responses = tasks.stream()
                .map(this::toTaskResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    /**
     * Clear completed tasks
     */
    @PostMapping("/clear")
    public ApiResponse<Integer> clearTasks() {
        int count = taskService.clearCompletedTasks();
        return ApiResponse.success(count, "已清理 " + count + " 个已完成任务");
    }

    /**
     * Get available workloads
     */
    @GetMapping("/workloads")
    public ApiResponse<List<Map<String, String>>> getWorkloads() {
        String[] workloads = workloadService.getAvailableWorkloads();
        List<Map<String, String>> result = java.util.Arrays.stream(workloads)
                .map(w -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("name", w);
                    map.put("description", workloadService.getWorkloadDescription(w));
                    return map;
                })
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    /**
     * List result files
     */
    @GetMapping("/results")
    public ApiResponse<List<String>> listResults() {
        List<String> files = taskService.listResultFiles();
        return ApiResponse.success(files, "共 " + files.size() + " 个结果文件");
    }

    /**
     * Clear all result files
     */
    @PostMapping("/results/clear")
    public ApiResponse<Integer> clearResults() {
        log.info("Clearing all result files");
        
        // Check if any task is running
        if (taskService.hasRunningTask()) {
            return ApiResponse.error("有任务正在运行，无法清理结果文件");
        }
        
        int count = taskService.clearResultFiles();
        return ApiResponse.success(count, "已清理 " + count + " 个结果文件");
    }

    /**
     * Check if any task is running
     */
    @GetMapping("/running")
    public ApiResponse<Boolean> isRunning() {
        boolean running = taskService.hasRunningTask();
        return ApiResponse.success(running);
    }

    /**
     * Convert Task to TaskResponse
     */
    private TaskResponse toTaskResponse(Task task) {
        TaskResponse response = new TaskResponse();
        response.setSuccess(true);
        response.setTaskId(task.getTaskId());
        response.setWorkload(task.getWorkload());
        response.setOperation(task.getOperation());
        response.setStatus(task.getStatus());
        response.setStartTime(task.getStartTimeMillis());
        response.setEndTime(task.getEndTime());
        response.setDuration(task.getDuration());
        response.setResult(task.getResult());
        return response;
    }
}

