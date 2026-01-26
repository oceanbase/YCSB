package com.oceanbase.obkv.console.service;

import com.oceanbase.obkv.console.config.YcsbConfig;
import com.oceanbase.obkv.console.dto.TaskRequest;
import com.oceanbase.obkv.console.model.Task;
import com.oceanbase.obkv.console.model.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for managing and executing benchmark tasks
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final YcsbConfig ycsbConfig;
    private final WorkloadService workloadService;
    private final WebSocketService webSocketService;

    /**
     * Map of task ID to Task
     */
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    /**
     * Thread pool for async task execution
     */
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();

    /**
     * Result directory name
     */
    private static final String RESULT_DIR = "result";

    /**
     * Date format for result file names
     */
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Execute a segmented load operation
     */
    public Task executeSegmentLoad(TaskRequest request) throws IOException {
        Task task = Task.create(request.getWorkload(), "segment_load");
        tasks.put(task.getTaskId(), task);

        // Generate base workload file (shared by all segments)
        Path workloadFile = workloadService.generateWorkloadFile(request);

        // Start async execution using thread pool
        CompletableFuture.runAsync(() -> executeSegmentLoadAsync(request, task, workloadFile), taskExecutor);

        return task;
    }

    /**
     * Execute segmented load async
     */
    private void executeSegmentLoadAsync(TaskRequest request, Task task, Path workloadFile) {
        task.setStatus(TaskStatus.RUNNING);
        int totalSegments = request.getSegments() != null ? request.getSegments() : 1;
        int totalRecords = request.getWorkloadParams().getRecordcount();
        int batchSize = totalRecords / totalSegments;

        webSocketService.sendTaskStatus(task.getTaskId(), TaskStatus.RUNNING, 
                String.format("分段导入开始: 总记录数=%d, 分段数=%d, 每段约=%d", totalRecords, totalSegments, batchSize));

        List<Process> processes = new ArrayList<>();
        File workingDir = Paths.get(ycsbConfig.getJarPath()).toAbsolutePath().getParent().getParent().getParent().toFile();
        StringBuilder summaryResult = new StringBuilder();
        summaryResult.append("Segmented Load Summary:\n");

        try {
            for (int i = 0; i < totalSegments; i++) {
                int start = i * batchSize;
                int count = (i == totalSegments - 1) ? (totalRecords - start) : batchSize;

                List<String> command = buildCommand(request, workloadFile, true);
                command.add("-p");
                command.add("insertstart=" + start);
                command.add("-p");
                command.add("insertcount=" + count);

                String segmentLabel = "[Segment " + i + "]";
                task.addLog(segmentLabel + " 启动: start=" + start + ", count=" + count);
                webSocketService.sendTaskLog(task.getTaskId(), segmentLabel + " 启动...");

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(workingDir);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                task.addProcess(process);
                processes.add(process);

                // Start a dedicated thread to read this process's output
                int segmentIdx = i;
                taskExecutor.submit(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String logLine = segmentLabel + " " + line;
                            task.addLog(logLine);
                            webSocketService.sendTaskLog(task.getTaskId(), logLine);
                            
                            if (line.contains("[OVERALL]")) {
                                synchronized (summaryResult) {
                                    summaryResult.append(segmentLabel).append(" ").append(line).append("\n");
                                }
                            }
                        }
                    } catch (IOException e) {
                        log.error("Error reading segment {} output", segmentIdx, e);
                    }
                });
            }

            // Wait for all processes to complete
            boolean allSuccess = true;
            for (int i = 0; i < processes.size(); i++) {
                int exitCode = processes.get(i).waitFor();
                if (exitCode != 0) {
                    allSuccess = false;
                    task.addLog("[Segment " + i + "] 异常退出，退出码: " + exitCode);
                }
            }

            if (task.getStatus() == TaskStatus.STOPPED) {
                log.info("Segmented task {} was stopped by user", task.getTaskId());
            } else if (allSuccess) {
                task.complete(summaryResult.toString());
                webSocketService.sendTaskStatus(task.getTaskId(), TaskStatus.COMPLETED, "所有分段导入完成");
            } else {
                task.fail("部分分段导入失败");
                webSocketService.sendTaskStatus(task.getTaskId(), TaskStatus.FAILED, "部分分段导入失败");
            }

            saveTaskLogs(task, workingDir);

        } catch (Exception e) {
            log.error("Segmented load failed", e);
            task.fail(e.getMessage());
            webSocketService.sendTaskStatus(task.getTaskId(), TaskStatus.FAILED, "分段导入异常: " + e.getMessage());
        } finally {
            workloadService.deleteTempFile(workloadFile);
        }
    }

    /**
     * Execute a load operation
     */
    public Task executeLoad(TaskRequest request) throws IOException {
        Task task = Task.create(request.getWorkload(), "load");
        tasks.put(task.getTaskId(), task);

        // Generate workload file
        Path workloadFile = workloadService.generateWorkloadFile(request);

        // Start async execution using thread pool
        CompletableFuture.runAsync(() -> executeTaskAsync(request, task, workloadFile, true), taskExecutor);

        return task;
    }

    /**
     * Execute a run operation
     */
    public Task executeRun(TaskRequest request) throws IOException {
        Task task = Task.create(request.getWorkload(), "run");
        tasks.put(task.getTaskId(), task);

        // Generate workload file
        Path workloadFile = workloadService.generateWorkloadFile(request);

        // Start async execution using thread pool
        CompletableFuture.runAsync(() -> executeTaskAsync(request, task, workloadFile, false), taskExecutor);

        return task;
    }

    /**
     * Execute task (runs in background thread)
     */
    private void executeTaskAsync(TaskRequest request, Task task, Path workloadFile, boolean isLoad) {
        task.setStatus(TaskStatus.RUNNING);
        webSocketService.sendTaskStatus(task.getTaskId(), TaskStatus.RUNNING, "任务开始执行");

        try {
            // Build command
            List<String> command = buildCommand(request, workloadFile, isLoad);
            String commandStr = String.join(" ", command);
            task.setCommand(commandStr);
            log.info("Executing command: {}", commandStr);

            // Log workload parameters
            logWorkloadParameters(task, workloadFile);

            // Get working directory (Project Root)
            // jarPath is usually obkv-hbase/target/xxx.jar, so we go up 3 levels to get root
            File workingDir = Paths.get(ycsbConfig.getJarPath()).toAbsolutePath().getParent().getParent().getParent().toFile();

            // Start process
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            task.addProcess(process);

            // Read output
            StringBuilder resultBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    task.addLog(line);
                    webSocketService.sendTaskLog(task.getTaskId(), line);
                    log.debug("[{}] {}", task.getTaskId(), line);

                    // Capture result lines
                    if (line.contains("[OVERALL]") || line.contains("[READ]") || 
                        line.contains("[UPDATE]") || line.contains("[INSERT]") ||
                        line.contains("[SCAN]") || line.contains("[READ-MODIFY-WRITE]")) {
                        resultBuilder.append(line).append("\n");
                    }
                }
            }

            // Wait for process to complete
            int exitCode = process.waitFor();

            // Check if task was already stopped (by user request)
            if (task.getStatus() == TaskStatus.STOPPED) {
                log.info("Task {} was stopped by user", task.getTaskId());
                // Status already set, just save logs
            } else if (exitCode == 0) {
                task.complete(resultBuilder.toString());
                webSocketService.sendTaskStatus(task.getTaskId(), TaskStatus.COMPLETED, "任务执行完成");
                log.info("Task {} completed successfully", task.getTaskId());
            } else {
                // Check if it was killed (exit code 137 = SIGKILL, 143 = SIGTERM)
                if (exitCode == 137 || exitCode == 143) {
                    task.stop();
                    webSocketService.sendTaskStatus(task.getTaskId(), TaskStatus.STOPPED, "任务已停止");
                    log.info("Task {} was killed with exit code {}", task.getTaskId(), exitCode);
                } else {
                    task.fail("Exit code: " + exitCode);
                    webSocketService.sendTaskStatus(task.getTaskId(), TaskStatus.FAILED, "任务执行失败，退出码: " + exitCode);
                    log.error("Task {} failed with exit code {}", task.getTaskId(), exitCode);
                }
            }

            // Save logs to result file
            saveTaskLogs(task, workingDir);

        } catch (InterruptedException e) {
            task.stop();
            webSocketService.sendTaskStatus(task.getTaskId(), TaskStatus.STOPPED, "任务被中断");
            log.info("Task {} was interrupted", task.getTaskId());
            // Save logs even if interrupted
            try {
                File workingDir = Paths.get(ycsbConfig.getJarPath()).toAbsolutePath().getParent().getParent().getParent().toFile();
                saveTaskLogs(task, workingDir);
            } catch (Exception ex) {
                log.warn("Failed to save logs for interrupted task", ex);
            }
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            task.fail(e.getMessage());
            webSocketService.sendTaskStatus(task.getTaskId(), TaskStatus.FAILED, "任务执行异常: " + e.getMessage());
            log.error("Task {} failed with exception", task.getTaskId(), e);
            // Save logs even on failure
            try {
                File workingDir = Paths.get(ycsbConfig.getJarPath()).toAbsolutePath().getParent().getParent().getParent().toFile();
                saveTaskLogs(task, workingDir);
            } catch (Exception ex) {
                log.warn("Failed to save logs for failed task", ex);
            }
        } finally {
            // Clean up temp file
            workloadService.deleteTempFile(workloadFile);
        }
    }

    /**
     * Build the command to execute
     */
    private List<String> buildCommand(TaskRequest request, Path workloadFile, boolean isLoad) {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(Paths.get(ycsbConfig.getJarPath()).toAbsolutePath().toString());
        
        // Add DB implementation based on model type
        command.add("-db");
        if ("table".equalsIgnoreCase(request.getModelType())) {
            command.add("com.oceanbase.obkv.table.ycsb.ObTableClientDB");
        } else {
            command.add("com.oceanbase.obkv.ycsb.OBHBaseClient");
        }
        
        command.add("-P");
        command.add(workloadFile.toAbsolutePath().toString());

        if (isLoad) {
            command.add("-load");
        }

        return command;
    }

    /**
     * Log workload parameters to task log and WebSocket
     */
    private void logWorkloadParameters(Task task, Path workloadFile) {
        try {
            List<String> lines = Files.readAllLines(workloadFile);
            
            // Send header
            String header = "==================== 任务参数 ====================";
            task.addLog(header);
            webSocketService.sendTaskLog(task.getTaskId(), header);
            
            // Group parameters by category
            StringBuilder connParams = new StringBuilder();
            StringBuilder workloadParams = new StringBuilder();
            StringBuilder clientParams = new StringBuilder();
            StringBuilder otherParams = new StringBuilder();
            
            for (String line : lines) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                
                String formattedLine = "  " + line;
                task.addLog(formattedLine);
                webSocketService.sendTaskLog(task.getTaskId(), formattedLine);
            }
            
            String footer = "=".repeat(50);
            task.addLog(footer);
            webSocketService.sendTaskLog(task.getTaskId(), footer);
            
            log.info("Workload parameters logged for task {}", task.getTaskId());
        } catch (Exception e) {
            log.warn("Failed to log workload parameters", e);
        }
    }

    /**
     * Stop a running task
     */
    public boolean stopTask(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            return false;
        }

        if (task.getStatus() != TaskStatus.RUNNING) {
            return false;
        }

        List<Process> processes = task.getProcesses();
        boolean stopped = false;
        if (processes != null && !processes.isEmpty()) {
            for (Process process : processes) {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                    stopped = true;
                }
            }
        }

        if (stopped) {
            task.stop();
            webSocketService.sendTaskStatus(taskId, TaskStatus.STOPPED, "任务已停止");
            log.info("Task {} stopped (all child processes killed)", taskId);
            return true;
        }

        return false;
    }

    /**
     * Get task by ID
     */
    public Task getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * Get all tasks
     */
    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * Get tasks by status
     */
    public List<Task> getTasksByStatus(TaskStatus status) {
        List<Task> result = new ArrayList<>();
        for (Task task : tasks.values()) {
            if (task.getStatus() == status) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * Clear completed tasks
     */
    public int clearCompletedTasks() {
        int count = 0;
        for (String taskId : new ArrayList<>(tasks.keySet())) {
            Task task = tasks.get(taskId);
            if (task.getStatus() == TaskStatus.COMPLETED || 
                task.getStatus() == TaskStatus.FAILED ||
                task.getStatus() == TaskStatus.STOPPED) {
                tasks.remove(taskId);
                count++;
            }
        }
        return count;
    }

    /**
     * Save task logs to result directory
     * File name format: {workload}_{operation}_{status}_{timestamp}.log
     */
    private void saveTaskLogs(Task task, File workingDir) {
        try {
            // Create result directory if not exists
            Path resultDir = Paths.get(workingDir.getAbsolutePath(), RESULT_DIR);
            Files.createDirectories(resultDir);

            // Build file name: workloada_load_completed_20240116_143052.log
            String status = task.getStatus().name().toLowerCase();
            String timestamp = task.getStartTime().format(FILE_DATE_FORMAT);
            String fileName = String.format("%s_%s_%s_%s.log",
                    task.getWorkload(),
                    task.getOperation(),
                    status,
                    timestamp);

            Path logFile = resultDir.resolve(fileName);
            task.setResultFile(logFile.toString());

            // Write logs to file
            try (BufferedWriter writer = Files.newBufferedWriter(logFile)) {
                // Write header
                writer.write("=".repeat(80));
                writer.newLine();
                writer.write("YCSB Benchmark Result");
                writer.newLine();
                writer.write("=".repeat(80));
                writer.newLine();
                writer.write("Task ID:    " + task.getTaskId());
                writer.newLine();
                writer.write("Workload:   " + task.getWorkload());
                writer.newLine();
                writer.write("Operation:  " + task.getOperation());
                writer.newLine();
                writer.write("Status:     " + task.getStatus());
                writer.newLine();
                writer.write("Start Time: " + task.getStartTime());
                writer.newLine();
                writer.write("End Time:   " + task.getEndTime());
                writer.newLine();
                writer.write("=".repeat(80));
                writer.newLine();
                writer.newLine();
                
                // Write YCSB command
                writer.write("YCSB Command:");
                writer.newLine();
                writer.write("-".repeat(80));
                writer.newLine();
                if (task.getCommand() != null) {
                    writer.write(task.getCommand());
                } else {
                    writer.write("(command not available)");
                }
                writer.newLine();
                writer.write("-".repeat(80));
                writer.newLine();
                writer.newLine();

                // Write all logs
                for (String log : task.getLogs()) {
                    writer.write(log);
                    writer.newLine();
                }

                // Write summary result
                if (task.getResult() != null && !task.getResult().isEmpty()) {
                    writer.newLine();
                    writer.write("=".repeat(80));
                    writer.newLine();
                    writer.write("SUMMARY");
                    writer.newLine();
                    writer.write("=".repeat(80));
                    writer.newLine();
                    writer.write(task.getResult());
                }
            }

            log.info("Task logs saved to: {}", logFile);
            webSocketService.sendTaskLog(task.getTaskId(), "[系统] 日志已保存到: " + fileName);

        } catch (Exception e) {
            log.error("Failed to save task logs", e);
        }
    }

    /**
     * Get result directory path
     */
    public Path getResultDir() {
        File workingDir = Paths.get(ycsbConfig.getJarPath()).toAbsolutePath().getParent().getParent().getParent().toFile();
        return Paths.get(workingDir.getAbsolutePath(), RESULT_DIR);
    }

    /**
     * List all result files
     */
    public List<String> listResultFiles() {
        Path resultDir = getResultDir();
        if (!Files.exists(resultDir)) {
            return new ArrayList<>();
        }

        try {
            File[] files = resultDir.toFile().listFiles((dir, name) -> name.endsWith(".log"));
            if (files == null) {
                return new ArrayList<>();
            }
            
            // Sort by last modified (newest first)
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            
            List<String> result = new ArrayList<>();
            for (File file : files) {
                result.add(file.getName());
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to list result files", e);
            return new ArrayList<>();
        }
    }

    /**
     * Clear all result files
     * @return number of files deleted
     */
    public int clearResultFiles() {
        Path resultDir = getResultDir();
        if (!Files.exists(resultDir)) {
            return 0;
        }

        try {
            File[] files = resultDir.toFile().listFiles((dir, name) -> name.endsWith(".log"));
            if (files == null) {
                return 0;
            }

            int count = 0;
            for (File file : files) {
                if (file.delete()) {
                    count++;
                }
            }
            log.info("Cleared {} result files", count);
            return count;
        } catch (Exception e) {
            log.error("Failed to clear result files", e);
            return 0;
        }
    }

    /**
     * Check if any task is running
     */
    public boolean hasRunningTask() {
        for (Task task : tasks.values()) {
            if (task.getStatus() == TaskStatus.RUNNING) {
                return true;
            }
        }
        return false;
    }
}

