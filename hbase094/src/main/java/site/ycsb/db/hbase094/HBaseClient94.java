/**
 * Copyright (c) 2010-2016 Yahoo! Inc., 2017 YCSB contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb.db.hbase094;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HConnection;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.PageFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.security.UserGroupInformation;
import site.ycsb.ByteArrayByteIterator;
import site.ycsb.ByteIterator;
import site.ycsb.DB;
import site.ycsb.DBException;
import site.ycsb.Status;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ConcurrentModificationException;

import static site.ycsb.workloads.CoreWorkload.TABLENAME_PROPERTY;
import static site.ycsb.workloads.CoreWorkload.TABLENAME_PROPERTY_DEFAULT;

/**
 * HBase 0.94 client for YCSB framework.
 *
 * Uses HConnection / HTable API compatible with Apache HBase 0.94.x clusters.
 */
public class HBaseClient94 extends DB {
  public static final String ZOOKEEPER_QUORUM = "hbase.zookeeper.quorum";
  public static final String ZOOKEEPER_CLIENT_PORT = "hbase.zookeeper.property.clientPort";
  public static final String HBASE_MASTER = "hbase.master";
  public static final String COLUMN_FAMILY_PROPERTY = "columnfamily";
  /** Number of cell versions per qualifier on insert/update; 1 = default single-version put. */
  public static final String VERSIONS_PER_QUALIFIER = "hbase.versionsPerQualifier";
  /** Milliseconds between consecutive versions; t_i = T0 - i * versionDeltaMs. */
  public static final String VERSION_DELTA_MS = "hbase.versionDeltaMs";
  /**
   * Anchor timestamp (ms) for the latest version (i=0). Typically load/benchmark end time;
   * with versionSpreadInWindow, each row's T0 is shifted backward within versionWindowMs.
   */
  public static final String VERSION_ANCHOR_TS = "hbase.versionAnchorTs";
  /** Spread each row's latest-version timestamp across versionWindowMs (default 180 days). */
  public static final String VERSION_SPREAD_IN_WINDOW = "hbase.versionSpreadInWindow";
  /** Time window size (ms) used when versionSpreadInWindow is true. Default: 180 days. */
  public static final String VERSION_WINDOW_MS = "hbase.versionWindowMs";
  /** Enable Get TimeRange filter on read (benchmark pure-read mode). */
  public static final String READ_TIME_RANGE_ENABLED = "hbase.readTimeRangeEnabled";
  /** Read window max timestamp (ms); defaults to hbase.versionAnchorTs when unset. */
  public static final String READ_ANCHOR_TS = "hbase.readAnchorTs";
  /** Read TimeRange width (ms); default 180 days. minTs = readAnchorTs - readWindowMs. */
  public static final String READ_WINDOW_MS = "hbase.readWindowMs";
  /** Max versions on Get; set via workload (benchmark read: 2000). */
  public static final String READ_MAX_VERSIONS = "hbase.readMaxVersions";
  /**
   * When true, read() counts KeyValue versions per qualifier and checks against expected count.
   * Default false; enable only for smoke / validation runs.
   */
  public static final String VERIFY_READ_VERSIONS_ENABLED = "hbase.verifyReadVersionsEnabled";
  /** Expected versions per qualifier when verify is enabled (e.g. 50 for V=50 load). */
  public static final String VERIFY_READ_VERSIONS_EXPECTED = "hbase.verifyReadVersionsExpected";
  /** Expected distinct qualifiers per row when Get uses whole column family (default 10). */
  public static final String VERIFY_READ_VERSIONS_FIELD_COUNT = "hbase.verifyReadVersionsFieldCount";
  /** HBase 0.94 client RPC timeout (ms); default server-side is 60000. */
  public static final String HBASE_RPC_TIMEOUT = "hbase.rpc.timeout";
  /** Socket read timeout (ms); 0.94 client; should be >= hbase.rpc.timeout. */
  public static final String IPC_SOCKET_TIMEOUT = "ipc.socket.timeout";

  private static final long DEFAULT_VERSION_WINDOW_MS = 15552000000L;
  /** 0 = unset; do not call Get.setMaxVersions unless workload specifies hbase.readMaxVersions. */
  private static final int READ_MAX_VERSIONS_UNSET = 0;

