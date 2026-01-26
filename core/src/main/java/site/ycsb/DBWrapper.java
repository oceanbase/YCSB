/**
 * Copyright (c) 2010 Yahoo! Inc., 2016-2020 YCSB contributors. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb;

import java.util.Map;

import site.ycsb.measurements.Measurements;
import org.apache.htrace.core.TraceScope;
import org.apache.htrace.core.Tracer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wrapper around a "real" DB that measures latencies and counts return codes.
 * Also reports latency separately between OK and failed operations.
 */
public class DBWrapper extends DB {
  private final DB db;
  private final Measurements measurements;
  private final Tracer tracer;

  private boolean reportLatencyForEachError = false;
  private Set<String> latencyTrackedErrors = new HashSet<String>();

  private static final String REPORT_LATENCY_FOR_EACH_ERROR_PROPERTY = "reportlatencyforeacherror";
  private static final String REPORT_LATENCY_FOR_EACH_ERROR_PROPERTY_DEFAULT = "false";

  private static final String LATENCY_TRACKED_ERRORS_PROPERTY = "latencytrackederrors";
  
  private int retryLimit = 0;
  private double retryInterval = 3.0;
  
  private static final String INSERTION_RETRY_LIMIT = "core_workload_insertion_retry_limit";
  private static final String INSERTION_RETRY_INTERVAL = "core_workload_insertion_retry_interval";

  private static final AtomicBoolean LOG_REPORT_CONFIG = new AtomicBoolean(false);

  private final String scopeStringCleanup;
  private final String scopeStringDelete;
  private final String scopeStringInit;
  private final String scopeStringInsert;
  private final String scopeStringRead;
  private final String scopeStringScan;
  private final String scopeStringUpdate;
  private final String scopeStringBatchPut;
  private final String scopeStringBatchRead;
  public DBWrapper(final DB db, final Tracer tracer) {
    this.db = db;
    measurements = Measurements.getMeasurements();
    this.tracer = tracer;
    final String simple = db.getClass().getSimpleName();
    scopeStringCleanup = simple + "#cleanup";
    scopeStringDelete = simple + "#delete";
    scopeStringInit = simple + "#init";
    scopeStringInsert = simple + "#insert";
    scopeStringRead = simple + "#read";
    scopeStringScan = simple + "#scan";
    scopeStringUpdate = simple + "#update";
    scopeStringBatchPut = simple + "#batchPut";
    scopeStringBatchRead = simple + "#batchRead";
  }

  /**
   * Set the properties for this DB.
   */
  public void setProperties(Properties p) {
    db.setProperties(p);
  }

  /**
   * Get the set of properties for this DB.
   */
  public Properties getProperties() {
    return db.getProperties();
  }

  /**
   * Initialize any state for this DB.
   * Called once per DB instance; there is one DB instance per client thread.
   */
  public void init() throws DBException {
    try (final TraceScope span = tracer.newScope(scopeStringInit)) {
      db.init();

      this.reportLatencyForEachError = Boolean.parseBoolean(getProperties().
          getProperty(REPORT_LATENCY_FOR_EACH_ERROR_PROPERTY,
              REPORT_LATENCY_FOR_EACH_ERROR_PROPERTY_DEFAULT));

      if (!reportLatencyForEachError) {
        String latencyTrackedErrorsProperty = getProperties().getProperty(LATENCY_TRACKED_ERRORS_PROPERTY, null);
        if (latencyTrackedErrorsProperty != null) {
          this.latencyTrackedErrors = new HashSet<String>(Arrays.asList(
              latencyTrackedErrorsProperty.split(",")));
        }
      }
      
      // Load retry configuration for Run phase
      this.retryLimit = Integer.parseInt(getProperties().getProperty(INSERTION_RETRY_LIMIT, "0"));
      this.retryInterval = Double.parseDouble(getProperties().getProperty(INSERTION_RETRY_INTERVAL, "3.0"));

      if (LOG_REPORT_CONFIG.compareAndSet(false, true)) {
        System.err.println("DBWrapper: report latency for each error is " +
            this.reportLatencyForEachError + " and specific error codes to track" +
            " for latency are: " + this.latencyTrackedErrors.toString());
        System.err.println("DBWrapper: Run phase retry limit=" + this.retryLimit + 
            ", retry interval=" + this.retryInterval + "s");
      }
    }
  }

