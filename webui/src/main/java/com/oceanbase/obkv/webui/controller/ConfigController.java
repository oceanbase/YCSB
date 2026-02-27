package com.oceanbase.obkv.webui.controller;

import com.oceanbase.obkv.webui.model.SavedConfigMeta;
import com.oceanbase.obkv.webui.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/configs")
public class ConfigController {

    @Autowired
    private ConfigService configService;

    @GetMapping
    public List<SavedConfigMeta> listConfigs() {
        return configService.listConfigs();
    }

    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String name) {
        try {
            String content = configService.loadConfigContent(name);
            SavedConfigMeta meta = configService.getConfigMeta(name);
            Map<String, Object> resp = new HashMap<>();
            resp.put("name", name);
            resp.put("module", meta.getModule());
            resp.put("testType", meta.getTestType());
            resp.put("savedAt", meta.getSavedAt());
            resp.put("workloadContent", content);
            return ResponseEntity.ok(resp);
        } catch (FileNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{name}")
    public ResponseEntity<Map<String, Object>> saveConfig(
            @PathVariable String name,
            @RequestBody Map<String, String> body) {
        try {
            String module = body.getOrDefault("module", "");
            String testType = body.getOrDefault("testType", "");
            String workloadContent = body.getOrDefault("workloadContent", "");
            configService.saveConfig(name, module, testType, workloadContent);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "saved"); resp.put("name", name);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>(); err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (IOException e) {
            Map<String, Object> err = new HashMap<>(); err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable String name) {
        try {
            configService.deleteConfig(name);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "deleted"); resp.put("name", name);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>(); err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (IOException e) {
            Map<String, Object> err = new HashMap<>(); err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }
}
