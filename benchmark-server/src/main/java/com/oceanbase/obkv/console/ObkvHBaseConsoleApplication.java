package com.oceanbase.obkv.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * OBKV-HBase Benchmark Console Application
 * 
 * Main entry point for the benchmark control console backend service.
 */
@SpringBootApplication
@EnableAsync
public class ObkvHBaseConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObkvHBaseConsoleApplication.class, args);
    }
}

