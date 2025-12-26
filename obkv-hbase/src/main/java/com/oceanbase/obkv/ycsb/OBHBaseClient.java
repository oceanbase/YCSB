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
import org.apache.hadoop.hbase.util.Bytes;
import java.io.IOException;
import java.util.*;
import static com.alipay.oceanbase.hbase.constants.OHConstants.*;
import static site.ycsb.Status.*;
import static org.apache.commons.lang.StringUtils.isNotBlank;

public class OBHBaseClient extends DB {
    public static final String COLUMN_FAMILY = "hbase.oceanbase.columnFamily";
    public static final String TABLE         = "hbase.oceanbase.table";
    public static final String HBASE_MASTER  = "hbase.master";
    public static final String ZOOKEEPER_QUORUM = "hbase.zookeeper.quorum";
    public static final String ZOOKEEPER_CLIENT_PORT = "hbase.zookeeper.property.clientPort";
    public static final String HBASE_CLIENT_IPC_POOL_SIZE = "hbase.client.ipc.pool.size";
    private String             columnFamily;
    private byte[]             columnFamilyBytes;
    private String             tableName;
    public boolean             debug         = false;
    private Connection connection = null;
    private int                zeropadding;
    private boolean            isObkv = true;

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
        debug = Boolean.parseBoolean(getProperties().getProperty("debug"));
        isObkv = Boolean.parseBoolean(getProperties().getProperty("isObkv"));
        columnFamily = getProperties().getProperty(COLUMN_FAMILY);
        tableName = getProperties().getProperty(TABLE);
        columnFamilyBytes = Bytes.toBytes(columnFamily);
        System.out.println("columnFamily: " + columnFamily);
        System.out.println("table: " + tableName);
        System.out.println("debug: " + debug);
        System.out.println("isObkv: " + isObkv);
        Configuration config = HBaseConfiguration.create();
        if (isObkv) {
            config.set(ClusterConnection.HBASE_CLIENT_CONNECTION_IMPL, "com.alipay.oceanbase.hbase.util.OHConnectionImpl");
            initObkvConfig(config);
        } else {
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
            testNetworkConnectivity(getProperties().getProperty(ZOOKEEPER_QUORUM, "127.0.0.1"), 
                                   Integer.parseInt(getProperties().getProperty(ZOOKEEPER_CLIENT_PORT, "2181")));
        }
        try {
            System.out.println("Creating HBase connection...");
            connection = ConnectionFactory.createConnection(config);
            System.out.println("Connection created successfully");
            
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
        // Some other useful property
        for (Property property : Property.values()) {
            String value = props.getProperty(property.getKey());
            if (value != null) {
                config.set(property.getKey(), value);
            }
        }

        zeropadding = Integer.parseInt(getProperties().getProperty("zeropadding", "12"));   
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
     * 测试ZooKeeper连接
     */
    private void testZooKeeperConnection(String quorum, String port) {
        try {
            System.out.println("Testing ZooKeeper connection to " + quorum + ":" + port);
            org.apache.zookeeper.ZooKeeper zk = new org.apache.zookeeper.ZooKeeper(
                quorum + ":" + port, 5000, new org.apache.zookeeper.Watcher() {
                    @Override
                    public void process(org.apache.zookeeper.WatchedEvent event) {
                        System.out.println("ZooKeeper event: " + event.getType());
                    }
                });
            
            // 等待连接建立
            int retries = 0;
            while (zk.getState() != org.apache.zookeeper.ZooKeeper.States.CONNECTED && retries < 10) {
                Thread.sleep(1000);
                retries++;
                System.out.println("ZooKeeper connection attempt " + retries + ", state: " + zk.getState());
            }
            
            if (zk.getState() == org.apache.zookeeper.ZooKeeper.States.CONNECTED) {
                System.out.println("ZooKeeper connection successful");
                // 测试基本操作
                zk.exists("/", false);
                System.out.println("ZooKeeper root path accessible");
            } else {
                System.err.println("ZooKeeper connection failed, state: " + zk.getState());
            }
            
            zk.close();
        } catch (Exception e) {
            System.err.println("ZooKeeper connection test failed: " + e.getMessage());
            e.printStackTrace();
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
            Get g = new Get(Bytes.toBytes(key));
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

        Scan scan = new Scan(Bytes.toBytes(startkey));
        scan.setCaching(recordcount);
        scan.setMaxVersions(1);
        // 计算结束key并设置scan范围
        String endKeyStr = incrementPaddedKey(startkey, recordcount);
        scan.setStopRow(Bytes.toBytes(endKeyStr));
       
        if (fields == null) {
            scan.addFamily(columnFamilyBytes);
        } else {
            for (String field : fields) {
                scan.addColumn(columnFamilyBytes, Bytes.toBytes(field));
            }
        }

        ResultScanner scanner = null;
        try {
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
        Put p = new Put(Bytes.toBytes(key));
        for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
            if (debug) {
                System.out.println("Adding field/value " + entry.getKey() + "/" + entry.getValue()// NOPMD
                                   + " to put request");// NOPMD
            }
            p.addColumn(columnFamilyBytes, Bytes.toBytes(entry.getKey()), entry.getValue().toArray());
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
            Put put = new Put(key.getBytes());
            values.forEach((k, v) -> put.addColumn(columnFamilyBytes, k.getBytes(), v.toArray()));
            putList.add(put);
        });
        try {
            connection.getTable(TableName.valueOf(tableName)).put(putList);
        } catch (IOException e) {
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
            Get get = new Get(key.getBytes());
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
                    Map<String, ByteIterator> result = valuesMap.get(Bytes.toString(CellUtil.cloneRow(c)));
                    result.put(Bytes.toString(CellUtil.cloneQualifier(c)),
                            new ByteArrayByteIterator(CellUtil.cloneValue(c)));
                    if (debug) {
                        System.out.println(
                                "Result for field: " + Bytes.toString(CellUtil.cloneQualifier(c))
                                        + " is: " + Bytes.toString(CellUtil.cloneValue(c)));
                    }
                }
            }
        } catch (Exception e) {
            if (debug) {
                System.err.println("Error doing batch read: " + e);
            }
            return Status.ERROR;
        }
        return Status.OK;
    }
}