  private static final Configuration CONFIG = HBaseConfiguration.create();
  private static final AtomicInteger THREAD_COUNT = new AtomicInteger(0);
  private static final Object CONNECTION_LOCK = new Object();
  private static final Object TABLE_LOCK = new Object();
  private static HConnection hConn = null;

  private boolean debug = false;
  private String tableName = "";
  private HTableInterface hTable = null;
  private String columnFamily = "";
  private byte[] columnFamilyBytes;
  private boolean clientSideBuffering = false;
  private long writeBufferSize = 1024 * 1024 * 12;
  private boolean usePageFilter = true;
  private int versionsPerQualifier = 1;
  private long versionDeltaMs = 0;
  private long versionAnchorTs = 0;
  private boolean versionSpreadInWindow = true;
  private long versionWindowMs = DEFAULT_VERSION_WINDOW_MS;
  private boolean readTimeRangeEnabled = false;
  private long readAnchorTs = 0;
  private long readWindowMs = DEFAULT_VERSION_WINDOW_MS;
  private int readMaxVersions = READ_MAX_VERSIONS_UNSET;
  private boolean verifyReadVersionsEnabled = false;
  private int verifyReadVersionsExpected = 0;
  private int verifyReadVersionsFieldCount = 10;

  private static final AtomicInteger VERIFY_READ_PASS = new AtomicInteger(0);
  private static final AtomicInteger VERIFY_READ_FAIL = new AtomicInteger(0);
  private static final AtomicInteger VERIFY_READ_SUMMARY_PRINTED = new AtomicInteger(0);

  @Override
  public void init() throws DBException {
    Properties props = getProperties();
    debug = Boolean.parseBoolean(props.getProperty("debug", "false"));

    if (props.containsKey("clientbuffering")) {
      clientSideBuffering = Boolean.parseBoolean(props.getProperty("clientbuffering"));
    }
    if (props.containsKey("writebuffersize")) {
      writeBufferSize = Long.parseLong(props.getProperty("writebuffersize"));
    }
    if ("false".equals(props.getProperty("hbase.usepagefilter", "true"))) {
      usePageFilter = false;
    }

    applyConfiguration(props);

    if ("kerberos".equalsIgnoreCase(CONFIG.get("hbase.security.authentication"))) {
      CONFIG.set("hadoop.security.authentication", "Kerberos");
      UserGroupInformation.setConfiguration(CONFIG);
    }
    if (props.getProperty("principal") != null && props.getProperty("keytab") != null) {
      try {
        UserGroupInformation.loginUserFromKeytab(
            props.getProperty("principal"), props.getProperty("keytab"));
      } catch (IOException e) {
        throw new DBException("Keytab file is not readable or not found", e);
      }
    }

    columnFamily = props.getProperty(COLUMN_FAMILY_PROPERTY);
    if (columnFamily == null || columnFamily.isEmpty()) {
      throw new DBException("No columnfamily specified");
    }
    columnFamilyBytes = Bytes.toBytes(columnFamily);

    initVersionLoadConfig(props);
    initReadConfig(props);

    try {
      THREAD_COUNT.incrementAndGet();
      synchronized (CONNECTION_LOCK) {
        if (hConn == null) {
          hConn = HConnectionManager.createConnection(CONFIG);
        }
      }
    } catch (IOException e) {
      throw new DBException("Connection to HBase was not successful", e);
    }

    String table = props.getProperty(TABLENAME_PROPERTY, TABLENAME_PROPERTY_DEFAULT);
    boolean skipTableCheck = Boolean.parseBoolean(props.getProperty("hbase.skipTableCheck", "false"));
    if (!skipTableCheck) {
      try {
        HTableInterface ht = hConn.getTable(table);
        ht.getTableDescriptor();
        ht.close();
      } catch (IOException e) {
        throw new DBException("Table check failed: " + table, e);
      }
    }

    if (debug) {
      System.out.println("HBase 0.94 client initialized");
      System.out.println("  table=" + table + ", columnfamily=" + columnFamily);
      System.out.println("  " + ZOOKEEPER_QUORUM + "=" + CONFIG.get(ZOOKEEPER_QUORUM));
      System.out.println("  " + ZOOKEEPER_CLIENT_PORT + "=" + CONFIG.get(ZOOKEEPER_CLIENT_PORT));
      if (versionsPerQualifier > 1) {
        System.out.println("  multi-version load: versionsPerQualifier=" + versionsPerQualifier
            + ", versionDeltaMs=" + versionDeltaMs
            + ", versionAnchorTs=" + versionAnchorTs
            + ", versionSpreadInWindow=" + versionSpreadInWindow
            + ", versionWindowMs=" + versionWindowMs);
      }
      if (readTimeRangeEnabled) {
        System.out.println("  read TimeRange: minTs=" + (readAnchorTs - readWindowMs)
            + ", maxTs=" + readAnchorTs);
      }
      if (readMaxVersions > 0) {
        System.out.println("  read maxVersions=" + readMaxVersions);
      }
      if (verifyReadVersionsEnabled) {
        System.out.println("  read version verify: expectedPerQualifier="
            + verifyReadVersionsExpected + ", fieldCount=" + verifyReadVersionsFieldCount);
      }
    }
  }

