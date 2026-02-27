package com.oceanbase.obkv.webui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oceanbase.obkv.webui.model.ModuleDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Loads module descriptor JSON files from classpath:modules/*.json at startup.
 * All module-specific knowledge (parameters, test types, connection modes) lives in
 * these descriptors, keeping Java/JS code free of per-module hardcoding.
 */
@Service
public class ModuleService {

    private static final Logger log = LoggerFactory.getLogger(ModuleService.class);

    private final Map<String, ModuleDescriptor> descriptors = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void loadDescriptors() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath:modules/*.json");
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    ModuleDescriptor desc = objectMapper.readValue(is, ModuleDescriptor.class);
                    descriptors.put(desc.getModuleId(), desc);
                    log.info("Loaded module descriptor: {}", desc.getModuleId());
                } catch (IOException e) {
                    log.error("Failed to load module descriptor: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan module descriptors", e);
        }
        if (descriptors.isEmpty()) {
            log.warn("No module descriptors found in classpath:modules/");
        }
    }

    public Collection<ModuleDescriptor> getAllModules() {
        return descriptors.values();
    }

    public ModuleDescriptor getModule(String moduleId) {
        return descriptors.get(moduleId);
    }

    public boolean hasModule(String moduleId) {
        return descriptors.containsKey(moduleId);
    }

    /**
     * Returns the knownParams set for a module, used as whitelist by ConfigService.
     */
    public Set<String> getKnownParams(String moduleId) {
        ModuleDescriptor desc = descriptors.get(moduleId);
        if (desc == null || desc.getKnownParams() == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(desc.getKnownParams());
    }
}
