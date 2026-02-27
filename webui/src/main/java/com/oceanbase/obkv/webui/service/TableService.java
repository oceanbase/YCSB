package com.oceanbase.obkv.webui.service;

import com.oceanbase.obkv.webui.model.DbConnConfig;
import com.oceanbase.obkv.webui.model.SqlResult;
import com.oceanbase.obkv.webui.model.TableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles table creation:
 * 1. Calls create_table.sh to generate SQL
 * 2. Executes the generated SQL via JDBC
 */
@Service
public class TableService {

    private static final Logger log = LoggerFactory.getLogger(TableService.class);

    @Value("${webui.hbase.workdir:obkv-hbase}")
    private String hbaseWorkdir;

    @Value("${webui.table.workdir:obkv-table}")
    private String tableWorkdir;

    /**
     * Calls create_table.sh for the given module and returns the generated SQL.
     */
    public SqlResult generateSql(TableConfig config) {
        String module = config.getModule();
        Map<String, String> params = config.getParams();

        try {
            if ("obkv-hbase".equals(module)) {
                return generateHbaseSql(params);
            } else if ("obkv-table".equals(module)) {
                return generateTableSql(params);
            } else {
                return new SqlResult(false, null, "Unknown module: " + module);
            }
        } catch (Exception e) {
            log.error("SQL generation failed", e);
            return new SqlResult(false, null, e.getMessage());
        }
    }

    private SqlResult generateHbaseSql(Map<String, String> params) throws IOException, InterruptedException {
        Path workdir = Paths.get(hbaseWorkdir).toAbsolutePath();
        List<String> cmd = new ArrayList<>();
        cmd.add("bash");
        cmd.add("create_table.sh");

        // Map UI params to script flags
        // Frontend: "type"=hbase|ts (table model) → script --mode
        //           "mode"=first_part|sec_part (partition level) → script --type
        addParam(cmd, params, "--mode",                    "type");
        addParam(cmd, params, "--type",                    "mode");
        addParam(cmd, params, "--partition_type",          "partition_type");
        addParam(cmd, params, "--partition_count",         "partition_count");
        addParam(cmd, params, "--max_key",                 "max_key");
        addParam(cmd, params, "--key_length",              "key_length");
        addParam(cmd, params, "--range_partition_count",   "range_partition_count");
        addParam(cmd, params, "--range_start_timestamp",   "range_start_timestamp");
        addParam(cmd, params, "--range_partition_duration_ms", "range_partition_duration_ms");
        addParam(cmd, params, "--key_subpartition_count",  "key_subpartition_count");
        addParam(cmd, params, "--table_name",              "table_name");
        addParam(cmd, params, "--family",                  "family");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workdir.toFile());
        // Do NOT merge stderr - the script sends "SQL also saved to: ..." to stderr,
        // mixing it into stdout would corrupt the SQL string fed to JDBC.

        Process process = pb.start();

        // Drain stderr in a background thread to prevent pipe deadlock
        final StringBuilder stderrBuf = new StringBuilder();
        Thread stderrDrainer = new Thread(() -> {
            try {
                stderrBuf.append(readAll(process.getErrorStream()));
            } catch (IOException ignored) {}
        }, "stderr-drainer");
        stderrDrainer.setDaemon(true);
        stderrDrainer.start();

        String stdoutOutput = readAll(process.getInputStream());
        int exitCode = process.waitFor();
        stderrDrainer.join(3000);

        if (exitCode != 0) {
            String errMsg = stderrBuf.toString().trim();
            if (errMsg.isEmpty()) errMsg = stdoutOutput.trim();
            return new SqlResult(false, null, "create_table.sh failed (exit " + exitCode + "):\n" + errMsg);
        }

        // Prefer reading from the generated .sql file (avoids any stderr contamination).
        // Fall back to stdout if the file is not found.
        String sql = readNewestSqlFile(workdir);
        if (sql == null || sql.trim().isEmpty()) {
            sql = stdoutOutput.trim();
        }
        if (sql == null || sql.trim().isEmpty()) {
            return new SqlResult(false, null, "create_table.sh succeeded but no SQL output found");
        }