  private void initReadConfig(Properties props) throws DBException {
    readTimeRangeEnabled = Boolean.parseBoolean(
        props.getProperty(READ_TIME_RANGE_ENABLED, "false"));

    String maxVersionsStr = props.getProperty(READ_MAX_VERSIONS);
    if (maxVersionsStr != null && !maxVersionsStr.trim().isEmpty()) {
      try {
        readMaxVersions = Integer.parseInt(maxVersionsStr.trim());
      } catch (NumberFormatException e) {
        throw new DBException("Invalid " + READ_MAX_VERSIONS + ": " + maxVersionsStr, e);
      }
      if (readMaxVersions <= 0) {
        throw new DBException(READ_MAX_VERSIONS + " must be greater than 0, got: "
            + readMaxVersions);
      }
    } else if (readTimeRangeEnabled) {
      throw new DBException(READ_MAX_VERSIONS + " is required when "
          + READ_TIME_RANGE_ENABLED + "=true (benchmark read workload uses 2000)");
    }

    if (!readTimeRangeEnabled) {
      return;
    }

    String anchorStr = props.getProperty(READ_ANCHOR_TS);
    if (anchorStr == null || anchorStr.trim().isEmpty()) {
      anchorStr = props.getProperty(VERSION_ANCHOR_TS);
    }
    if (anchorStr == null || anchorStr.trim().isEmpty()) {
      throw new DBException(READ_ANCHOR_TS + " (or " + VERSION_ANCHOR_TS
          + ") is required when " + READ_TIME_RANGE_ENABLED + "=true");
    }
    try {
      readAnchorTs = Long.parseLong(anchorStr.trim());
    } catch (NumberFormatException e) {
      throw new DBException("Invalid read anchor timestamp: " + anchorStr, e);
    }
    if (readAnchorTs <= 0) {
      throw new DBException(READ_ANCHOR_TS + " must be greater than 0 (epoch ms), got: "
          + readAnchorTs);
    }

    String windowStr = props.getProperty(READ_WINDOW_MS);
    if (windowStr != null && !windowStr.trim().isEmpty()) {
      try {
        readWindowMs = Long.parseLong(windowStr.trim());
      } catch (NumberFormatException e) {
        throw new DBException("Invalid " + READ_WINDOW_MS + ": " + windowStr, e);
      }
      if (readWindowMs <= 0) {
        throw new DBException(READ_WINDOW_MS + " must be greater than 0, got: " + readWindowMs);
      }
    } else {
      readWindowMs = DEFAULT_VERSION_WINDOW_MS;
    }

    initReadVersionVerifyConfig(props);
  }

