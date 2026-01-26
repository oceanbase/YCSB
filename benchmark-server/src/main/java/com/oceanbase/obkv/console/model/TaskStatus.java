package com.oceanbase.obkv.console.model;

/**
 * Task status enumeration
 */
public enum TaskStatus {

    /**
     * Task is waiting to be executed
     */
    PENDING,

    /**
     * Task is currently running
     */
    RUNNING,

    /**
     * Task completed successfully
     */
    COMPLETED,

    /**
     * Task failed with error
     */
    FAILED,

    /**
     * Task was manually stopped
     */
    STOPPED
}