        return new SqlResult(true, sql.trim(), "SQL generated successfully");
    }

    private SqlResult generateTableSql(Map<String, String> params) throws IOException, InterruptedException {
        Path workdir = Paths.get(tableWorkdir).toAbsolutePath();
        List<String> cmd = new ArrayList<>();
        cmd.add("bash");
        cmd.add("create_table.sh");

        // obkv-table create_table.sh uses positional args: --mode <mode> --fields <n> ...
        addParam(cmd, params, "--mode",   "mode");
        addParam(cmd, params, "--fields", "fields");

        String mode = params.getOrDefault("mode", "range");
        if ("range".equals(mode)) {
            addPositional(cmd, params, "num_partitions");
            addPositional(cmd, params, "max_key");
            addPositional(cmd, params, "key_length");
        } else {
            addPositional(cmd, params, "range_partition_count");
            addPositional(cmd, params, "key_subpartition_count");
            addPositional(cmd, params, "start_timestamp");
            addPositional(cmd, params, "partition_duration_ms");
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workdir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String scriptOutput = readAll(process.getInputStream());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            return new SqlResult(false, null, "create_table.sh failed (exit " + exitCode + "):\n" + scriptOutput);
        }

        // obkv-table only writes to file, read newest .sql
        String sql = readNewestSqlFile(workdir);
        if (sql == null || sql.isEmpty()) {
            return new SqlResult(false, null, "create_table.sh succeeded but no SQL file found");
        }

        return new SqlResult(true, sql, "SQL generated successfully");
    }

    private void addParam(List<String> cmd, Map<String, String> params, String flag, String key) {
        String val = params.get(key);
        if (val != null && !val.isEmpty()) {
            cmd.add(flag);
            cmd.add(val);
        }
    }

    private void addPositional(List<String> cmd, Map<String, String> params, String key) {
        String val = params.get(key);
        if (val != null && !val.isEmpty()) {
            cmd.add(val);
        }
    }

    private String readAll(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String readNewestSqlFile(Path dir) throws IOException {
        Optional<Path> newest = Files.list(dir)
            .filter(p -> p.getFileName().toString().endsWith(".sql"))
            .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
        if (!newest.isPresent()) {
            return null;
        }
        return new String(Files.readAllBytes(newest.get()));
    }

    /**
     * Executes the given SQL against OceanBase via JDBC.
     * If dropIfExists is true, prepends DROP TABLE IF EXISTS for each table created by the SQL.
     */
    public SqlResult executeSql(DbConnConfig conn) {
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            conn.getHost(), conn.getPort(), conn.getDatabase());
        try (Connection c = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
            String sql = conn.getSql();
            if (conn.getDropIfExists()) {
                sql = prependDropIfExists(sql);
            }
            // Execute multiple statements separated by semicolons
            String[] statements = sql.split(";");
            try (Statement stmt = c.createStatement()) {
                for (String s : statements) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }
            return new SqlResult(true, sql, "Table created successfully");
        } catch (SQLException e) {
            log.error("SQL execution failed", e);
            return new SqlResult(false, conn.getSql(), "SQL execution failed: " + e.getMessage());
        }
    }

    /** Extracts table names from CREATE TABLE statements and prepends DROP TABLE IF EXISTS. */
    private String prependDropIfExists(String sql) {
        // Match CREATE TABLE [IF NOT EXISTS] `name` or CREATE TABLE [IF NOT EXISTS] name
        Pattern p = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:`([^`]+)`|(\\S+))", Pattern.CASE_INSENSITIVE);
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = p.matcher(sql);
        while (m.find()) {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            if (name != null && !name.isEmpty()) tables.add(name);
        }
        if (tables.isEmpty()) return sql;
        StringBuilder sb = new StringBuilder();
        for (String t : tables) {
            sb.append("DROP TABLE IF EXISTS `").append(t).append("`;\n");
        }
        sb.append(sql);
        return sb.toString();
    }

    /**
     * Tests JDBC connectivity (simple ping).
     */
    public SqlResult testConnection(DbConnConfig conn) {
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000",
            conn.getHost(), conn.getPort(), conn.getDatabase());
        try (Connection c = DriverManager.getConnection(url, conn.getUsername(), conn.getPassword())) {
            boolean valid = c.isValid(3);
            return new SqlResult(valid, null, valid ? "Connection successful" : "Connection test failed");
        } catch (SQLException e) {
            return new SqlResult(false, null, "Connection failed: " + e.getMessage());
        }
    }
}
