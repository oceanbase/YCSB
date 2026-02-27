package com.oceanbase.obkv.webui.service;

import com.oceanbase.obkv.webui.model.SavedConfigMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages saved test configurations in webui-configs/.
 * Each config is a standard YCSB workload .properties file with __webui.* meta keys.
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z0-9_\\-]{1,100}$");
    private static final String META_PREFIX = "__webui.";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Value("${webui.configs.dir:webui-configs}")
    private String configsDir;

    @Autowired
    private ModuleService moduleService;

    private Path configsPath;

    @PostConstruct
    public void init() throws IOException {
        configsPath = Paths.get(configsDir).toAbsolutePath();
        Files.createDirectories(configsPath);
        log.info("Configs directory: {}", configsPath);
    }

    public void validateName(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Config name must match [a-zA-Z0-9_-] and be 1-100 chars");
        }
    }

    public List<SavedConfigMeta> listConfigs() {
        try {
            return Files.list(configsPath)
                .filter(p -> p.getFileName().toString().endsWith(".properties"))
                .map(p -> {
                    String name = p.getFileName().toString().replace(".properties", "");
                    try {
                        Properties props = loadProperties(p);
                        return new SavedConfigMeta(
                            name,
                            props.getProperty(META_PREFIX + "module", ""),
                            props.getProperty(META_PREFIX + "testType", ""),
                            props.getProperty(META_PREFIX + "savedAt", "")
                        );
                    } catch (IOException e) {
                        return new SavedConfigMeta(name, "", "", "");
                    }
                })
                .sorted(Comparator.comparing(SavedConfigMeta::getName))
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list configs", e);
            return Collections.emptyList();
        }
    }

    /**
     * Returns the raw workload content (without __webui.* meta lines).
     */
    public String loadConfigContent(String name) throws IOException {
        validateName(name);
        Path file = configsPath.resolve(name + ".properties");
        if (!Files.exists(file)) {
            throw new FileNotFoundException("Config not found: " + name);
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return lines.stream()
            .filter(l -> !l.startsWith(META_PREFIX))
            .collect(Collectors.joining("\n"));
    }

    /**
     * Returns structured metadata for a config (module, testType, savedAt).
     */
    public SavedConfigMeta getConfigMeta(String name) throws IOException {
        validateName(name);
        Path file = configsPath.resolve(name + ".properties");
        if (!Files.exists(file)) {
            throw new FileNotFoundException("Config not found: " + name);
        }
        Properties props = loadProperties(file);
        return new SavedConfigMeta(
            name,
            props.getProperty(META_PREFIX + "module", ""),
            props.getProperty(META_PREFIX + "testType", ""),
            props.getProperty(META_PREFIX + "savedAt", "")
        );
    }

    /**
     * Saves workload content with module/testType metadata.
     */
    public void saveConfig(String name, String module, String testType, String workloadContent) throws IOException {
        validateName(name);
        Path file = configsPath.resolve(name + ".properties");
        StringBuilder sb = new StringBuilder();
        sb.append(META_PREFIX).append("module=").append(module).append("\n");
        sb.append(META_PREFIX).append("testType=").append(testType).append("\n");
        sb.append(META_PREFIX).append("savedAt=").append(LocalDateTime.now().format(FMT)).append("\n");
        sb.append(workloadContent);
        if (!workloadContent.endsWith("\n")) {
            sb.append("\n");
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        log.info("Saved config: {}", name);
    }

    public void deleteConfig(String name) throws IOException {
        validateName(name);
        Path file = configsPath.resolve(name + ".properties");
        Files.deleteIfExists(file);
        log.info("Deleted config: {}", name);
    }

    public boolean exists(String name) {
        try {
            validateName(name);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return Files.exists(configsPath.resolve(name + ".properties"));
    }

    private Properties loadProperties(Path file) throws IOException {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(file)) {
            props.load(is);
        }
        return props;
    }
}
