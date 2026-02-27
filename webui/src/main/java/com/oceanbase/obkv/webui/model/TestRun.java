package com.oceanbase.obkv.webui.model;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory state for a currently active (or recently finished) test run.
 * Not persisted – reconstructed from meta.json on restart if needed.
 */
public class TestRun {

    private final String testId;
    private final RunMeta meta;
    private Process process;
    private Thread readerThread;
    private Thread tailThread;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private volatile long tailOffset = 0;
    private final long startNano;

    public TestRun(String testId, RunMeta meta) {
        this.testId = testId;
        this.meta = meta;
        this.startNano = System.nanoTime();
    }

    public String getTestId() { return testId; }
    public RunMeta getMeta() { return meta; }
    public Process getProcess() { return process; }
    public void setProcess(Process process) { this.process = process; }
    public Thread getReaderThread() { return readerThread; }
    public void setReaderThread(Thread readerThread) { this.readerThread = readerThread; }
    public Thread getTailThread() { return tailThread; }
    public void setTailThread(Thread tailThread) { this.tailThread = tailThread; }
    public List<SseEmitter> getEmitters() { return emitters; }
    public long getTailOffset() { return tailOffset; }
    public void setTailOffset(long tailOffset) { this.tailOffset = tailOffset; }
    public long getStartNano() { return startNano; }

    public void addEmitter(SseEmitter emitter) {
        emitters.add(emitter);
    }

    public void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }
}