  private void initReadVersionVerifyConfig(Properties props) throws DBException {
    verifyReadVersionsEnabled = Boolean.parseBoolean(
        props.getProperty(VERIFY_READ_VERSIONS_ENABLED, "false"));
    if (!verifyReadVersionsEnabled) {
      return;
    }

    String expectedStr = props.getProperty(VERIFY_READ_VERSIONS_EXPECTED);
    if (expectedStr != null && !expectedStr.trim().isEmpty()) {
      try {
        verifyReadVersionsExpected = Integer.parseInt(expectedStr.trim());
      } catch (NumberFormatException e) {
        throw new DBException("Invalid " + VERIFY_READ_VERSIONS_EXPECTED + ": " + expectedStr, e);
      }
    } else if (versionsPerQualifier > 1) {
      verifyReadVersionsExpected = versionsPerQualifier;
    } else {
      throw new DBException(VERIFY_READ_VERSIONS_EXPECTED + " (or " + VERSIONS_PER_QUALIFIER
          + " > 1) is required when " + VERIFY_READ_VERSIONS_ENABLED + "=true");
    }
    if (verifyReadVersionsExpected <= 0) {
      throw new DBException(VERIFY_READ_VERSIONS_EXPECTED + " must be > 0, got: "
          + verifyReadVersionsExpected);
    }

    String fieldCountStr = props.getProperty(VERIFY_READ_VERSIONS_FIELD_COUNT, "10");
    try {
      verifyReadVersionsFieldCount = Integer.parseInt(fieldCountStr.trim());
    } catch (NumberFormatException e) {
      throw new DBException("Invalid " + VERIFY_READ_VERSIONS_FIELD_COUNT + ": "
          + fieldCountStr, e);
    }
    if (verifyReadVersionsFieldCount <= 0) {
      throw new DBException(VERIFY_READ_VERSIONS_FIELD_COUNT + " must be > 0, got: "
          + verifyReadVersionsFieldCount);
    }
  }

  private void initVersionLoadConfig(Properties props) throws DBException {
    String versionsStr = props.getProperty(VERSIONS_PER_QUALIFIER, "1");
    try {
      versionsPerQualifier = Integer.parseInt(versionsStr.trim());
    } catch (NumberFormatException e) {
      throw new DBException("Invalid " + VERSIONS_PER_QUALIFIER + ": " + versionsStr, e);
    }
    if (versionsPerQualifier <= 0) {
      throw new DBException(VERSIONS_PER_QUALIFIER + " must be greater than 0, got: "
          + versionsPerQualifier);
    }
    if (versionsPerQualifier == 1) {
      return;
    }

    String deltaStr = props.getProperty(VERSION_DELTA_MS);
    if (deltaStr == null || deltaStr.trim().isEmpty()) {
      throw new DBException(VERSION_DELTA_MS + " is required when "
          + VERSIONS_PER_QUALIFIER + " > 1");
    }
    try {
      versionDeltaMs = Long.parseLong(deltaStr.trim());
    } catch (NumberFormatException e) {
      throw new DBException("Invalid " + VERSION_DELTA_MS + ": " + deltaStr, e);
    }
    if (versionDeltaMs <= 0) {
      throw new DBException(VERSION_DELTA_MS + " must be greater than 0, got: " + versionDeltaMs);
    }

    String anchorStr = props.getProperty(VERSION_ANCHOR_TS);
    if (anchorStr == null || anchorStr.trim().isEmpty()) {
      throw new DBException(VERSION_ANCHOR_TS + " is required when "
          + VERSIONS_PER_QUALIFIER + " > 1");
    }
    try {
      versionAnchorTs = Long.parseLong(anchorStr.trim());
    } catch (NumberFormatException e) {
      throw new DBException("Invalid " + VERSION_ANCHOR_TS + ": " + anchorStr, e);
    }
    if (versionAnchorTs <= 0) {
      throw new DBException(VERSION_ANCHOR_TS + " must be greater than 0 (epoch ms), got: "
          + versionAnchorTs);
    }

    versionSpreadInWindow = Boolean.parseBoolean(
        props.getProperty(VERSION_SPREAD_IN_WINDOW, "true"));

    String windowStr = props.getProperty(VERSION_WINDOW_MS);
    if (windowStr != null && !windowStr.trim().isEmpty()) {
      try {
        versionWindowMs = Long.parseLong(windowStr.trim());
      } catch (NumberFormatException e) {
        throw new DBException("Invalid " + VERSION_WINDOW_MS + ": " + windowStr, e);
      }
      if (versionWindowMs <= 0) {
        throw new DBException(VERSION_WINDOW_MS + " must be greater than 0, got: "
            + versionWindowMs);
      }
    }

    long versionSpanMs = (long) (versionsPerQualifier - 1) * versionDeltaMs;
    if (versionSpanMs >= versionWindowMs) {
      throw new DBException("Version span (" + versionSpanMs + " ms) must be less than "
          + VERSION_WINDOW_MS + " (" + versionWindowMs + " ms)");
    }
  }

