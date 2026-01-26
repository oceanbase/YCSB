package com.oceanbase.obkv.console.service;

import com.oceanbase.obkv.console.model.SqlConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Service for SQL database operations
 */
@Slf4j
@Service
public class SqlService {

    /**
     * Test database connection
     */
    public boolean testConnection(SqlConnection config) {
        try (Connection conn = getConnection(config)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("Connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get a database connection
     */
    public Connection getConnection(SqlConnection config) throws SQLException {
        String url = config.buildJdbcUrl();
        log.debug("Connecting to: {}", url);
        return DriverManager.getConnection(url, config.getUser(), config.getPassword());
    }

    /**
     * Execute a SQL statement
     */
    public void executeStatement(SqlConnection config, String sql) throws SQLException {
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            log.info("Executing SQL: {}", sql);
            stmt.execute(sql);
        }
    }

    /**
     * Execute a SQL statement and return execution time
     */
    public long executeStatementTimed(SqlConnection config, String sql) throws SQLException {
        long startTime = System.currentTimeMillis();
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            log.info("Executing SQL: {}", sql);
            stmt.execute(sql);
        }
        return System.currentTimeMillis() - startTime;
    }
}

