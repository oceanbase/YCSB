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

package com.oceanbase.obkv.table.ycsb;
import com.alipay.oceanbase.rpc.mutation.result.BatchOperationResult;
import site.ycsb.*;
import java.util.*;
import static site.ycsb.Status.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.sql.Timestamp;
import com.alipay.oceanbase.rpc.ObTableClient;
import com.alipay.oceanbase.rpc.mutation.InsertOrUpdate;
import com.alipay.oceanbase.rpc.get.Get;
import com.alipay.oceanbase.rpc.property.Property;
import com.alipay.oceanbase.rpc.mutation.BatchOperation;
import com.alipay.oceanbase.rpc.mutation.MutationFactory;
import com.alipay.oceanbase.rpc.mutation.Row;
import com.alipay.oceanbase.rpc.mutation.Mutation;
import static com.alipay.oceanbase.rpc.mutation.MutationFactory.colVal;
import static com.alipay.oceanbase.rpc.mutation.MutationFactory.row;
import static com.alipay.oceanbase.rpc.mutation.MutationFactory.query;
import com.alipay.oceanbase.rpc.table.api.TableQuery;
import com.alipay.oceanbase.rpc.stream.QueryResultSet;
import com.alipay.oceanbase.rpc.table.api.TableQuery;
import com.alipay.oceanbase.rpc.protocol.payload.impl.ObObj;
 
 public class ObTableClientDBRK extends DB {
    public static final String PROP_KEY_ODP_MODE                = "obkv.isOdpMode";
    public static final String PROP_KEY_ODP_ADDR                = "obkv.odpAddr";
    public static final String PROP_KEY_ODP_PORT                = "obkv.odpPort";
    public static final String PROP_KEY_DATABASE                = "obkv.database";
  
    public static final String PROP_KEY_FULL_USER_NAME          = "obkv.fullUserName";
    public static final String PROP_KEY_CONFIG_URL              = "obkv.configUrl";
    public static final String PROP_KEY_PASSWORD                = "obkv.password";
    public static final String PROP_KEY_SYS_USER_NAME           = "obkv.sysUserName";
    public static final String PROP_KEY_SYS_PASSWORD            = "obkv.sysPassword";
  
    public static final String PROP_KEY_DEBUG                   = "obkv.debug";
    public static final String PROP_KEY_BATCH_THREAD_COUNT      = "obkv.batch.threadCount";
    public static final String PROP_KEY_INSERT_TYPE             = "obkv.insertType";
    public static final String PROP_KEY_UPDATE_TYPE             = "obkv.updateType";
    public static final String PROP_KEY_BATCH_PUT_TYPE          = "obkv.batchPutType";
    public static final String PROP_KEY_PARTITION_START_TS      = "obkv.rangePartitionStartTs";
    public static final String PROP_KEY_PARTITION_DURATION_MS   = "obkv.rangePartitionDurationMs";
    public static final String PROP_KEY_PARTITION_COUNT         = "obkv.rangePartitionCount";
    public static final String PROP_KEY_PREFIX_COUNT             = "obkv.prefixCount";

    private ObTableClient client = null;
    private boolean debug = false;
    private int threadCount = 3;
    private ExecutorService executorService;
    private String insertType;
    private String updateType;
    private String batchPutType;
    private long partitionStartTs = 0;  // 第一个range分区的起始时间戳（毫秒）
    private long partitionDurationMs = 0;  // 每个range分区的时间长度（毫秒）
    private int partitionCount = 0;  // 一级range分区的数量
    private int prefixCount = 10000;  // 前缀ID的总数
    private String idColumn = "ycsb_id";
    private String tsColumn = "ycsb_ts";

    @Override
    public void cleanup() throws DBException {
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
 
    /**
     * 初始化，可以从 java 启动参数中传入
    * @throws DBException exception
    */
    public void init() throws DBException {
        boolean isOdpMode = false;
        Properties props = getProperties();
        client = new ObTableClient();
        if (props.getProperty(PROP_KEY_ODP_MODE) != null) {
            isOdpMode = Boolean.parseBoolean(props.getProperty(PROP_KEY_ODP_MODE));
            client.setOdpMode(isOdpMode);
        }
        if (isOdpMode) { 
            client.setFullUserName(props.getProperty(PROP_KEY_FULL_USER_NAME));
            client.setOdpAddr(props.getProperty(PROP_KEY_ODP_ADDR));
            client.setOdpPort(Integer.parseInt(props.getProperty(PROP_KEY_ODP_PORT)));
            client.setDatabase(props.getProperty(PROP_KEY_DATABASE));
            client.setPassword(props.getProperty(PROP_KEY_PASSWORD, ""));
        } else {
            client.setFullUserName(props.getProperty(PROP_KEY_FULL_USER_NAME));
            client.setParamURL(props.getProperty(PROP_KEY_CONFIG_URL));
            client.setPassword(props.getProperty(PROP_KEY_PASSWORD, ""));
            client.setSysUserName(props.getProperty(PROP_KEY_SYS_USER_NAME));
            client.setSysPassword(props.getProperty(PROP_KEY_SYS_PASSWORD, ""));
        }

        // Some other useful property
        for (Property property : Property.values()) {
            String value = props.getProperty(property.getKey());
            if (value != null) {
                client.addProperty(property.getKey(), value);
            }
        }

        insertType = props.getProperty(PROP_KEY_INSERT_TYPE, "put").toLowerCase();
        updateType = props.getProperty(PROP_KEY_UPDATE_TYPE, "put").toLowerCase();
        batchPutType = props.getProperty(PROP_KEY_BATCH_PUT_TYPE, "put").toLowerCase();

        // debug
        if (props.getProperty(PROP_KEY_DEBUG) != null) {
            debug = Boolean.parseBoolean(props.getProperty(PROP_KEY_DEBUG));
        }

        if (debug) {
            System.out.println("isOdpMode: " + isOdpMode);
            System.out.println("insertType: " + insertType);
            System.out.println("updateType: " + updateType);
            System.out.println("batchPutType: " + batchPutType);
        }

        // thread count
        if (props.getProperty(PROP_KEY_BATCH_THREAD_COUNT) != null) {
            threadCount = Integer.parseInt(props.getProperty(PROP_KEY_BATCH_THREAD_COUNT));
        }
        
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
        if (props.getProperty(PROP_KEY_PREFIX_COUNT) != null) {
            prefixCount = Integer.parseInt(props.getProperty(PROP_KEY_PREFIX_COUNT));
            if (prefixCount <= 0) {
                throw new DBException("Invalid partition configuration: " + PROP_KEY_PREFIX_COUNT + 
                                    " must be specified and greater than 0 (integer)");
            }
        } else {
            throw new DBException("Partition configuration is required. Please specify: " + PROP_KEY_PREFIX_COUNT + 
                                " (must be greater than 0, integer)");
        }
        
        if (debug) {
            System.out.println("Partition config: startTs=" + partitionStartTs + 
                             ", durationMs=" + partitionDurationMs + 
                             ", count=" + partitionCount +
                             ", prefixCount=" + prefixCount);
        }
        
        executorService = Executors.newFixedThreadPool(threadCount);
        client.setRuntimeBatchExecutor(executorService);

        try {
            client.init();

        } catch (Exception e) {
            throw new DBException(e.toString());
        }
    }
 
    /**
     * 读取数据测试
     * @param table table
     * @param key key
     * @param fields fields
     * @param result result
     * @return ans
     */
    @Override
    public Status read(String table, String key, Set<String> fields,
                        Map<String, ByteIterator> result) {
        try {
            // 基于key生成 id、ts
            String id = generateId(key);
            Timestamp ts = generateTs(key);
            
            // 注册主键元素
            client.addRowKeyElement(table, new String[]{idColumn, tsColumn});
            Object[] keys = new Object[]{id, ts};
            String[] fs = new String[]{};
            if (fields != null) {
                fs = fields.toArray(new String[]{});
            }
            if (debug) {
                System.out.println("read: keys=" + Arrays.toString(keys) + ", fs=" + Arrays.toString(fs));
            }
            Iterator i$ = client.get(table, keys, fs).entrySet().iterator();
            while (i$.hasNext()) {
                Map.Entry<String, Object> entry = (Map.Entry) i$.next();
                result.put(entry.getKey(), new StringByteIterator(entry.getValue().toString()));
                if (debug) {
                    System.out.println("read result: {" + entry.getKey() + ": " + entry.getValue().toString() + "}");
                }
            }

            return result.isEmpty() ? Status.NOT_FOUND : Status.OK;
        } catch (Exception e) {
            e.printStackTrace();
            return Status.ERROR;
        }
    }

    /**
     * 基于key生成唯一id，确保id数量为prefixCount，循环使用
     * @param key YCSB 生成的 key，是一个整型字符串（递增id）
     * @return id 字符串，长度为 36
     */
    private String generateId(String key) {
        long keyValue;
        try {
            keyValue = Long.parseLong(key.trim());
        } catch (NumberFormatException e) {
            // 如果不是数字，使用hashCode
            keyValue = Math.abs((long)key.hashCode());
        }
        
        // id = key % prefixCount，确保id循环使用
        long idValue = keyValue % prefixCount;
        
        return String.valueOf(idValue);
    }

    /**
     * 基于key生成ts (timestamp)，确保均匀分布在所有range分区上
     * @param key YCSB 生成的 key，是一个整型字符串（递增id）
     * @return Timestamp 对象
     */
    private Timestamp generateTs(String key) {
        // 如果未配置分区参数，使用当前系统时间
        if (partitionCount <= 0 || partitionDurationMs <= 0) {
            return new Timestamp(System.currentTimeMillis());
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
        
        return new Timestamp(finalTs);
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
 
        try {
            client.addRowKeyElement(table, new String[]{idColumn, tsColumn});
            TableQuery query = client.query(table);
            String id = generateId(startkey);
            query.addScanRange(new Object[] { id, ObObj.getMin()}, new Object[] { id, ObObj.getMax() });
            query.limit(recordcount);
            if (fields != null) {
                query.select(fields.toArray(new String[]{}));
            }
            QueryResultSet resultSet = query.asyncExecute();
            while (resultSet.next()) {
                Map<String, Object> row = resultSet.getRow();
                HashMap<String, ByteIterator> rowResult = new HashMap<String, ByteIterator>();
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    rowResult.put(key, new StringByteIterator(value.toString()));
                }
                result.add(rowResult);
                if (debug) {
                    System.out.println("scan result: id=" + id +  ", data=" + rowResult);
                }
            }
            if (result.isEmpty()) {
                if (debug) {
                    System.out.println("scan result: id=" + id +  ", data=null");
                }
                return Status.NOT_FOUND;
            }
            return Status.OK;
        } catch (Exception e) {
            e.printStackTrace();
            return Status.ERROR;
        }
    }
    /**
     * 更新操作
     * @param table table
     * @param key key
     * @param values values
     * @return ans
     */
    @Override
    public Status update(String table, String key, Map<String, ByteIterator> values) {
        // 基于key生成 pmid、ts
        String id = generateId(key);
        Timestamp ts = generateTs(key);
        
        // 构建复合主键 (pmid, ts)
        Row rowKey = row(colVal(idColumn, id), colVal(tsColumn, ts));
        
        // 构建数据行，只更新value字段
        Row row = row();
        for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
            row.add(entry.getKey(), entry.getValue().toString());
        }
        
        if (debug) {
            System.out.println("update: id=" + id + ", ts=" + ts);
        }
        
        try {
            // 注册主键元素
            client.addRowKeyElement(table, new String[]{idColumn, tsColumn});
            
            switch (updateType) {
                case "update":
                    client.update(table).setRowKey(rowKey).addMutateRow(row).execute();
                    break;
                case "insertup":
                    client.insertOrUpdate(table).setRowKey(rowKey).addMutateRow(row).execute();
                    break;
                case "put":
                    client.put(table).setRowKey(rowKey).addMutateRow(row).execute();
                    break;
                default:
                    throw new DBException("update type: " + updateType + " is not supported");
            }
            return Status.OK;
        } catch (Exception e) {
            e.printStackTrace();
            return Status.ERROR;
        }
    }
 
     @Override
     public Status insert(String table, String key, Map<String, ByteIterator> values) {
        // 基于key生成 id、ts
        String id = generateId(key);
        Timestamp ts = generateTs(key);

        // 构建复合主键 (id, ts)
        Row rowKey = row(colVal(idColumn, id), colVal(tsColumn, ts));
        
        // 构建数据行
        Row row = row();
        for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
            row.add(entry.getKey(), entry.getValue().toString());
        }

        if (debug) {
            System.out.println("insert: id=" + id + ", ts=" + ts);
        }

        try {
            // 注册主键元素
            client.addRowKeyElement(table, new String[]{idColumn, tsColumn});
            
            switch (insertType) {
                case "insert":
                    client.insert(table).setRowKey(rowKey).addMutateRow(row).execute();
                    break;
                case "insertup":
                    client.insertOrUpdate(table).setRowKey(rowKey).addMutateRow(row).execute();
                    break;
                case "put":
                    client.put(table).setRowKey(rowKey).addMutateRow(row).execute();
                    break;
                default:
                    throw new DBException("insert type " + insertType + " is not supported");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Status.ERROR;
        }

        return OK;
     }
 
     /**
      * 删除接口，目前不支持批量删除测试
      * @param table table
      * @param key key
      * @return ans
      */
    @Override
    public Status delete(String table, String key) {
        return Status.NOT_IMPLEMENTED;
    }
 
    @Override
    public Status batchPut(String table, Map<String, Map<String, ByteIterator>> valuesMap) {
        List<Mutation> mutationList = new ArrayList<>();
        BatchOperation batchOperation = client.batchOperation(table);
        
        // 注册主键元素
        try {
            client.addRowKeyElement(table, new String[]{idColumn, tsColumn});
        } catch (Exception e) {
            // 如果已经注册过，忽略错误
            if (debug) {
                System.out.println("Row key element already registered or error: " + e.getMessage());
            }
        }

        valuesMap.forEach((k, v) -> {
            // 将 Map<String, ByteIterator> 转换为 HashMap<String, ByteIterator>
            HashMap<String, ByteIterator> values = new HashMap<>();
            if (v != null) {
                for (Map.Entry<String, ByteIterator> entry : v.entrySet()) {
                    values.put(entry.getKey(), entry.getValue());
                }
            }

            // 基于key生成 pmid、ts、value
            String id = generateId(k);
            Timestamp ts = generateTs(k);

            if (debug) {
                System.out.println("batchPut: id=" + id + ", ts=" + ts);
            }

            // 构建复合主键 (pmid, ts)
            Row rowKey = row(colVal(idColumn, id), colVal(tsColumn, ts));
            
            // 构建数据行，只包含 value 字段
            Row row = row();
            for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
                row.add(entry.getKey(), entry.getValue().toString());
            }

            switch (batchPutType) {
                case "insert":
                    mutationList.add(MutationFactory.insert().setRowKey(rowKey).addMutateRow(row));
                    break;
                case "insertup":
                    mutationList.add(MutationFactory.insertOrUpdate().setRowKey(rowKey).addMutateRow(row));
                    break;
                case "put":
                    mutationList.add(MutationFactory.put().setRowKey(rowKey).addMutateRow(row));
                    break;
                default:
                    break;
            }
        });
        try {
            if (!mutationList.isEmpty()) {
                batchOperation.addOperation(mutationList);
            }
            batchOperation.execute();
        } catch (Exception e) {
            System.err.println("batch put error: batchPutType=" + batchPutType);
            e.printStackTrace();
            return Status.ERROR;
        }
        return Status.OK;
    }
 
    @Override
    public Status batchRead(String table, Set<String> fields, Map<String, Map<String, ByteIterator>> valuesMap) {
        try {
            // 注册主键元素
            client.addRowKeyElement(table, new String[]{idColumn, tsColumn});
            
            // 创建 Get 操作列表
            List<TableQuery> getOps = new ArrayList<>();
            List<String> keys = new ArrayList<>(valuesMap.keySet());
            
            // 为每个 key 创建 Get 操作
            for (String key : keys) {
                // 基于key生成 id、ts
                String id = generateId(key);
                Timestamp ts = generateTs(key);
                
                // 构建复合主键
                Row rowKey = row(colVal(idColumn, id), colVal(tsColumn, ts));
                List<String> selectFields = new ArrayList<>();
                selectFields.add(idColumn);
                selectFields.add(tsColumn);
                if (fields != null && !fields.isEmpty()) {
                    selectFields.addAll(fields);
                }
                // 创建 Get 操作，select 所有列
                TableQuery getOp = query().setRowKey(rowKey).select(selectFields.toArray(new String[]{}));
                getOps.add(getOp);
            }
            
            // 批量执行
            BatchOperation batchOperation = client.batchOperation(table);
            batchOperation.addOperation(getOps.toArray(new TableQuery[0]));
            BatchOperationResult batchResult = batchOperation.execute();
            
            if (batchResult == null || batchResult.size() == 0) {
                return Status.NOT_FOUND;
            }
            
            // 处理结果
            for (int i = 0; i < batchResult.size(); i++) {
                Row row = batchResult.get(i).getOperationRow();
                if (row != null) {
                    Map<String, Object> rowData = row.getMap();
                    if (rowData != null && !rowData.isEmpty()) {
                        String key = keys.get(i);
                        HashMap<String, ByteIterator> rowResult = new HashMap<>();
                        
                        // 根据 fields 参数过滤结果
                        if (fields != null && !fields.isEmpty()) {
                            for (String field : fields) {
                                if (rowData.containsKey(field)) {
                                    rowResult.put(field, new StringByteIterator(rowData.get(field).toString()));
                                }
                            }
                        } else {
                            // 如果 fields 为 null，返回所有字段
                            for (Map.Entry<String, Object> entry : rowData.entrySet()) {
                                rowResult.put(entry.getKey(), new StringByteIterator(entry.getValue().toString()));
                            }
                        }
                        
                        // 将结果放入 valuesMap
                        valuesMap.put(key, rowResult);
                        
                        if (debug) {
                            System.out.println("batchRead result[" + i + "]: key=" + key + ", data=" + rowResult);
                        }
                    }
                }
            }
            
            return Status.OK;
        } catch (Exception e) {
            e.printStackTrace();
            return Status.ERROR;
        }
    }
 }