  /**
   * Latest-version timestamp T0 for the given row key.
   * i=0 uses T0; older versions use T0 - i * versionDeltaMs.
   */
  private long computeLatestVersionTs(String key) {
    long t0 = versionAnchorTs;
    if (versionSpreadInWindow) {
      long spreadOffset = keySpreadOffset(key) % versionWindowMs;
      t0 = versionAnchorTs - spreadOffset;
    }
    return t0;
  }

  private long keySpreadOffset(String key) {
    int end = key.length();
    int start = end;
    while (start > 0 && Character.isDigit(key.charAt(start - 1))) {
      start--;
    }
    if (start < end) {
      try {
        return Long.parseLong(key.substring(start));
      } catch (NumberFormatException e) {
        // fall through
      }
    }
    try {
      return Math.abs(Long.parseLong(key.trim()));
    } catch (NumberFormatException e) {
      return Math.abs((long) key.hashCode());
    }
  }

  private static void applyConfiguration(Properties props) {
    String quorum = props.getProperty(ZOOKEEPER_QUORUM);
    if (quorum != null && !quorum.isEmpty()) {
      CONFIG.set(ZOOKEEPER_QUORUM, quorum);
    }
    String clientPort = props.getProperty(ZOOKEEPER_CLIENT_PORT, "2181");
    CONFIG.set(ZOOKEEPER_CLIENT_PORT, clientPort);
    String master = props.getProperty(HBASE_MASTER);
    if (master != null && !master.isEmpty()) {
      CONFIG.set(HBASE_MASTER, master);
    }
    String rpcTimeout = props.getProperty(HBASE_RPC_TIMEOUT);
    if (rpcTimeout != null && !rpcTimeout.trim().isEmpty()) {
      CONFIG.set(HBASE_RPC_TIMEOUT, rpcTimeout.trim());
    }
    String socketTimeout = props.getProperty(IPC_SOCKET_TIMEOUT);
    if (socketTimeout != null && !socketTimeout.trim().isEmpty()) {
      CONFIG.set(IPC_SOCKET_TIMEOUT, socketTimeout.trim());
    } else if (rpcTimeout != null && !rpcTimeout.trim().isEmpty()) {
      // Match rpc timeout when socket timeout is unset (0.94 default socket timeout is lower).
      CONFIG.set(IPC_SOCKET_TIMEOUT, rpcTimeout.trim());
    }
  }

  @Override
  public void cleanup() throws DBException {
    try {
      if (hTable != null) {
        hTable.flushCommits();
      }
      synchronized (CONNECTION_LOCK) {
        int threadCount = THREAD_COUNT.decrementAndGet();
        if (threadCount <= 0 && hConn != null) {
          hConn.close();
          hConn = null;
        }
        if (verifyReadVersionsEnabled && threadCount <= 0
            && VERIFY_READ_SUMMARY_PRINTED.compareAndSet(0, 1)) {
          System.out.println("[HBaseClient94] read version verify summary: pass="
              + VERIFY_READ_PASS.get() + ", fail=" + VERIFY_READ_FAIL.get()
              + ", expectedPerQualifier=" + verifyReadVersionsExpected
              + ", fieldCount=" + verifyReadVersionsFieldCount);
        }
      }
      // Latency tracked by DBWrapper.cleanup() as CLEANUP; do not measure here.
    } catch (IOException e) {
      throw new DBException(e);
    }
  }

  private void getHTable(String table) throws IOException {
    synchronized (TABLE_LOCK) {
      hTable = hConn.getTable(table);
      hTable.setAutoFlush(!clientSideBuffering, true);
      hTable.setWriteBufferSize(writeBufferSize);
    }
  }

  private void applyReadGetOptions(Get g) throws IOException {
    if (readMaxVersions > 0) {
      g.setMaxVersions(readMaxVersions);
    }
    if (readTimeRangeEnabled) {
      long minTs = readAnchorTs - readWindowMs;
      g.setTimeRange(minTs, readAnchorTs);
      if (debug) {
        System.out.println("Get TimeRange: minTs=" + minTs + ", maxTs=" + readAnchorTs
            + ", maxVersions=" + readMaxVersions);
      }
    } else if (debug && readMaxVersions > 0) {
      System.out.println("Get maxVersions=" + readMaxVersions);
    }
  }

