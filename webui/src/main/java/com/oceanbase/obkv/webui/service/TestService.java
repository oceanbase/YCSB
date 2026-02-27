package com.oceanbase.obkv.webui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oceanbase.obkv.webui.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Core service for managing YCSB test processes.
 *
 * Architecture for zero-memory log accumulation:
 *   YCSB stdout → reader thread → output.log (line-buffered, auto-flush)
 *                                      ↓
 *                           tail-follower thread → SseEmitter (Last-Event-ID = byte offset)
 *
 * webui JVM memory stays bounded regardless of test duration or concurrency.
 */
@Service
public class TestService {

    private static final Logger log = LoggerFactory.getLogger(TestService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Pattern OVERALL_PATTERN = Pattern.compile("\\[OVERALL\\].*?Throughput\\(ops/sec\\),\\s*([\\d.]+)");
    private static final Pattern RUNTIME_PATTERN = Pattern.compile("\\[OVERALL\\].*?RunTime\\(ms\\),\\s*([\\d.]+)");
    private static final Pattern OPS_PATTERN    = Pattern.compile("\\[OVERALL\\].*?Operations,\\s*(\\d+)");
    private static final Pattern OP_PATTERN     = Pattern.compile("\\[([A-Z_]+)\\],\\s*([^,]+),\\s*([\\d.]+)");
    private static final Pattern STATUS_PATTERN = Pattern.compile("(\\d+) sec:.*?(\\d+) operations");

    @Value("${webui.runs.dir:webui-runs}")
    private String runsDir;

    @Value("${webui.hbase.jar.path:obkv-hbase/build/obkv-hbase-0.18.0-SNAPSHOT-jar-with-dependencies.jar}")
    private String hbaseJarPath;

    @Value("${webui.table.jar.path:obkv-table/build/obkv-table-0.18.0-SNAPSHOT-jar-with-dependencies.jar}")
    private String tableJarPath;

    @Value("${webui.hbase.workdir:obkv-hbase}")
    private String hbaseWorkdir;

    @Value("${webui.table.workdir:obkv-table}")
    private String tableWorkdir;

    @Value("${webui.ycsb.jvm.max-heap:512m}")
    private String ycsbMaxHeap;

    @Value("${webui.max.concurrent.tests:5}")
    private int maxConcurrentTests;

    @Value("${webui.runs.max-count:100}")
    private int runsMaxCount;

    @Autowired
    private ModuleService moduleService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, TestRun> activeRuns = new ConcurrentHashMap<>();
    private final List<RunMeta> historyIndex = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sse-tail");
        t.setDaemon(true);
        return t;
    });
    private Path runsPath;

    @PostConstruct
    public void init() throws IOException {
        runsPath = Paths.get(runsDir).toAbsolutePath();
        Files.createDirectories(runsPath);
        log.info("Runs directory: {}", runsPath);
        loadHistoryIndex();
    }

    private void loadHistoryIndex() {
        try {
            List<RunMeta> loaded = Files.list(runsPath)
                .filter(Files::isDirectory)
                .map(dir -> {
                    try {
                        Path metaFile = dir.resolve("meta.json");
                        if (Files.exists(metaFile)) {
                            RunMeta meta = objectMapper.readValue(metaFile.toFile(), RunMeta.class);
                            if ("RUNNING".equals(meta.getStatus())) {
                                meta.setStatus("UNKNOWN");
                                writeMeta(dir, meta);
                                log.warn("Marked stale RUNNING test as UNKNOWN: {}", meta.getTestId());
                            }
                            return meta;
                        }
                    } catch (IOException e) {
                        log.warn("Failed to load meta from {}", dir, e);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RunMeta::getStartTime).reversed())
                .collect(Collectors.toList());
            historyIndex.addAll(loaded);
            log.info("Loaded {} historical test records", loaded.size());
        } catch (IOException e) {
            log.error("Failed to load history index", e);
        }
    }

    public int getRunningCount() {
        return (int) activeRuns.values().stream().filter(TestRun::isRunning).count();
    }

    public String startTest(TestConfig config) throws IOException {
        if (getRunningCount() >= maxConcurrentTests) {
            throw new IllegalStateException("Max concurrent tests reached: " + maxConcurrentTests);
        }

        String module = config.getModule();
        ModuleDescriptor desc = moduleService.getModule(module);
        if (desc == null) {
            throw new IllegalArgumentException("Unknown module: " + module);
        }

        String testId = UUID.randomUUID().toString();
        Path testDir = runsPath.resolve(testId);
        Files.createDirectories(testDir);

        // Write workload.properties
        Path workloadFile = testDir.resolve("workload.properties");
        Files.write(workloadFile, config.getWorkloadContent().getBytes(StandardCharsets.UTF_8));

        // Write initial meta.json
        RunMeta meta = new RunMeta();
        meta.setTestId(testId);
        meta.setModule(module);
        meta.setTestType(config.getTestType());
        meta.setTableMode(config.getTableMode());
        meta.setStatus("RUNNING");
        meta.setStartTime(LocalDateTime.now().format(FMT));
        meta.setConfigName(config.getConfigName());
        writeMeta(testDir, meta);

        // Build command
        List<String> cmd = buildCommand(desc, config, workloadFile.toAbsolutePath().toString());

        // Determine working directory
        String workdirStr = "obkv-hbase".equals(module) ? hbaseWorkdir : tableWorkdir;
        File workdir = Paths.get(workdirStr).toAbsolutePath().toFile();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workdir);
        pb.redirectErrorStream(true);  // merge stderr into stdout to prevent pipe deadlock

        TestRun run = new TestRun(testId, meta);
        Process process = pb.start();
        // process.pid() is Java 9+; use reflection fallback for Java 8
        try {
            java.lang.reflect.Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            meta.setYcsbPid(pidField.getLong(process));
        } catch (Exception e) {
            meta.setYcsbPid(-1);
        }
        writeMeta(testDir, meta);
        run.setProcess(process);

        activeRuns.put(testId, run);

        // Reader thread: consume stdout line by line → write to output.log
        Path outputLog = testDir.resolve("output.log");
        PrintWriter logWriter;
        try {
            logWriter = new PrintWriter(new BufferedWriter(new FileWriter(outputLog.toFile(), true)), true);
        } catch (IOException e) {
            throw new IOException("Cannot open output.log: " + e.getMessage(), e);
        }

        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logWriter.println(line);
                }
            } catch (IOException e) {
                String errMsg = "[WEBUI ERROR] Reader thread exception: " + e.getMessage();
                logWriter.println(errMsg);
                meta.setStatus("ERROR");
                try { writeMeta(testDir, meta); } catch (IOException ex) { /* ignore */ }
                broadcastToEmitters(run, "error", errMsg);
            } finally {
                logWriter.close();
                onProcessFinished(run, testDir, meta);
            }
        }, "reader-" + testId.substring(0, 8));
        readerThread.setDaemon(true);
        run.setReaderThread(readerThread);
        readerThread.start();

        // tail-follower thread: read output.log new lines → push to SSE emitters
        AtomicInteger snapshotTimer = new AtomicInteger(0);
        Thread tailThread = new Thread(() -> {
            try (RandomAccessFile raf = new RandomAccessFile(outputLog.toFile(), "r")) {
                while (run.isRunning() || raf.getFilePointer() < raf.length()) {
                    long fileLen = raf.length();
                    long pos = raf.getFilePointer();
                    if (pos < fileLen) {
                        String line = raf.readLine();
                        if (line != null) {
                            String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                            run.setTailOffset(raf.getFilePointer());
                            broadcastToEmitters(run, "log", decoded);
                            // Parse [STATUS] lines for intermediate snapshots
                            if (decoded.contains("[STATUS]") || decoded.contains("sec:")) {
                                int count = snapshotTimer.incrementAndGet();
                                // roughly every 60 lines of status output ≈ 60 seconds
                                if (count % 60 == 0) {
                                    tryWriteSnapshot(testDir, run);
                                }
                            }
                        }
                    } else {
                        Thread.sleep(200);
                        // Send SSE heartbeat every ~15s to keep connection alive
                        if (System.currentTimeMillis() % 15000 < 200) {
                            sendHeartbeat(run);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (FileNotFoundException e) {
                log.debug("output.log not yet available for {}", testId);
            } catch (IOException e) {
                log.warn("tail-follower error for {}: {}", testId, e.getMessage());
            } finally {
                broadcastToEmitters(run, "done", "EOF");
                run.getEmitters().forEach(SseEmitter::complete);
                run.getEmitters().clear();
            }
        }, "tail-" + testId.substring(0, 8));
        tailThread.setDaemon(true);
        run.setTailThread(tailThread);
        tailThread.start();

        // Add to history
        addToHistory(meta);
        pruneHistory();

        log.info("Started test {} (module={}, type={})", testId, module, config.getTestType());
        return testId;
    }

    private List<String> buildCommand(ModuleDescriptor desc, TestConfig config, String workloadPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-Xmx" + ycsbMaxHeap);
        cmd.add("-jar");

        String module = config.getModule();
        String jarPath = "obkv-hbase".equals(module)
            ? Paths.get(hbaseJarPath).toAbsolutePath().toString()
            : Paths.get(tableJarPath).toAbsolutePath().toString();
        cmd.add(jarPath);

        // Load flag for load tests
        boolean isLoad = false;
        if (desc.getTestTypes() != null) {
            for (ModuleDescriptor.TestType tt : desc.getTestTypes()) {
                if (tt.getId().equals(config.getTestType()) && tt.isLoad()) {
                    isLoad = true;
                    // Use dbClass from testType if specified, else from tableMode
                    if (tt.getDbClass() != null) {
                        cmd.add("-db");
                        cmd.add(tt.getDbClass());
                    }
                    break;
                }
            }
        }

        // For obkv-table, add -db based on tableMode
        if ("obkv-table".equals(module) && config.getTableMode() != null && desc.getTableModes() != null) {
            for (ModuleDescriptor.TableMode tm : desc.getTableModes()) {
                if (tm.getId().equals(config.getTableMode()) && tm.getDbClass() != null) {
                    // Only add if not already added above
                    if (!cmd.contains("-db")) {
                        cmd.add("-db");
                        cmd.add(tm.getDbClass());
                    }
                    break;
                }
            }
        }

        if (isLoad) {
            String loadFlag = desc.getLoadFlag() != null ? desc.getLoadFlag() : "-load";
            cmd.add(loadFlag);
        } else {
            cmd.add("-t");
        }

        cmd.add("-P");
        cmd.add(workloadPath);

        // Add module command flags (e.g. -s for status thread)
        if (desc.getCommandFlags() != null) {
            cmd.addAll(desc.getCommandFlags());
        }

        return cmd;
    }

    private void onProcessFinished(TestRun run, Path testDir, RunMeta meta) {
        long durationMs = (System.nanoTime() - run.getStartNano()) / 1_000_000L;
        meta.setDurationMs(durationMs);
        meta.setEndTime(LocalDateTime.now().format(FMT));

        int exitCode = 0;
        try {
            exitCode = run.getProcess().exitValue();
        } catch (IllegalThreadStateException e) {
            exitCode = -1;
        }

        if (!"STOPPED".equals(meta.getStatus()) && !"ERROR".equals(meta.getStatus())
                && !"DISK_FULL".equals(meta.getStatus())) {
            meta.setStatus(exitCode == 0 ? "COMPLETED" : "FAILED");
        }

        try {
            writeMeta(testDir, meta);
            parseAndWriteResult(testDir);
        } catch (IOException e) {
            log.error("Failed to write result for {}", run.getTestId(), e);
        }

        updateHistoryEntry(meta);
        activeRuns.remove(run.getTestId());
        log.info("Test {} finished: status={}, duration={}ms", run.getTestId(), meta.getStatus(), durationMs);
    }

    public void stopTest(String testId) throws IOException {
        TestRun run = activeRuns.get(testId);
        if (run == null) {
            throw new FileNotFoundException("Test not found or not running: " + testId);
        }
        RunMeta meta = run.getMeta();
        meta.setStatus("STOPPED");
        Path testDir = runsPath.resolve(testId);
        writeMeta(testDir, meta);

        Process process = run.getProcess();
        if (process != null && process.isAlive()) {
            process.destroy();  // SIGTERM
            // Force kill after 10s if still alive
            new Thread(() -> {
                try {
                    if (!process.waitFor(10, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        log.warn("Force-killed YCSB process for test {}", testId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "stopper-" + testId.substring(0, 8)).start();
        }
    }

    public SseEmitter streamLog(String testId, long lastEventId) throws IOException {
        Path testDir = runsPath.resolve(testId);
        if (!Files.exists(testDir)) {
            throw new FileNotFoundException("Test not found: " + testId);
        }

        SseEmitter emitter = new SseEmitter(0L); // no timeout

        TestRun run = activeRuns.get(testId);
        if (run != null && run.isRunning()) {
            // Live test: add emitter and tail-follower will push to it
            // First replay already-written content from current offset
            long currentOffset = run.getTailOffset();
            if (lastEventId > 0 && lastEventId < currentOffset) {
                currentOffset = lastEventId;
            } else if (lastEventId == 0) {
                currentOffset = 0;
            }
            final long replayFrom = currentOffset;
            run.addEmitter(emitter);
            emitter.onCompletion(() -> run.removeEmitter(emitter));
            emitter.onTimeout(() -> run.removeEmitter(emitter));
            emitter.onError(e -> run.removeEmitter(emitter));

            if (replayFrom > 0) {
                sseExecutor.submit(() -> replaySince(emitter, testDir.resolve("output.log"), replayFrom));
            }
        } else {
            // Completed test: replay full log
            sseExecutor.submit(() -> {
                Path outputLog = testDir.resolve("output.log");
                replaySince(emitter, outputLog, lastEventId > 0 ? lastEventId : 0);
                try {
                    emitter.send(SseEmitter.event().name("done").data("EOF"));
                } catch (IOException e) {
                    // client disconnected
                }
                emitter.complete();
            });
        }

        return emitter;
    }

    private void replaySince(SseEmitter emitter, Path logFile, long fromOffset) {
        if (!Files.exists(logFile)) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            raf.seek(fromOffset);
            String line;
            while ((line = raf.readLine()) != null) {
                String decoded = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                emitter.send(SseEmitter.event().name("log").data(decoded).id(String.valueOf(raf.getFilePointer())));
            }
        } catch (IOException e) {
            log.debug("Replay interrupted for {}", logFile, e);
        }
    }

    private void broadcastToEmitters(TestRun run, String eventName, String data) {
        List<SseEmitter> emitters = run.getEmitters();
        if (emitters.isEmpty()) return;
        List<SseEmitter> toRemove = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data).id(String.valueOf(run.getTailOffset())));
            } catch (IOException e) {
                toRemove.add(emitter);
            }
        }
        emitters.removeAll(toRemove);
    }

    private void sendHeartbeat(TestRun run) {
        List<SseEmitter> emitters = run.getEmitters();
        if (emitters.isEmpty()) return;
        List<SseEmitter> toRemove = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                toRemove.add(emitter);
            }
        }
        emitters.removeAll(toRemove);
    }

    private void parseAndWriteResult(Path testDir) throws IOException {
        Path outputLog = testDir.resolve("output.log");
        if (!Files.exists(outputLog)) return;

        List<String> lines = Files.readAllLines(outputLog, StandardCharsets.UTF_8);
        TestResult result = parseResult(lines);
        if (result != null) {
            objectMapper.writeValue(testDir.resolve("result.json").toFile(), result);
        }
    }

    private TestResult parseResult(List<String> lines) {
        double throughput = 0;
        long runTimeMs = 0;
        long totalOps = 0;
        Map<String, TestResult.OperationResult> ops = new LinkedHashMap<>();

        for (String line : lines) {
            Matcher m = OVERALL_PATTERN.matcher(line);
            if (m.find()) throughput = Double.parseDouble(m.group(1));

            m = RUNTIME_PATTERN.matcher(line);
            if (m.find()) runTimeMs = (long) Double.parseDouble(m.group(1));

            m = OPS_PATTERN.matcher(line);
            if (m.find()) totalOps = Long.parseLong(m.group(1));

            m = OP_PATTERN.matcher(line);
            if (m.find()) {
                String opType = m.group(1);
                String metric = m.group(2).trim();
                String val = m.group(3);
                if ("OVERALL".equals(opType)) continue;

                TestResult.OperationResult op = ops.computeIfAbsent(opType, k -> {
                    TestResult.OperationResult r = new TestResult.OperationResult();
                    r.setType(k);
                    return r;
                });
                switch (metric) {
                    case "Operations":        op.setCount(Long.parseLong(val)); break;
                    case "AverageLatency(us)": op.setAvgLatencyUs(Double.parseDouble(val)); break;
                    case "95thPercentileLatency(us)": op.setP95LatencyUs(Double.parseDouble(val)); break;
                    case "99thPercentileLatency(us)": op.setP99LatencyUs(Double.parseDouble(val)); break;
                    case "MinLatency(us)":    op.setMinLatencyUs(Double.parseDouble(val)); break;
                    case "MaxLatency(us)":    op.setMaxLatencyUs(Double.parseDouble(val)); break;
                    case "Return=OK":         op.setReturnOK(Long.parseLong(val)); break;
                    case "Return=ERROR":      op.setReturnError(Long.parseLong(val)); break;
                }
            }
        }

        if (throughput == 0 && ops.isEmpty()) return null;

        TestResult result = new TestResult();
        result.setThroughput(throughput);
        result.setRunTimeMs(runTimeMs);
        result.setTotalOps(totalOps);
        result.setOperations(new ArrayList<>(ops.values()));
        return result;
    }

    private void tryWriteSnapshot(Path testDir, TestRun run) {
        try {
            Path outputLog = testDir.resolve("output.log");
            if (Files.exists(outputLog)) {
                List<String> lines = Files.readAllLines(outputLog, StandardCharsets.UTF_8);
                TestResult snap = parseResult(lines);
                if (snap != null) {
                    objectMapper.writeValue(testDir.resolve("result_snapshot.json").toFile(), snap);
                }
            }
        } catch (IOException e) {
            log.debug("Snapshot write failed for {}", run.getTestId(), e);
        }
    }

    public List<RunMeta> listHistory(int page, int size) {
        int fromIndex = page * size;
        if (fromIndex >= historyIndex.size()) return Collections.emptyList();
        int toIndex = Math.min(fromIndex + size, historyIndex.size());
        return new ArrayList<>(historyIndex.subList(fromIndex, toIndex));
    }

    public RunMeta getRunMeta(String testId) throws IOException {
        Path metaFile = runsPath.resolve(testId).resolve("meta.json");
        if (!Files.exists(metaFile)) throw new FileNotFoundException("Test not found: " + testId);
        return objectMapper.readValue(metaFile.toFile(), RunMeta.class);
    }

    public TestResult getResult(String testId) throws IOException {
        Path testDir = runsPath.resolve(testId);
        Path resultFile = testDir.resolve("result.json");
        if (!Files.exists(resultFile)) {
            resultFile = testDir.resolve("result_snapshot.json");
        }
        if (!Files.exists(resultFile)) return null;
        return objectMapper.readValue(resultFile.toFile(), TestResult.class);
    }

    public String getLogChunk(String testId, long offset, int limit) throws IOException {
        Path logFile = runsPath.resolve(testId).resolve("output.log");
        if (!Files.exists(logFile)) return "";

        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        int total = lines.size();
        int fromLine;
        int toLine;
        if (offset < 0) {
            // negative offset means from end
            fromLine = Math.max(0, total + (int) offset);
            toLine = total;
        } else if (offset == 0 && limit <= 0) {
            fromLine = Math.max(0, total - 2000);
            toLine = total;
        } else {
            fromLine = (int) Math.min(offset, total);
            toLine = Math.min(fromLine + (limit > 0 ? limit : 2000), total);
        }
        return lines.subList(fromLine, toLine).stream().collect(Collectors.joining("\n"));
    }

    public String getWorkload(String testId) throws IOException {
        Path wf = runsPath.resolve(testId).resolve("workload.properties");
        if (!Files.exists(wf)) throw new FileNotFoundException("Workload not found: " + testId);
        return new String(Files.readAllBytes(wf), StandardCharsets.UTF_8);
    }

    public void deleteRecord(String testId) throws IOException {
        Path testDir = runsPath.resolve(testId);
        if (!Files.exists(testDir)) throw new FileNotFoundException("Test not found: " + testId);
        // Refuse to delete running test
        TestRun run = activeRuns.get(testId);
        if (run != null && run.isRunning()) {
            throw new IllegalStateException("Cannot delete a running test");
        }
        deleteDirectory(testDir);
        historyIndex.removeIf(m -> testId.equals(m.getTestId()));
        log.info("Deleted test record: {}", testId);
    }

    private void writeMeta(Path testDir, RunMeta meta) throws IOException {
        objectMapper.writeValue(testDir.resolve("meta.json").toFile(), meta);
    }

    private void addToHistory(RunMeta meta) {
        historyIndex.add(0, meta);  // newest first
    }

    private void updateHistoryEntry(RunMeta meta) {
        for (int i = 0; i < historyIndex.size(); i++) {
            if (meta.getTestId().equals(historyIndex.get(i).getTestId())) {
                historyIndex.set(i, meta);
                return;
            }
        }
    }

    private void pruneHistory() {
        if (historyIndex.size() <= runsMaxCount) return;
        // Remove oldest entries beyond limit
        while (historyIndex.size() > runsMaxCount) {
            RunMeta oldest = historyIndex.remove(historyIndex.size() - 1);
            // Only delete if not in activeRuns
            if (!activeRuns.containsKey(oldest.getTestId())) {
                try {
                    deleteDirectory(runsPath.resolve(oldest.getTestId()));
                } catch (IOException e) {
                    log.warn("Failed to prune old run: {}", oldest.getTestId(), e);
                }
            }
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walk(dir)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try { Files.delete(p); } catch (IOException e) { /* ignore */ }
            });
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down TestService, terminating {} active runs", activeRuns.size());
        for (TestRun run : activeRuns.values()) {
            if (run.isRunning()) {
                run.getMeta().setStatus("STOPPED");
                try {
                    writeMeta(runsPath.resolve(run.getTestId()), run.getMeta());
                } catch (IOException e) { /* ignore */ }
                run.getProcess().destroy();
                try {
                    if (!run.getProcess().waitFor(10, TimeUnit.SECONDS)) {
                        run.getProcess().destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        sseExecutor.shutdownNow();
    }
}