  /**
   * Cleanup any state for this DB.
   * Called once per DB instance; there is one DB instance per client thread.
   */
  public void cleanup() throws DBException {
    try (final TraceScope span = tracer.newScope(scopeStringCleanup)) {
      long ist = measurements.getIntendedStartTimeNs();
      long st = System.nanoTime();
      db.cleanup();
      long en = System.nanoTime();
      measure("CLEANUP", Status.OK, ist, st, en);
    }
  }

  /**
   * Read a record from the database. Each field/value pair from the result
   * will be stored in a HashMap.
   *
   * @param table The name of the table
   * @param key The record key of the record to read.
   * @param fields The list of fields to read, or null for all of them
   * @param result A HashMap of field/value pairs for the result
   * @return The result of the operation.
   */
  public Status read(String table, String key, Set<String> fields,
                     Map<String, ByteIterator> result) {
    try (final TraceScope span = tracer.newScope(scopeStringRead)) {
      long ist = measurements.getIntendedStartTimeNs();
      if (retryLimit > 0) {
        return retryOperation("READ", () -> db.read(table, key, fields, result), ist);
      } else {
        long st = System.nanoTime();
        Status res = db.read(table, key, fields, result);
        long en = System.nanoTime();
        measure("READ", res, ist, st, en);
        measurements.reportStatus("READ", res);
        return res;
      }
    }
  }

  /**
   * Perform a range scan for a set of records in the database.
   * Each field/value pair from the result will be stored in a HashMap.
   *
   * @param table The name of the table
   * @param startkey The record key of the first record to read.
   * @param recordcount The number of records to read
   * @param fields The list of fields to read, or null for all of them
   * @param result A Vector of HashMaps, where each HashMap is a set field/value pairs for one record
   * @return The result of the operation.
   */
  public Status scan(String table, String startkey, int recordcount,
                     Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
    try (final TraceScope span = tracer.newScope(scopeStringScan)) {
      long ist = measurements.getIntendedStartTimeNs();
      if (retryLimit > 0) {
        return retryOperation("SCAN", () -> db.scan(table, startkey, recordcount, fields, result), ist);
      } else {
        long st = System.nanoTime();
        Status res = db.scan(table, startkey, recordcount, fields, result);
        long en = System.nanoTime();
        measure("SCAN", res, ist, st, en);
        measurements.reportStatus("SCAN", res);
        return res;
      }
    }
  }

  private void measure(String op, Status result, long intendedStartTimeNanos,
                       long startTimeNanos, long endTimeNanos) {
    String measurementName = op;
    if (result == null || !result.isOk()) {
      if (this.reportLatencyForEachError ||
          this.latencyTrackedErrors.contains(result.getName())) {
        measurementName = op + "-" + result.getName();
      } else {
        measurementName = op + "-FAILED";
      }
    }
    measurements.measure(measurementName,
        (int) ((endTimeNanos - startTimeNanos) / 1000));
    measurements.measureIntended(measurementName,
        (int) ((endTimeNanos - intendedStartTimeNanos) / 1000));
  }
  
  /**
   * Retry helper for Run phase operations. Sleeps are excluded from RT measurement.
   */
  private Status retryOperation(String operationName, java.util.function.Supplier<Status> operation,
                                long intendedStartTime) {
    int numOfRetries = 0;
    long actualStartTime = System.nanoTime();
    long currentIntendedStartTime = intendedStartTime; // Track current intended start time
    
    while (true) {
      Status status = operation.get();
      long actualEndTime = System.nanoTime();
      
      if (status != null && status.isOk()) {
        // Only measure successful operation
        measure(operationName, status, currentIntendedStartTime, actualStartTime, actualEndTime);
        measurements.reportStatus(operationName, status);
        return status;
      }
      
      // Failed, check if we should retry
      if (++numOfRetries <= retryLimit) {
        System.out.println("Retrying " + operationName + " (" + numOfRetries + " of " + retryLimit + ")");
        try {
          int sleepTime = (int) (1000 * retryInterval * (0.8 + 0.4 * Math.random()));
          Thread.sleep(sleepTime);
          // Record retry sleep time to exclude from runtime
          measurements.addRetrySleepTime(sleepTime);
          // Reset start time after sleep so retry time is excluded from BOTH measurements
          actualStartTime = System.nanoTime();
          currentIntendedStartTime = actualStartTime; // Also reset intended time
          measurements.setIntendedStartTimeNs(actualStartTime);
        } catch (InterruptedException e) {
          // Report the failed status and return
          measure(operationName, status, currentIntendedStartTime, actualStartTime, actualEndTime);
          measurements.reportStatus(operationName, status);
          return status;
        }
      } else {
        // No more retries, report the last failed status
        measure(operationName, status, currentIntendedStartTime, actualStartTime, actualEndTime);
        measurements.reportStatus(operationName, status);
        return status;
      }
    }
  }