  @Override
  public Status read(String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
    if (!ensureHTable(table)) {
      return Status.ERROR;
    }

    Result r;
    try {
      Get g = new Get(Bytes.toBytes(key));
      if (fields == null) {
        g.addFamily(columnFamilyBytes);
      } else {
        for (String field : fields) {
          g.addColumn(columnFamilyBytes, Bytes.toBytes(field));
        }
      }
      applyReadGetOptions(g);
      r = hTable.get(g);
    } catch (IOException e) {
      System.err.println("Error doing get: " + e);
      return Status.ERROR;
    } catch (ConcurrentModificationException e) {
      return Status.ERROR;
    }

    if (r == null || r.isEmpty()) {
      return Status.NOT_FOUND;
    }

    if (verifyReadVersionsEnabled) {
      Status verifyStatus = verifyReadVersionCounts(key, r, fields);
      if (Status.OK != verifyStatus) {
        return verifyStatus;
      }
    }

    for (KeyValue kv : r.raw()) {
      String qualifier = Bytes.toString(kv.getQualifier());
      // HBase returns versions newest-first; keep the first per qualifier for YCSB.
      if (result.containsKey(qualifier)) {
        continue;
      }
      result.put(qualifier, new ByteArrayByteIterator(kv.getValue()));
      if (debug) {
        System.out.println("Result for field: " + Bytes.toString(kv.getQualifier())
            + " is: " + Bytes.toString(kv.getValue()));
      }
    }
    return Status.OK;
  }

  /**
   * Counts KeyValues per qualifier (after RS TimeRange / maxVersions filtering) and compares
   * to hbase.verifyReadVersionsExpected.
   */
  private Status verifyReadVersionCounts(String key, Result r, Set<String> fields) {
    Map<String, Integer> versionCounts = new HashMap<String, Integer>();
    KeyValue[] raw = r.raw();
    int i = 0;
    for (; i < raw.length; ++i) {
      KeyValue kv = raw[i];
      if (!Bytes.equals(columnFamilyBytes, kv.getFamily())) {
        continue;
      }
      String qualifier = Bytes.toString(kv.getQualifier());
      Integer cnt = versionCounts.get(qualifier);
      if (null == cnt) {
        versionCounts.put(qualifier, 1);
      } else {
        versionCounts.put(qualifier, cnt + 1);
      }
    }

    if (null != fields && !fields.isEmpty()) {
      for (String field : fields) {
        Integer cnt = versionCounts.get(field);
        if (null == cnt || cnt.intValue() != verifyReadVersionsExpected) {
          logVerifyFailure(key, versionCounts);
          VERIFY_READ_FAIL.incrementAndGet();
          return Status.UNEXPECTED_STATE;
        }
      }
    } else {
      if (versionCounts.size() != verifyReadVersionsFieldCount) {
        logVerifyFailure(key, versionCounts);
        VERIFY_READ_FAIL.incrementAndGet();
        return Status.UNEXPECTED_STATE;
      }
      for (Map.Entry<String, Integer> entry : versionCounts.entrySet()) {
        if (entry.getValue().intValue() != verifyReadVersionsExpected) {
          logVerifyFailure(key, versionCounts);
          VERIFY_READ_FAIL.incrementAndGet();
          return Status.UNEXPECTED_STATE;
        }
      }
    }

    VERIFY_READ_PASS.incrementAndGet();
    return Status.OK;
  }

  private void logVerifyFailure(String key, Map<String, Integer> versionCounts) {
    if (debug || VERIFY_READ_FAIL.get() < 5) {
      System.err.println("[HBaseClient94] read version verify failed: key=" + key
          + ", expectedPerQualifier=" + verifyReadVersionsExpected
          + ", fieldCount=" + verifyReadVersionsFieldCount
          + ", actual=" + versionCounts);
    }
  }

