package com.oceanbase.obkv.webui.controller;

import com.oceanbase.obkv.webui.model.RunMeta;
import com.oceanbase.obkv.webui.model.TestConfig;
import com.oceanbase.obkv.webui.model.TestResult;
import com.oceanbase.obkv.webui.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tests")
public class TestController {

    @Autowired
    private TestService testService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> startTest(@RequestBody TestConfig config) {
        try {
            String testId = testService.startTest(config);
            Map<String, Object> resp = new HashMap<>(); resp.put("testId", testId);
            return ResponseEntity.ok(resp);
        } catch (IllegalStateException e) {
            Map<String, Object> err = new HashMap<>(); err.put("error", e.getMessage());
            return ResponseEntity.status(429).body(err);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>(); err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (IOException e) {
            Map<String, Object> err = new HashMap<>(); err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @GetMapping
    public ResponseEntity<List<RunMeta>> listTests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(testService.listHistory(page, size));
    }

    @GetMapping("/{id}/stream")
    public SseEmitter streamLog(
            @PathVariable String id,
            @RequestHeader(value = "Last-Event-ID", defaultValue = "0") long lastEventId) throws IOException {
        return testService.streamLog(id, lastEventId);
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<TestResult> getResults(@PathVariable String id) {
        try {
            TestResult result = testService.getResult(id);
            if (result == null) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(result);
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/log")
    public ResponseEntity<String> getLog(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") long offset,
            @RequestParam(defaultValue = "2000") int limit) {
        try {
            String log = testService.getLogChunk(id, offset, limit);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(log);
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/workload")
    public ResponseEntity<String> getWorkload(@PathVariable String id) {
        try {
            String content = testService.getWorkload(id);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(content);
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/meta")
    public ResponseEntity<RunMeta> getMeta(@PathVariable String id) {
        try {
            return ResponseEntity.ok(testService.getRunMeta(id));
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> stopTest(@PathVariable String id) {
        try {
            testService.stopTest(id);
            Map<String, Object> resp = new HashMap<>(); resp.put("status", "stopping"); resp.put("testId", id);
            return ResponseEntity.ok(resp);
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            Map<String, Object> err = new HashMap<>(); err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @DeleteMapping("/{id}/record")
    public ResponseEntity<Map<String, Object>> deleteRecord(@PathVariable String id) {
        try {
            testService.deleteRecord(id);
            Map<String, Object> resp = new HashMap<>(); resp.put("status", "deleted"); resp.put("testId", id);
            return ResponseEntity.ok(resp);
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            Map<String, Object> err = new HashMap<>(); err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (IOException e) {
            Map<String, Object> err = new HashMap<>(); err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }
}