  /**
   * Update a record in the database. Any field/value pairs in the specified values HashMap will be written into the
   * record with the specified record key, overwriting any existing values with the same field name.
   *
   * @param table The name of the table
   * @param key The record key of the record to write.
   * @param values A HashMap of field/value pairs to update in the record
   * @return The result of the operation.
   */
  public Status update(String table, String key,
                       Map<String, ByteIterator> values) {
    try (final TraceScope span = tracer.newScope(scopeStringUpdate)) {
      long ist = measurements.getIntendedStartTimeNs();
      if (retryLimit > 0) {
        return retryOperation("UPDATE", () -> db.update(table, key, values), ist);
      } else {
        long st = System.nanoTime();
        Status res = db.update(table, key, values);
        long en = System.nanoTime();
        measure("UPDATE", res, ist, st, en);
        measurements.reportStatus("UPDATE", res);
        return res;
      }
    }
  }

  /**
   * Insert a record in the database. Any field/value pairs in the specified
   * values HashMap will be written into the record with the specified
   * record key.
   *
   * @param table The name of the table
   * @param key The record key of the record to insert.
   * @param values A HashMap of field/value pairs to insert in the record
   * @return The result of the operation.
   */
  public Status insert(String table, String key,
                       Map<String, ByteIterator> values) {
    try (final TraceScope span = tracer.newScope(scopeStringInsert)) {
      long ist = measurements.getIntendedStartTimeNs();
      if (retryLimit > 0) {
        return retryOperation("INSERT", () -> db.insert(table, key, values), ist);
      } else {
        long st = System.nanoTime();
        Status res = db.insert(table, key, values);
        long en = System.nanoTime();
        measure("INSERT", res, ist, st, en);
        measurements.reportStatus("INSERT", res);
        return res;
      }
    }
  }

  /**
   * Delete a record from the database.
   *
   * @param table The name of the table
   * @param key The record key of the record to delete.
   * @return The result of the operation.
   */
  public Status delete(String table, String key) {
    try (final TraceScope span = tracer.newScope(scopeStringDelete)) {
      long ist = measurements.getIntendedStartTimeNs();
      long st = System.nanoTime();
      Status res = db.delete(table, key);
      long en = System.nanoTime();
      measure("DELETE", res, ist, st, en);
      measurements.reportStatus("DELETE", res);
      return res;
    }
  }

    /**
   * batch operation exclude read.
   *
   * @param table The name of the table
   * @param valuesMap a batch operation for multiple rows.
   * @return The result of the operation.
   */
    public Status batchPut(String table, Map<String, Map<String, ByteIterator>> valuesMap) {
      try (final TraceScope span = tracer.newScope(scopeStringBatchPut)) {
        long ist = measurements.getIntendedStartTimeNs();
        if (retryLimit > 0) {
          return retryOperation("BATCH_PUT", () -> db.batchPut(table, valuesMap), ist);
        } else {
          long st = System.nanoTime();
          Status res = db.batchPut(table, valuesMap);
          long en = System.nanoTime();
          measure("BATCH_PUT", res, ist, st, en);
          measurements.reportStatus("BATCH_PUT", res);
          return res;
        }
      }
    }
  
    /**
     * batch read operation.
     *
     * @param table The name of the table
     * @param fields The list of fields to read, or null for all of them
     * @param valuesMap a batch operation for multiple rows.
     * @return The result of the operation.
     */
    public Status batchRead(String table, Set<String> fields, Map<String, Map<String, ByteIterator>> valuesMap) {
      try (final TraceScope span = tracer.newScope(scopeStringBatchRead)) {
        long ist = measurements.getIntendedStartTimeNs();
        if (retryLimit > 0) {
          return retryOperation("BATCH_READ", () -> db.batchRead(table, fields, valuesMap), ist);
        } else {
          long st = System.nanoTime();
          Status res = db.batchRead(table, fields, valuesMap);
          long en = System.nanoTime();
          measure("BATCH_READ", res, ist, st, en);
          measurements.reportStatus("BATCH_READ", res);
          return res;
        }
      }
    }
}
