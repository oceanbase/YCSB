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
import com.alipay.oceanbase.hbase.OHTableClient;
import com.alipay.oceanbase.rpc.property.Property;
import site.ycsb.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
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
    public static final String VERSION       = "hbase.oceanbase.version";

    private String             columnFamily;
    private byte[]             columnFamilyBytes;
    private String             tableName;
    public boolean             debug         = false;
    private OHTableClient client = null;
    private int                zeropadding;
    private Long               version       = null;

    @Override
    public void cleanup() throws DBException {
        if (client != null) {
            try {
                client.close();
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
        columnFamily = getProperties().getProperty(COLUMN_FAMILY);
        tableName = getProperties().getProperty(TABLE);
        columnFamilyBytes = Bytes.toBytes(columnFamily);
        
        String v = getProperties().getProperty(VERSION);
        if (isNotBlank(v)) {
            try {
                version = Long.parseLong(v);
            } catch (NumberFormatException e) {
                System.err.println("Invalid version format: " + v);
            }
        }

        System.out.println("columnFamily: " + columnFamily + ", table: " + tableName + ", version: " + version + ", debug: " + debug);
        Configuration config = HBaseConfiguration.create();
        initObkvConfig(config);
        client = new OHTableClient(tableName, config);
        try {
            client.init();
        } catch (Exception e) {
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

        // Set hardcoded connection stability parameters
        config.set("rpc.connect.timeout", "10000");
        config.set("metadata.refresh.lock.timeout", "20000");
        config.set("rs.list.acquire.connect.timeout", "10000");
        config.set("rs.list.acquire.read.timeout", "10000");
        config.set("table.entry.acquire.connect.timeout", "10000");
        config.set("table.entry.acquire.socket.timeout", "10000");
        config.set("table.entry.refresh.lock.timeout", "10000");
        config.set("rpc.login.timeout", "10000");
        config.set("connection.max.expired.time", "10000");
        config.set("runtime.max.wait", "10000");

        zeropadding = Integer.parseInt(getProperties().getProperty("zeropadding", "12"));   
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
            r = client.get(g);
        } catch (IOException e) {
            System.err.println("Error doing get: " + e);
            return SERVICE_UNAVAILABLE;
        } catch (ConcurrentModificationException e) {
            return SERVICE_UNAVAILABLE;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("get failed");
            return ERROR;
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
            scan.setStartRow(Bytes.toBytes(startkey));
            scan.setStopRow(Bytes.toBytes(incrementPaddedKey(startkey, recordcount)));
        
            if (fields == null) {
                scan.addFamily(columnFamilyBytes);
            } else {
                for (String field : fields) {
                    scan.addColumn(columnFamilyBytes, Bytes.toBytes(field));
                }
            }

            scanner = client.getScanner(scan);
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
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("scan failed");
            return ERROR;
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
            if (version != null) {
                p.addColumn(columnFamilyBytes, Bytes.toBytes(entry.getKey()), version, entry.getValue().toArray());
            } else {
                p.addColumn(columnFamilyBytes, Bytes.toBytes(entry.getKey()), entry.getValue().toArray());
            }
            if (debug) {
                System.out.println("Adding field/value " + entry.getKey() + "/" + entry.getValue()
                                   + " to put request");
            }
        }
        try {
            client.put(p);
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
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("put failed");
            return ERROR;
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
            client.delete(delete);
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
            values.forEach((k, v) -> {
                if (version != null) {
                    put.addColumn(columnFamilyBytes, k.getBytes(), version, v.toArray());
                } else {
                    put.addColumn(columnFamilyBytes, k.getBytes(), v.toArray());
                }
            });
            putList.add(put);
        });
        try {
            client.put(putList);
        } catch (IOException e) {
            if (debug) {
                System.err.println("Error doing batch: " + e);
            }
            return Status.ERROR;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("batchPut failed");
            return ERROR;
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
            res = client.get(getList);
            if (res == null || res.length == 0) {
                if (debug) {
                    System.out.println("Result is empty");
                }
                return Status.OK;
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