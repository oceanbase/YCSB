package com.oceanbase.obkv.webui.controller;

import com.oceanbase.obkv.webui.model.ModuleDescriptor;
import com.oceanbase.obkv.webui.service.ModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/modules")
public class ModuleController {

    @Autowired
    private ModuleService moduleService;

    @GetMapping
    public Collection<ModuleDescriptor> listModules() {
        return moduleService.getAllModules();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModuleDescriptor> getModule(@PathVariable String id) {
        ModuleDescriptor desc = moduleService.getModule(id);
        if (desc == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(desc);
    }
}
