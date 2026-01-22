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
    public static final String PROP_KEY_PARTITION_START_TS      = "obkv.rangePartitionStartTs";
    public static final String PROP_KEY_PARTITION_DURATION_MS   = "obkv.rangePartitionDurationMs";
    public static final String PROP_KEY_PARTITION_COUNT         = "obkv.rangePartitionCount";
    public static final String PROP_KEY_COUNT                   = "obkv.keyCount";
    public static final String PROP_ENABLE_TIME_RANGE_TEST_MODE = "obkv.enableTimeRangeTestMode";
    public static final String PROP_USE_PAGE_FILTER             = "obkv.scan.usePageFilter";
    public static final String PROP_PAGE_FILTER_SIZE            = "obkv.scan.pageFilterSize";

    public static final String COLUMN_FAMILY = "hbase.oceanbase.columnFamily";
    public static final String TABLE         = "hbase.oceanbase.table";
    public static final String HBASE_MASTER  = "hbase.master";
    public static final String ZOOKEEPER_QUORUM = "hbase.zookeeper.quorum";
    public static final String ZOOKEEPER_CLIENT_PORT = "hbase.zookeeper.property.clientPort";
    public static final String HBASE_CLIENT_IPC_POOL_SIZE = "hbase.client.ipc.pool.size";
    public static final String USE_PUT_OPTIMIZATION = "hbase.htable.use.put.optimization";
    private static final String KEY_FORMAT = "user_%012d_%s";
    private String             columnFamily;
    private byte[]             columnFamilyBytes;
    private String             tableName;
    public boolean             debug         = false;
    private Connection connection = null;
    private int                zeropadding;
    private boolean            isObkv = true;
    private long partitionStartTs = 0;  // 第一个range分区的起始时间戳（毫秒）
    private long partitionDurationMs = 0;  // 每个range分区的时间长度（毫秒）
    private int partitionCount = 0;  // 一级range分区的数量
    private int keyCount = 1;  // id的总数量
    private boolean enableTimeRangeTestMode = false;  // 是否启用时间范围测试模式
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
        System.out.println("columnFamily: " + columnFamily + ", table: " + tableName + ", debug: " + debug + ", isObkv: " + isObkv);
        enableTimeRangeTestMode = Boolean.parseBoolean(getProperties().getProperty(PROP_ENABLE_TIME_RANGE_TEST_MODE, "false"));
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
        if (enableTimeRangeTestMode) {
            initPartitionConfig();
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
        if (props.getProperty(PROP_KEY_COUNT) != null) {
            keyCount = Integer.parseInt(props.getProperty(PROP_KEY_COUNT));
            if (keyCount <= 0) {
                throw new DBException("Invalid partition configuration: " + PROP_KEY_COUNT + 
                                    " must be specified and greater than 0 (integer)");
            }
        } else {
            throw new DBException("Partition configuration is required. Please specify: " + PROP_KEY_COUNT + 
                                " (must be greater than 0, integer)");
        }
        
        if (debug) {
            System.out.println("Partition config: startTs=" + partitionStartTs + 
                             ", durationMs=" + partitionDurationMs + 
                             ", count=" + partitionCount +
                             ", keyCount=" + keyCount);
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
            config.set(HBASE_OCEANBASE_PASSWORD, props.getProperty(HBASE_OCEANBASE_PASSWORD));
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
            config.set(HBASE_OCEANBASE_SYS_PASSWORD, props.getProperty(HBASE_OCEANBASE_SYS_PASSWORD));
            config.set(HBASE_OCEANBASE_FULL_USER_NAME, props.getProperty(HBASE_OCEANBASE_FULL_USER_NAME));
            config.set(HBASE_OCEANBASE_PASSWORD, props.getProperty(HBASE_OCEANBASE_PASSWORD));
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
     * 将零填充的字符串转换为long，加上指定值，再转换回零填充字符串
     * @param paddedKey 零填充的字符串，如"00000028500000"
     * @param increment 要加上的值
     * @param paddingLength 填充长度
     * @return 转换后的零填充字符串
     */
    private String incrementPaddedKey(String paddedKey, long increment, int paddingLength) {
        long keyNum = Long.parseLong(paddedKey);
        long newKeyNum = keyNum + increment;
        return String.format("%0" + paddingLength + "d", newKeyNum);
    }

    /**
     * 将零填充的字符串转换为long，加上指定值，再转换回零填充字符串（使用配置的填充长度）
     * @param paddedKey 零填充的字符串，如"00000028500000"
     * @param increment 要加上的值
     * @return 转换后的零填充字符串
     */
    private String incrementPaddedKey(String paddedKey, long increment) {
        return incrementPaddedKey(paddedKey, increment, zeropadding);
    }


     /**
     * 基于ycsb_key生成唯一Key，确保Key数量为KeyCount，循环使用
     * @param key YCSB 生成ycsb_key，是一个整型字符串（递增id）
     * @return K 字符串，长度为 36
     */
     private String generateK(String key) {
        if (!enableTimeRangeTestMode) {
            return key;
        }

        long keyValue;
        try {
            keyValue = Long.parseLong(key.trim());
        } catch (NumberFormatException e) {
            // 如果不是数字，使用hashCode
            keyValue = Math.abs((long)key.hashCode());
        }
        
        // id = key % keyCount，确保key循环使用
        long KeyValue = keyValue % keyCount;
        
        return generateKeyPrefix(key) + generateTs(key);
    }

    private String generateKeyPrefix(String key) {
        long keyValue;
        try {
            keyValue = Long.parseLong(key.trim());
        } catch (NumberFormatException e) {
            // 如果不是数字，使用hashCode
            keyValue = Math.abs((long)key.hashCode());
        }
        
        // id = key % keyCount，确保key循环使用
        long KeyValue = keyValue % keyCount;
        return String.format(KEY_FORMAT, KeyValue, "");
    }

    /**
     * 基于key生成ts (timestamp)，确保均匀分布在所有range分区上
     * @param key YCSB 生成的 key，是一个整型字符串（递增id）
     * @return Timestamp 对象
     */
    private Long generateTs(String key) {
        // 如果未配置分区参数，使用当前系统时间
        if (!enableTimeRangeTestMode || partitionCount <= 0 || partitionDurationMs <= 0) {
            return System.currentTimeMillis();
        }
        
        // 将key转换为数值
        long keyValue;
        try {
            keyValue = Long.parseLong(key.trim());
        } catch (NumberFormatException e) {
            // 如果不是数字，使用hashCode
            keyValue = Math.abs((long)key.hashCode());
        }
        
        // 使用key本身作为hash值，确保不同key均匀分布
        // 为了更好的分布，可以使用一个简单的hash函数
        long hash = keyValue;
        
        // 计算分区索引：hash % partitionCount
        int partitionIndex = (int) (hash % partitionCount);
        
        // 计算在该分区内的偏移：hash % partitionDurationMs
        long offsetInPartition = hash % partitionDurationMs;
        
        // 计算最终的ts = 起始时间 + 分区索引 * 分区长度 + 分区内偏移
        long finalTs = partitionStartTs + partitionIndex * partitionDurationMs + offsetInPartition;
        
        if (debug) {
            System.out.println("generateTs: key=" + key + 
                             ", hash=" + hash + 
                             ", partitionIndex=" + partitionIndex + 
                             ", offsetInPartition=" + offsetInPartition + 
                             ", finalTs=" + finalTs);
        }
        
        return finalTs;
    }

    private long genRangePartStartTs(String key) {
        // 将key转换为数值
        long keyValue;
        try {
            keyValue = Long.parseLong(key.trim());
        } catch (NumberFormatException e) {
            // 如果不是数字，使用hashCode
            keyValue = Math.abs((long)key.hashCode());
        }
        
        // 使用key本身作为hash值，确保不同key均匀分布
        // 为了更好的分布，可以使用一个简单的hash函数
        long hash = keyValue;
        
        // 计算分区索引：hash % partitionCount
        int partitionIndex = (int) (hash % partitionCount);
        
        // 计算在该分区内的偏移：hash % partitionDurationMs
        long offsetInPartition = hash % partitionDurationMs;
        
        // 计算最终的ts = 起始时间 + 分区索引 * 分区长度
        long finalTs = partitionStartTs + partitionIndex * partitionDurationMs;
        
        if (debug) {
            System.out.println("generateTs: key=" + key + 
                             ", hash=" + hash + 
                             ", partitionIndex=" + partitionIndex + 
                             ", finalTs=" + finalTs);
        }
        return finalTs;
    }
    
    private long genRangePartEndTs(String key) {
        // 将key转换为数值
        long keyValue;
        try {
            keyValue = Long.parseLong(key.trim());
        } catch (NumberFormatException e) {
            // 如果不是数字，使用hashCode
            keyValue = Math.abs((long)key.hashCode());
        }
        
        // 使用key本身作为hash值，确保不同key均匀分布
        // 为了更好的分布，可以使用一个简单的hash函数
        long hash = keyValue;
        
        // 计算分区索引：hash % partitionCount
        int partitionIndex = (int) (hash % partitionCount);
        
        // 计算在该分区内的偏移：hash % partitionDurationMs
        long offsetInPartition = hash % partitionDurationMs;
        
        // 计算最终的ts = 起始时间 + 分区索引 * 分区长度 + 分区内偏移
        long finalTs = partitionStartTs + (partitionIndex + 1)* partitionDurationMs - 1;
        
        if (debug) {
            System.out.println("generateTs: key=" + key + 
                             ", hash=" + hash + 
                             ", partitionIndex=" + partitionIndex + 
                             ", finalTs=" + finalTs);
        }
        return finalTs;
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
            if (debug) {
                System.out.println("Doing read from HBase columnfamily " + columnFamily);
                System.out.println("Doing read for key: " + key);
            }
            Get g = new Get(Bytes.toBytes(generateK(key)));
            // if (enableTimeRangeTestMode) {
            //     g.setTimeRange(genRangePartStartTs(key), genRangePartEndTs(key));
            // }
            if (fields == null) {
                g.addFamily(columnFamilyBytes);
            } else {
                for (String field : fields) {
                    g.addColumn(columnFamilyBytes, Bytes.toBytes(field));
                }
            }
            r = connection.getTable(TableName.valueOf(tableName)).get(g);
        } catch (IOException e) {
            System.err.println("Error doing get: " + e);
            return SERVICE_UNAVAILABLE;
        } catch (ConcurrentModificationException e) {
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
            if (enableTimeRangeTestMode) {
                scan.setStartRow(Bytes.toBytes(generateKeyPrefix(startkey) + "0"));
                scan.setStopRow(Bytes.toBytes(generateKeyPrefix(startkey) + "9"));
                // scan.setTimeRange(genRangePartStartTs(startkey), genRangePartEndTs(startkey));
            } else {
                scan.setMaxVersions(1);
                scan.setStartRow(Bytes.toBytes(generateK(startkey)));
                scan.setStopRow(Bytes.toBytes(incrementPaddedKey(generateK(startkey), recordcount)));
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

    /**
     * 更新操作，目前不支持批量接口测试
     * @param table table
     * @param key key
     * @param values values
     * @return ans
     */
    @Override
    public Status update(String table, String key, Map<String, ByteIterator> values) {
        if (debug) {
            System.out.println("Setting up put for key: " + key);// NOPMD
        }
        Put p = new Put(Bytes.toBytes(generateK(key)));
        for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
            if (debug) {
                System.out.println("Adding field/value " + entry.getKey() + "/" + entry.getValue()// NOPMD
                                   + " to put request");// NOPMD
            }
            if (enableTimeRangeTestMode) {
                p.addColumn(columnFamilyBytes, Bytes.toBytes(entry.getKey()), generateTs(key), entry.getValue().toArray());
            } else {
                p.addColumn(columnFamilyBytes, Bytes.toBytes(entry.getKey()), entry.getValue().toArray());
            }
        }
        try {
            connection.getTable(TableName.valueOf(tableName)).put(p);
            if (debug) {
                System.out.println("put success");
            }
        } catch (IOException e) {
            if (debug) {
                System.err.println("Error doing put: " + e);// NOPMD
            }
            e.printStackTrace();
            return SERVICE_UNAVAILABLE;
        } catch (ConcurrentModificationException e) {
            //do nothing for now...hope this is rare
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
        Delete delete = new Delete(Bytes.toBytes(key));
        delete.addFamily(columnFamilyBytes);
        try {
            connection.getTable(TableName.valueOf(tableName)).delete(delete);
            return OK;
        } catch (IOException e) {
            if (debug) {
                System.err.println("Error doing delete: " + e);// NOPMD
            }
            return ERROR;
        }
    }

    @Override
    public Status batchPut(String table, Map<String, Map<String, ByteIterator>> valuesMap) {
        List<Put> putList = new ArrayList<>();
        valuesMap.forEach((key, values) -> {
            Put put = new Put(generateK(key).getBytes());
            values.forEach((k, v) -> {
                if (enableTimeRangeTestMode) {
                    put.addColumn(columnFamilyBytes, k.getBytes(), generateTs(key), v.toArray());
                } else {
                    put.addColumn(columnFamilyBytes, k.getBytes(), v.toArray());
                }
            });
            putList.add(put);
        });
        try {
            connection.getTable(TableName.valueOf(tableName)).put(putList);
        } catch (IOException e) {
            e.printStackTrace();
            if (debug) {
                System.err.println("Error doing batch: " + e);
            }
            return Status.ERROR;
        }
        return Status.OK;
    }

    @Override
    public Status batchRead(String table, Set<String> fields, Map<String, Map<String, ByteIterator>> valuesMap) {
        List<Get> getList = new ArrayList<>();
        valuesMap.keySet().forEach(key -> {
            Get get = new Get(generateK(key).getBytes());
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
                        System.out.println("Result for key: " + getList.get(i).getRow() + " is empty");
                    }
                    continue;
                }
                while (res[i].advance()) {
                    final Cell c = res[i].current();
                    Map<String, ByteIterator> result = new HashMap<>();
                    result.put(Bytes.toString(CellUtil.cloneQualifier(c)),
                            new ByteArrayByteIterator(CellUtil.cloneValue(c)));
                    valuesMap.put(Bytes.toString(CellUtil.cloneRow(c)), result);
                    if (debug) {
                        System.out.println(
                                "Result for field: " + Bytes.toString(CellUtil.cloneQualifier(c))
                                        + " is: " + Bytes.toString(CellUtil.cloneValue(c)));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Status.ERROR;
        }
        return Status.OK;
    }
}