  @Override
  public Status scan(String table, String startkey, int recordcount, Set<String> fields,
      Vector<HashMap<String, ByteIterator>> result) {
    if (!this.tableName.equals(table)) {
      hTable = null;
      try {
        getHTable(table);
        this.tableName = table;
      } catch (IOException e) {
        System.err.println("Error accessing HBase table: " + e);
        return Status.ERROR;
      }
    }

    Scan s = new Scan(Bytes.toBytes(startkey));
    s.setCaching(recordcount);
    if (usePageFilter) {
      s.setFilter(new PageFilter(recordcount));
    }
    if (fields == null) {
      s.addFamily(columnFamilyBytes);
    } else {
      for (String field : fields) {
        s.addColumn(columnFamilyBytes, Bytes.toBytes(field));
      }
    }

    ResultScanner scanner = null;
    try {
      scanner = hTable.getScanner(s);
      int numResults = 0;
      for (Result rr = scanner.next(); rr != null; rr = scanner.next()) {
        HashMap<String, ByteIterator> rowResult = new HashMap<>();
        for (KeyValue kv : rr.raw()) {
          rowResult.put(Bytes.toString(kv.getQualifier()), new ByteArrayByteIterator(kv.getValue()));
        }
        result.add(rowResult);
        numResults++;
        if (numResults >= recordcount) {
          break;
        }
      }
    } catch (IOException e) {
      if (debug) {
        System.out.println("Error in getting/parsing scan result: " + e);
      }
      return Status.ERROR;
    } finally {
      if (scanner != null) {
        scanner.close();
      }
    }
    return Status.OK;
  }

  @Override
  public Status update(String table, String key, Map<String, ByteIterator> values) {
    if (!ensureHTable(table)) {
      return Status.ERROR;
    }
    return putRow(key, values);
  }

  @Override
  public Status batchPut(String table, Map<String, Map<String, ByteIterator>> valuesMap) {
    if (!ensureHTable(table)) {
      return Status.ERROR;
    }
    if (valuesMap.isEmpty()) {
      return Status.OK;
    }
    List<Put> puts = new ArrayList<>(valuesMap.size());
    for (Map.Entry<String, Map<String, ByteIterator>> entry : valuesMap.entrySet()) {
      puts.add(buildPut(entry.getKey(), entry.getValue()));
    }
    try {
      if (puts.size() == 1) {
        hTable.put(puts.get(0));
      } else {
        hTable.put(puts);
      }
    } catch (IOException e) {
      if (debug) {
        System.err.println("Error doing batch put: " + e);
      }
      return Status.ERROR;
    } catch (ConcurrentModificationException e) {
      return Status.ERROR;
    }
    return Status.OK;
  }

  private boolean ensureHTable(String table) {
    if (this.tableName.equals(table) && hTable != null) {
      return true;
    }
    hTable = null;
    try {
      getHTable(table);
      this.tableName = table;
      return true;
    } catch (IOException e) {
      System.err.println("Error accessing HBase table: " + e);
      return false;
    }
  }

  private Status putRow(String key, Map<String, ByteIterator> values) {
    try {
      hTable.put(buildPut(key, values));
    } catch (IOException e) {
      if (debug) {
        System.err.println("Error doing put: " + e);
      }
      return Status.ERROR;
    } catch (ConcurrentModificationException e) {
      return Status.ERROR;
    }
    return Status.OK;
  }

  /**
   * One Put per row: all qualifiers and explicit timestamps in a single RPC.
   * Multi-version load uses V cells per qualifier (same data as before).
   */
  private Put buildPut(String key, Map<String, ByteIterator> values) {
    byte[] row = Bytes.toBytes(key);
    Put p = new Put(row);
    if (versionsPerQualifier <= 1) {
      for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
        p.add(columnFamilyBytes, Bytes.toBytes(entry.getKey()), entry.getValue().toArray());
      }
      return p;
    }

    long latestTs = computeLatestVersionTs(key);
    for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
      byte[] qualifier = Bytes.toBytes(entry.getKey());
      byte[] value = entry.getValue().toArray();
      for (int i = 0; i < versionsPerQualifier; i++) {
        long ts = latestTs - (long) i * versionDeltaMs;
        p.add(columnFamilyBytes, qualifier, ts, value);
      }
    }
    return p;
  }

  @Override
  public Status insert(String table, String key, Map<String, ByteIterator> values) {
    return update(table, key, values);
  }

  @Override
  public Status delete(String table, String key) {
    if (!this.tableName.equals(table)) {
      hTable = null;
      try {
        getHTable(table);
        this.tableName = table;
      } catch (IOException e) {
        System.err.println("Error accessing HBase table: " + e);
        return Status.ERROR;
      }
    }

    Delete d = new Delete(Bytes.toBytes(key));
    try {
      hTable.delete(d);
    } catch (IOException e) {
      if (debug) {
        System.err.println("Error doing delete: " + e);
      }
      return Status.ERROR;
    }
    return Status.OK;
  }
}
