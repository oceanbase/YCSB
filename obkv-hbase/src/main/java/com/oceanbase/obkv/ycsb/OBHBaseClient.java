/**
 * Copyright (c) 2010 Yahoo! Inc. All rights reserved.
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

package com.oceanbase.obkv.ycsb;
import com.alipay.oceanbase.rpc.property.Property;
import site.ycsb.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.PageFilter;
import org.apache.hadoop.hbase.util.Bytes;
import java.io.IOException;
import java.util.*;
import static com.alipay.oceanbase.hbase.constants.OHConstants.*;
import static site.ycsb.Status.*;
import static org.apache.commons.lang.StringUtils.isNotBlank;

public class OBHBaseClient extends DB {
    public static final String PROP_TEST_MODE                   = "obkv.testMode";
    public static final String PROP_MAX_KEY                     = "obkv.maxKey";
    public static final String PROP_KEY_PARTITION_START_TS      = "obkv.rangePartitionStartTs";
    public static final String PROP_KEY_PARTITION_DURATION_MS   = "obkv.rangePartitionDurationMs";
    public static final String PROP_KEY_PARTITION_COUNT         = "obkv.rangePartitionCount";
    public static final String PROP_PREFIX_COUNT                = "obkv.prefixCount";
    public static final String PROP_USE_PAGE_FILTER             = "obkv.scan.usePageFilter";
    public static final String PROP_PAGE_FILTER_SIZE            = "obkv.scan.pageFilterSize";

    public static final String COLUMN_FAMILY = "hbase.oceanbase.columnFamily";
    public static final String TABLE         = "hbase.oceanbase.table";
    public static final String HBASE_MASTER  = "hbase.master";
    public static final String ZOOKEEPER_QUORUM = "hbase.zookeeper.quorum";
    public static final String ZOOKEEPER_CLIENT_PORT = "hbase.zookeeper.property.clientPort";
    public static final String HBASE_CLIENT_IPC_POOL_SIZE = "hbase.client.ipc.pool.size";
    public static final String USE_PUT_OPTIMIZATION = "hbase.htable.use.put.optimization";
    private String             columnFamily;
    private byte[]             columnFamilyBytes;
    private String             tableName;
    public boolean             debug         = false;
    private Connection connection = null;
    private int                zeropadding;
    private boolean            isObkv = true;
    private String testMode = "default";  // 测试模式：default 或 prefix
    private long maxKey = Long.MAX_VALUE;  // key的最大值，用于取余
    private long partitionStartTs = 0;  // 第一个range分区的起始时间戳（毫秒）
    private long partitionDurationMs = 0;  // 每个range分区的时间长度（毫秒）
    private int partitionCount = 0;  // 一级range分区的数量
    private int prefixCount = 1000;  // 前缀ID的总数
    private boolean usePageFilter = false;  // 是否使用PageFilter进行scan查询
    private Integer pageFilterSize = null;  // PageFilter的大小，如果为null则使用recordcount

    @Override
    public void cleanup() throws DBException {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                throw new DBException(e);
            }
        }
    }

    /**
     * 初始化，可以从 java 启动参数中传入
     * @throws DBException exception
     */
    public void init() throws DBException {
        debug = Boolean.parseBoolean(getProperties().getProperty("obkv.debug", "false"));
        isObkv = Boolean.parseBoolean(getProperties().getProperty("isObkv", "true"));
        columnFamily = getProperties().getProperty(COLUMN_FAMILY);
        tableName = getProperties().getProperty(TABLE);
        columnFamilyBytes = Bytes.toBytes(columnFamily);
        
        // 读取测试模式
        testMode = getProperties().getProperty(PROP_TEST_MODE, "default");
        if (!testMode.equals("default") && !testMode.equals("prefix")) {
            throw new DBException("Invalid testMode: " + testMode + ", must be 'default' or 'prefix'");
        }
        
        // 读取maxKey配置
        String maxKeyStr = getProperties().getProperty(PROP_MAX_KEY);
        if (maxKeyStr != null && !maxKeyStr.trim().isEmpty()) {
            try {
                maxKey = Long.parseLong(maxKeyStr.trim());
                if (maxKey <= 0) {
                    throw new DBException("Invalid maxKey configuration: " + PROP_MAX_KEY + 
                                        " must be greater than 0, got: " + maxKey);
                }
            } catch (NumberFormatException e) {
                throw new DBException("Invalid maxKey configuration: " + PROP_MAX_KEY + 
                                    " must be a valid long integer, got: " + maxKeyStr, e);
            }
        }
        
        // 读取zeropadding（从workload配置中获取）
        String zeropaddingStr = getProperties().getProperty("zeropadding", "12");
        try {
            zeropadding = Integer.parseInt(zeropaddingStr);
        } catch (NumberFormatException e) {
            zeropadding = 12;  // 默认值
        }
        
        System.out.println("columnFamily: " + columnFamily + ", table: " + tableName + ", debug: " + debug + ", isObkv: " + isObkv + ", testMode: " + testMode);
        
        // 前缀模式初始化
        if (testMode.equals("prefix")) {
            initPrefixMode();
        }
        
        usePageFilter = Boolean.parseBoolean(getProperties().getProperty(PROP_USE_PAGE_FILTER, "false"));
        String pageFilterSizeStr = getProperties().getProperty(PROP_PAGE_FILTER_SIZE);
        if (pageFilterSizeStr != null && !pageFilterSizeStr.trim().isEmpty()) {
            try {
                pageFilterSize = Integer.parseInt(pageFilterSizeStr.trim());
                if (pageFilterSize <= 0) {
                    throw new DBException("Invalid pageFilterSize configuration: " + PROP_PAGE_FILTER_SIZE + 
                                        " must be greater than 0, got: " + pageFilterSize);
                }
            } catch (NumberFormatException e) {
                throw new DBException("Invalid pageFilterSize configuration: " + PROP_PAGE_FILTER_SIZE + 
                                    " must be a valid integer, got: " + pageFilterSizeStr, e);
            }
        }
        if (debug && usePageFilter) {
            System.out.println("PageFilter is enabled for scan operations");
            if (pageFilterSize != null) {
                System.out.println("PageFilter size is configured to: " + pageFilterSize);
            } else {
                System.out.println("PageFilter size will use recordcount parameter");
            }
        }
        Configuration config = HBaseConfiguration.create();
        if (isObkv) {
            config.set(ClusterConnection.HBASE_CLIENT_CONNECTION_IMPL, "com.alipay.oceanbase.hbase.util.OHConnectionImpl");
            initObkvConfig(config);
        } else {
            initHBaseConfigAndTestConnectivity(config);
        }
        try {
            connection = ConnectionFactory.createConnection(config);
            
            if (!isObkv) {
                final TableName tName = TableName.valueOf(tableName);
                System.out.println("Checking if table exists: " + tName);
                
                try (Admin admin = connection.getAdmin()) {
                    System.out.println("Admin created, checking table existence...");
                    boolean tableExists = admin.tableExists(tName);
                    System.out.println("Table existence check completed: " + tableExists);
                    
                    if (!tableExists) {
                        throw new DBException("Table " + tName + " does not exist");
                    }
                } catch (IOException e) {
                    System.err.println("Error checking table existence: " + e.getMessage());
                    if (e.getCause() != null) {
                        System.err.println("Root cause: " + e.getCause().getMessage());
                    }
                    e.printStackTrace();
                }
                System.out.println("Table exists and is accessible");
            }
        } catch (IOException e) {
            System.err.println("Error during HBase initialization: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Root cause: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            throw new DBException(e);
        } catch (Exception e) {
            System.err.println("Unexpected error during HBase initialization: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Root cause: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            throw new DBException(e);
        }
    }

    private void initHBaseConfigAndTestConnectivity(final Configuration config) {
        // 不设置hbase.master，让HBase通过ZooKeeper自动发现
        // config.set("hbase.master", getProperties().getProperty("hbase.master", "127.0.0.1:16000"));
        config.set(ZOOKEEPER_QUORUM, getProperties().getProperty(ZOOKEEPER_QUORUM, "127.0.0.1"));
        config.set(ZOOKEEPER_CLIENT_PORT, getProperties().getProperty(ZOOKEEPER_CLIENT_PORT, "2181"));
        config.set(HBASE_CLIENT_IPC_POOL_SIZE, getProperties().getProperty(HBASE_CLIENT_IPC_POOL_SIZE, "256"));

        // 添加超时配置，防止卡死
        config.set("hbase.client.operation.timeout", "10000"); // 10秒操作超时
        config.set("hbase.client.scanner.timeout.period", "10000"); // 10秒扫描超时
        config.set("hbase.rpc.timeout", "10000"); // 10秒RPC超时
        config.set("hbase.client.retries.number", "1"); // 重试次数
        config.set("hbase.client.pause", "100"); // 重试间隔100ms

        // 添加更多HBase配置
        config.set("hbase.zookeeper.property.clientPort", getProperties().getProperty(ZOOKEEPER_CLIENT_PORT, "2181"));
        config.set("hbase.zookeeper.quorum", getProperties().getProperty(ZOOKEEPER_QUORUM, "127.0.0.1"));

        System.out.println("HBase Configuration:");
        System.out.println("  ZOOKEEPER_QUORUM: " + config.get(ZOOKEEPER_QUORUM));
        System.out.println("  ZOOKEEPER_CLIENT_PORT: " + config.get(ZOOKEEPER_CLIENT_PORT));
        System.out.println("  HBASE_MASTER: (auto-discovered via ZooKeeper)");
        System.out.println("  HBASE_CLIENT_OPERATION_TIMEOUT: " + config.get("hbase.client.operation.timeout"));
        System.out.println("  HBASE_RPC_TIMEOUT: " + config.get("hbase.rpc.timeout"));

        // 测试网络连接
        System.out.println("Testing network connectivity...");
        testNetworkConnectivity(
                getProperties().getProperty(ZOOKEEPER_QUORUM, "127.0.0.1"),
                Integer.parseInt(getProperties().getProperty(ZOOKEEPER_CLIENT_PORT, "2181")));
    }

    /**
     * 初始化前缀查询模式
     */
    private void initPrefixMode() throws DBException {
        // 验证orderedinserts必须是'ordered'
        String insertOrder = getProperties().getProperty("insertorder", "hashed");
        if (!insertOrder.equals("ordered")) {
            throw new DBException("Prefix query mode requires insertorder='ordered', but got: " + insertOrder);
        }
        
        // 读取prefixCount配置
        String prefixCountStr = getProperties().getProperty(PROP_PREFIX_COUNT);
        if (prefixCountStr != null && !prefixCountStr.trim().isEmpty()) {
            try {
                prefixCount = Integer.parseInt(prefixCountStr.trim());
                if (prefixCount <= 0) {
                    throw new DBException("Invalid prefixCount configuration: " + PROP_PREFIX_COUNT + 
                                        " must be greater than 0, got: " + prefixCount);
                }
            } catch (NumberFormatException e) {
                throw new DBException("Invalid prefixCount configuration: " + PROP_PREFIX_COUNT + 
                                    " must be a valid integer, got: " + prefixCountStr, e);
            }
        } else {
            throw new DBException("Prefix query mode requires " + PROP_PREFIX_COUNT + " configuration");
        }
        
        // 判断是否为二级分区表（通过检查是否有range分区配置）
        boolean isDoublePartition = false;
        String rangePartitionCountStr = getProperties().getProperty(PROP_KEY_PARTITION_COUNT);
        if (rangePartitionCountStr != null && !rangePartitionCountStr.trim().isEmpty()) {
            isDoublePartition = true;
            initPartitionConfig();
        }
        
        if (debug) {
            System.out.println("Prefix mode initialized:");
            System.out.println("  prefixCount: " + prefixCount);
            System.out.println("  isDoublePartition: " + isDoublePartition);
            if (isDoublePartition) {
                System.out.println("  rangePartitionCount: " + partitionCount);
                System.out.println("  rangePartitionStartTs: " + partitionStartTs);
                System.out.println("  rangePartitionDurationMs: " + partitionDurationMs);
            }
        }
    }
    
    private void initPartitionConfig() throws DBException {
        Properties props = getProperties();
        // partition configuration for uniform distribution - 必须显式指定且值必须大于0
        if (props.getProperty(PROP_KEY_PARTITION_START_TS) != null) {
            partitionStartTs = Long.parseLong(props.getProperty(PROP_KEY_PARTITION_START_TS));
            if (partitionStartTs <= 0) {
                throw new DBException("Invalid partition configuration: " + PROP_KEY_PARTITION_START_TS + 
                                    " must be specified and greater than 0 (millisecond timestamp)");
            }
        } else {
            throw new DBException("Partition configuration is required. Please specify: " + PROP_KEY_PARTITION_START_TS + 
                                " (must be greater than 0, millisecond timestamp)");
        }
        if (props.getProperty(PROP_KEY_PARTITION_DURATION_MS) != null) {
            partitionDurationMs = Long.parseLong(props.getProperty(PROP_KEY_PARTITION_DURATION_MS));
            if (partitionDurationMs <= 0) {
                throw new DBException("Invalid partition configuration: " + PROP_KEY_PARTITION_DURATION_MS + 
                                    " must be specified and greater than 0 (milliseconds)");
            }
        } else {
            throw new DBException("Partition configuration is required. Please specify: " + PROP_KEY_PARTITION_DURATION_MS + 
                                " (must be greater than 0, milliseconds)");
        }
        if (props.getProperty(PROP_KEY_PARTITION_COUNT) != null) {
            partitionCount = Integer.parseInt(props.getProperty(PROP_KEY_PARTITION_COUNT));
            if (partitionCount <= 0) {
                throw new DBException("Invalid partition configuration: " + PROP_KEY_PARTITION_COUNT + 
                                    " must be specified and greater than 0 (integer)");
            }
        } else {
            throw new DBException("Partition configuration is required. Please specify: " + PROP_KEY_PARTITION_COUNT + 
                                " (must be greater than 0, integer)");
        }
        
        if (debug) {
            System.out.println("Partition config: startTs=" + partitionStartTs + 
                             ", durationMs=" + partitionDurationMs + 
                             ", count=" + partitionCount);
        }
    }

    private void initObkvConfig(Configuration config) throws DBException {
        Properties props = getProperties();
        boolean odpMode = false;

        if (!isNotBlank(props.getProperty(COLUMN_FAMILY))) {
            throw new DBException("columnFamily is blank!");
        }
        if (!isNotBlank(props.getProperty(TABLE))) {
            throw new DBException("table is blank!");
        }
        if (!isNotBlank(props.getProperty(HBASE_OCEANBASE_FULL_USER_NAME))) {
            throw new DBException("full user name is blank!");
        }

        if (props.getProperty(HBASE_OCEANBASE_ODP_MODE) != null) {
            odpMode = Boolean.parseBoolean(props.getProperty(HBASE_OCEANBASE_ODP_MODE));
        }
        if (odpMode) {
            config.setBoolean(HBASE_OCEANBASE_ODP_MODE, true);
            config.set(HBASE_OCEANBASE_FULL_USER_NAME, props.getProperty(HBASE_OCEANBASE_FULL_USER_NAME));
            config.set(HBASE_OCEANBASE_PASSWORD, props.getProperty(HBASE_OCEANBASE_PASSWORD, ""));
            if (!isNotBlank(props.getProperty(HBASE_OCEANBASE_ODP_ADDR))) {
                throw new DBException("odp addr is blank!");
            }
            config.set(HBASE_OCEANBASE_ODP_ADDR, props.getProperty(HBASE_OCEANBASE_ODP_ADDR));
            if (!isNotBlank(props.getProperty(HBASE_OCEANBASE_ODP_PORT))) {
                throw new DBException("odp port is blank!");
            }
            config.setInt(HBASE_OCEANBASE_ODP_PORT,
                Integer.parseInt(props.getProperty(HBASE_OCEANBASE_ODP_PORT)));
            if (!isNotBlank(props.getProperty(HBASE_OCEANBASE_DATABASE))) {
                throw new DBException("database name is blank!");
            }
            config.set(HBASE_OCEANBASE_DATABASE, props.getProperty(HBASE_OCEANBASE_DATABASE));
        } else {
            if (!isNotBlank(props.getProperty(HBASE_OCEANBASE_PARAM_URL))) {
                throw new DBException("param url is blank!");
            }
            config.set(HBASE_OCEANBASE_PARAM_URL, props.getProperty(HBASE_OCEANBASE_PARAM_URL));
            if (!isNotBlank(props.getProperty(HBASE_OCEANBASE_SYS_USER_NAME))) {
                throw new DBException("sys name is blank!");
            }
            config.set(HBASE_OCEANBASE_SYS_USER_NAME, props.getProperty(HBASE_OCEANBASE_SYS_USER_NAME));
            config.set(HBASE_OCEANBASE_SYS_PASSWORD, props.getProperty(HBASE_OCEANBASE_SYS_PASSWORD, ""));
            config.set(HBASE_OCEANBASE_FULL_USER_NAME, props.getProperty(HBASE_OCEANBASE_FULL_USER_NAME));
            config.set(HBASE_OCEANBASE_PASSWORD, props.getProperty(HBASE_OCEANBASE_PASSWORD, ""));
        }
        if (props.getProperty(USE_PUT_OPTIMIZATION) != null) {
            config.setBoolean(USE_PUT_OPTIMIZATION, Boolean.parseBoolean(props.getProperty(USE_PUT_OPTIMIZATION)));
        } else {
            config.setBoolean(USE_PUT_OPTIMIZATION, false);
        }
        // Some other useful property
        for (Property property : Property.values()) {
            String value = props.getProperty(property.getKey());
            if (value != null) {
                config.set(property.getKey(), value);
            }
        }
    }

    /**
     * 测试网络连接
     */
    private void testNetworkConnectivity(String host, int port) {
        try {
            System.out.println("Testing network connectivity to " + host + ":" + port);
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 5000);
            System.out.println("Network connection successful to " + host + ":" + port);
            socket.close();
        } catch (Exception e) {
            System.err.println("Network connection failed to " + host + ":" + port + ": " + e.getMessage());
        }
    }

    /**
     * 根据测试模式处理key
     * @param ycsbKey YCSB生成的key
     * @return 处理后的key
     */
    private String processKey(String ycsbKey) {
        if (testMode.equals("prefix")) {
            return generatePrefixKey(ycsbKey);
        } else {
            return processKeyForDefaultMode(ycsbKey);
        }
    }
    
    /**
     * 处理默认模式的key，根据maxKey进行取余
     * @param coreKey core生成的key
     * @return 处理后的key
     */
    private String processKeyForDefaultMode(String coreKey) {
        if (maxKey < Long.MAX_VALUE) {
            try {
                long keyValue = Long.parseLong(coreKey.trim());
                keyValue = keyValue % (maxKey + 1);
                // 保持原有的零填充格式
                return String.format("%0" + zeropadding + "d", keyValue);
            } catch (NumberFormatException e) {
                // 如果无法解析为数字，直接返回原key
                if (debug) {
                    System.err.println("Warning: Cannot parse key as number: " + coreKey + ", using original key");
                }
                return coreKey;
            }
        }
        return coreKey;
    }
    
    /**
     * 生成前缀模式的key（prefixId_subId格式）
     * @param ycsbKey YCSB生成的key
     * @return prefixId_subId格式的key
     */
    private String generatePrefixKey(String ycsbKey) {
        try {
            long keyValue = Long.parseLong(ycsbKey.trim());
            long prefixId = keyValue / prefixCount;
            long subId = keyValue % prefixCount;
            return String.format("%d_%d", prefixId, subId);
        } catch (NumberFormatException e) {
            // 如果无法解析为数字，使用hashCode
            long keyValue = Math.abs((long)ycsbKey.hashCode());
            long prefixId = keyValue / prefixCount;
            long subId = keyValue % prefixCount;
            return String.format("%d_%d", prefixId, subId);
        }
    }
    
    /**
     * 生成二级分区表的时间戳，确保均匀分布在各个range分区
     * 基于partitionStartTs、partitionDurationMs、partitionCount自动计算时间戳
     * @param ycsbKey YCSB生成的key
     * @return 时间戳（毫秒）
     */
    private long generateTimestampForDoublePartition(String ycsbKey) {
        long keyValue;
        try {
            keyValue = Long.parseLong(ycsbKey.trim());
        } catch (NumberFormatException e) {
            keyValue = Math.abs((long)ycsbKey.hashCode());
        }
        
        long subId = keyValue % prefixCount;  // 计算subId
        
        // 使用subId计算分区索引，确保均匀分布
        int partitionIndex = (int) (subId % partitionCount);
        long offsetInPartition = subId % partitionDurationMs;
        
        // 直接基于partitionStartTs计算时间戳，确保均匀分布在各个range分区
        // 时间戳 = 分区起始时间 + 分区索引 * 分区时长 + 分区内偏移
        long finalTs = partitionStartTs + partitionIndex * partitionDurationMs + offsetInPartition;
        
        if (debug) {
            System.out.println("generateTimestampForDoublePartition: key=" + ycsbKey + 
                             ", subId=" + subId + 
                             ", partitionIndex=" + partitionIndex + 
                             ", offsetInPartition=" + offsetInPartition + 
                             ", finalTs=" + finalTs);
        }
        
        return finalTs;
    }
    
    /**
     * 从prefix key中提取prefixId
     * @param prefixKey prefixId_subId格式的key
     * @return prefixId
     */
    private String extractPrefixId(String prefixKey) {
        int underscoreIndex = prefixKey.indexOf('_');
        if (underscoreIndex > 0) {
            return prefixKey.substring(0, underscoreIndex) + "_";
        }
        return null;
    }
    
    /**
     * 读取数据测试，目前无法测试批量读取
     * @param table table
     * @param key key
     * @param fields fields
     * @param result result
     * @return ans
     */
    @Override
    public Status read(String table, String key, Set<String> fields,
                       Map<String, ByteIterator> result) {
        Result r = null;
        try {
            String processedKey = processKey(key);
            
            if (debug) {
                System.out.println("Doing read from HBase columnfamily " + columnFamily);
                System.out.println("Original key: " + key);
                System.out.println("Processed key: " + processedKey);
            }
            
            Get g = new Get(Bytes.toBytes(processedKey));
            if (fields == null) {
                g.addFamily(columnFamilyBytes);
            } else {
                for (String field : fields) {
                    g.addColumn(columnFamilyBytes, Bytes.toBytes(field));
                }
            }
            r = connection.getTable(TableName.valueOf(tableName)).get(g);
        } catch (Exception e) {
            IOException ioException = (e instanceof IOException) ? (IOException) e : new IOException("Error in read", e);
            System.err.println("Error doing get: " + ioException);
            ioException.printStackTrace();
            return SERVICE_UNAVAILABLE;
        }
        while (r.advance()) {
            final Cell cell = r.current();
            result.put(Bytes.toString(CellUtil.cloneQualifier(cell)), 
                      new ByteArrayByteIterator(CellUtil.cloneValue(cell)));
            if (debug) {
                System.out.println("Result for field: " + Bytes.toString(CellUtil.cloneQualifier(cell))
                                   + " is: " + Bytes.toString(CellUtil.cloneValue(cell)));
            }
        }
        return OK;
    }

    /**
     * @param table table
     * @param startkey startkey
     * @param recordcount recordcount
     * @param fields fields
     * @param result result
     * @return ans
     */
    @Override
    public Status scan(String table, String startkey, int recordcount, Set<String> fields,
                       Vector<HashMap<String, ByteIterator>> result) {
        ResultScanner scanner = null;
        try {
            Scan scan = new Scan();
            scan.setCaching(recordcount);
            scan.setMaxVersions(1);
            
            if (testMode.equals("prefix")) {
                // 前缀模式：使用setRowPrefixFilter
                String prefixId = extractPrefixId(startkey);
                byte[] prefixBytes = Bytes.toBytes(prefixId + "_");
                scan.setRowPrefixFilter(prefixBytes);
                
                if (debug) {
                    System.out.println("Prefix scan: prefixId=" + prefixId);
                }
            } else {
                // 默认模式：使用setStartRow（不设置setStopRow）
                String processedKey = processKeyForDefaultMode(startkey);
                scan.setStartRow(Bytes.toBytes(processedKey));
                
                if (debug) {
                    System.out.println("Default scan: startkey=" + processedKey);
                }
            }

            // 如果启用PageFilter，设置PageFilter并指定pagesize
            if (usePageFilter) {
                // 如果配置了pageFilterSize，使用配置的值，否则使用recordcount
                int pageSize = (pageFilterSize != null) ? pageFilterSize : recordcount;
                PageFilter pageFilter = new PageFilter(pageSize);
                scan.setFilter(pageFilter);
                if (debug) {
                    System.out.println("Using PageFilter with pageSize: " + pageSize + 
                                     (pageFilterSize != null ? " (configured)" : " (from recordcount)"));
                }
            } else {
                scan.setLimit(recordcount);
            }
        
            if (fields == null) {
                scan.addFamily(columnFamilyBytes);
            } else {
                for (String field : fields) {
                    scan.addColumn(columnFamilyBytes, Bytes.toBytes(field));
                }
            }

            scanner = connection.getTable(TableName.valueOf(tableName)).getScanner(scan);
            int numResults = 0;
            for (Result rr = scanner.next(); rr != null; rr = scanner.next()) {
                // get row key
                String key = Bytes.toString(rr.getRow());

                if (debug) {
                    System.out.println("Got scan result for key: " + key);
                }

                HashMap<String, ByteIterator> rowResult =
                        new HashMap<String, ByteIterator>();

                while (rr.advance()) {
                    final Cell cell = rr.current();
                    rowResult.put(Bytes.toString(CellUtil.cloneQualifier(cell)),
                            new ByteArrayByteIterator(CellUtil.cloneValue(cell)));
                }

                // add rowResult to result vector
                result.add(rowResult);
                numResults++;
                if (debug) {
                    for (Map.Entry<String, ByteIterator> entry : rowResult.entrySet()) {
                        System.out.println("Result for field: " + entry.getKey() + " is: " + entry.getValue());
                    }
                }
                // PageFilter does not guarantee that the number of results is <=
                // pageSize, so this
                // break is required.
                if (numResults >= recordcount) {// if hit recordcount, bail out
                    break;
                }
            }
        } catch (Exception e) {
            IOException ioException = (e instanceof IOException) ? (IOException) e : new IOException("Error in scan", e);
            System.err.println("Error in getting/parsing scan result: " + ioException);
            ioException.printStackTrace();
            return Status.ERROR;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return Status.OK;
    }

    /**
     * 更新操作，目前不支持批量接口测试
     * @param table table
     * @param key key
     * @param values values
     * @return ans
     */
    @Override
    public Status update(String table, String key, Map<String, ByteIterator> values) {
        String processedKey = processKey(key);
        
        if (debug) {
            System.out.println("Setting up put for key: " + key);
            System.out.println("Processed key: " + processedKey);
        }
        
        Put p = new Put(Bytes.toBytes(processedKey));
        for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
            if (debug) {
                System.out.println("Adding field/value " + entry.getKey() + "/" + entry.getValue()
                                   + " to put request");
            }
            
            // 判断是否需要指定时间戳（仅二级分区表的前缀模式）
            boolean needTimestamp = testMode.equals("prefix") && partitionCount > 0 && partitionDurationMs > 0;
            if (needTimestamp) {
                long timestamp = generateTimestampForDoublePartition(key);
                p.addColumn(columnFamilyBytes, Bytes.toBytes(entry.getKey()), timestamp, entry.getValue().toArray());
            } else {
                p.addColumn(columnFamilyBytes, Bytes.toBytes(entry.getKey()), entry.getValue().toArray());
            }
        }
        try {
            connection.getTable(TableName.valueOf(tableName)).put(p);
            if (debug) {
                System.out.println("put success");
            }
        } catch (Exception e) {
            IOException ioException = (e instanceof IOException) ? (IOException) e : new IOException("Error in update", e);
            System.err.println("Error doing put: " + ioException);
            ioException.printStackTrace();
            return SERVICE_UNAVAILABLE;
        }
        return OK;
    }

    @Override
    public Status insert(String table, String key, Map<String, ByteIterator> values) {
        return update(table, key, values);
    }

    /**
     * 删除接口，目前不支持批量删除测试
     * @param table table
     * @param key key
     * @return ans
     */
    @Override
    public Status delete(String table, String key) {
        String processedKey = processKey(key);
        
        Delete delete = new Delete(Bytes.toBytes(processedKey));
        delete.addFamily(columnFamilyBytes);
        try {
            connection.getTable(TableName.valueOf(tableName)).delete(delete);
            if (debug) {
                System.out.println("delete success for key: " + processedKey);
            }
            return OK;
        } catch (Exception e) {
            IOException ioException = (e instanceof IOException) ? (IOException) e : new IOException("Error in delete", e);
            System.err.println("Error doing delete: " + ioException);
            ioException.printStackTrace();
            return ERROR;
        }
    }

    @Override
    public Status batchPut(String table, Map<String, Map<String, ByteIterator>> valuesMap) {
        List<Put> putList = new ArrayList<>();
        valuesMap.forEach((key, values) -> {
            String processedKey = processKey(key);
            
            Put put = new Put(processedKey.getBytes());
            values.forEach((k, v) -> {
                // 判断是否需要指定时间戳（仅二级分区表的前缀模式）
                boolean needTimestamp = testMode.equals("prefix") && partitionCount > 0 && partitionDurationMs > 0;
                if (needTimestamp) {
                    long timestamp = generateTimestampForDoublePartition(key);
                    put.addColumn(columnFamilyBytes, k.getBytes(), timestamp, v.toArray());
                } else {
                    put.addColumn(columnFamilyBytes, k.getBytes(), v.toArray());
                }
            });
            putList.add(put);
        });
        try {
            connection.getTable(TableName.valueOf(tableName)).put(putList);
            if (debug) {
                System.out.println("batchPut success, count: " + putList.size());
            }
        } catch (Exception e) {
            IOException ioException = (e instanceof IOException) ? (IOException) e : new IOException("Error in batchPut", e);
            System.err.println("Error doing batchPut: " + ioException);
            ioException.printStackTrace();
            return Status.ERROR;
        }
        return Status.OK;
    }

    @Override
    public Status batchRead(String table, Set<String> fields, Map<String, Map<String, ByteIterator>> valuesMap) {
        List<Get> getList = new ArrayList<>();
        List<String> originalKeys = new ArrayList<>();
        
        valuesMap.keySet().forEach(key -> {
            originalKeys.add(key);
            String processedKey = processKey(key);
            
            Get get = new Get(processedKey.getBytes());
            if (fields == null) {
                get.addFamily(columnFamilyBytes);
            } else {
                for (String field : fields) {
                    get.addColumn(columnFamilyBytes, Bytes.toBytes(field));
                }
            }
            getList.add(get);
        });
        try {
            Result[] res = null;
            res = connection.getTable(TableName.valueOf(tableName)).get(getList);
            if (res == null || res.length == 0) {
                if (debug) {
                    System.out.println("Result is empty");
                }
                return Status.NOT_FOUND;
            }
            for (int i = 0; i < res.length; i++) {
                if (res[i] == null || ((Result)res[i]).isEmpty()) {
                    if (debug) {
                        System.out.println("Result for key: " + originalKeys.get(i) + " is empty");
                    }
                    continue;
                }
                String originalKey = originalKeys.get(i);
                Map<String, ByteIterator> result = new HashMap<>();
                while (res[i].advance()) {
                    final Cell c = res[i].current();
                    result.put(Bytes.toString(CellUtil.cloneQualifier(c)),
                            new ByteArrayByteIterator(CellUtil.cloneValue(c)));
                    if (debug) {
                        System.out.println(
                                "Result for key: " + originalKey + ", field: " + Bytes.toString(CellUtil.cloneQualifier(c))
                                        + " is: " + Bytes.toString(CellUtil.cloneValue(c)));
                    }
                }
                valuesMap.put(originalKey, result);
            }
        } catch (Exception e) {
            IOException ioException = (e instanceof IOException) ? (IOException) e : new IOException("Error in batchRead", e);
            System.err.println("Error doing batchRead: " + ioException);
            ioException.printStackTrace();
            return Status.ERROR;
        }
        return Status.OK;
    }
}