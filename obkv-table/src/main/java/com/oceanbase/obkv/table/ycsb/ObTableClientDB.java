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
import com.alipay.oceanbase.rpc.ObTableClient;
import com.alipay.oceanbase.rpc.property.Property;
import com.alipay.oceanbase.rpc.mutation.BatchOperation;
import com.alipay.oceanbase.rpc.mutation.MutationFactory;
import com.alipay.oceanbase.rpc.mutation.Row;
import com.alipay.oceanbase.rpc.mutation.Mutation;
import static com.alipay.oceanbase.rpc.mutation.MutationFactory.colVal;
import static com.alipay.oceanbase.rpc.mutation.MutationFactory.row;
import com.alipay.oceanbase.rpc.stream.QueryResultSet;
import com.alipay.oceanbase.rpc.table.api.TableQuery;
import com.alipay.oceanbase.rpc.protocol.payload.impl.ObObj;
 
 public class ObTableClientDB extends DB {
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
    public static final String PROP_KEY_MAX_KEY                 = "obkv.maxKey";

    private ObTableClient client = null;
    private boolean debug = false;
    private int threadCount = 3;
    private ExecutorService executorService;
    private String insertType;
    private String updateType;
    private String batchPutType;
    private int zeropadding;
    private long maxKey = Long.MAX_VALUE;

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
        updateType = props.getProperty(PROP_KEY_UPDATE_TYPE, "update").toLowerCase();
        batchPutType = props.getProperty(PROP_KEY_BATCH_PUT_TYPE, "put").toLowerCase();

        // debug
        if (props.getProperty(PROP_KEY_DEBUG) != null) {
            debug = Boolean.parseBoolean(props.getProperty(PROP_KEY_DEBUG));
        }

        // maxKey
        String maxKeyStr = props.getProperty(PROP_KEY_MAX_KEY);
        if (maxKeyStr != null && !maxKeyStr.trim().isEmpty()) {
            try {
                maxKey = Long.parseLong(maxKeyStr.trim());
                if (maxKey <= 0) {
                    throw new DBException("Invalid maxKey configuration: " + PROP_KEY_MAX_KEY +
                                        " must be greater than 0, got: " + maxKey);
                }
            } catch (NumberFormatException e) {
                throw new DBException("Invalid maxKey configuration: " + PROP_KEY_MAX_KEY +
                                    " must be a valid long integer, got: " + maxKeyStr, e);
            }
        }

        if (debug) {
            System.out.println("isOdpMode: " + isOdpMode);
            System.out.println("insertType: " + insertType);
            System.out.println("updateType: " + updateType);
            System.out.println("batchPutType: " + batchPutType);
            System.out.println("maxKey: " + maxKey);
        }

        // thread count
        if (props.getProperty(PROP_KEY_BATCH_THREAD_COUNT) != null) {
            threadCount = Integer.parseInt(props.getProperty(PROP_KEY_BATCH_THREAD_COUNT));
        }
        zeropadding = Integer.parseInt(getProperties().getProperty("zeropadding", "12"));   
        executorService = Executors.newFixedThreadPool(threadCount);
        client.setRuntimeBatchExecutor(executorService);

        try {
            client.init();

        } catch (Exception e) {
            throw new DBException(e.toString());
        }
    }

    /**
     * 处理默认模式的key，根据maxKey进行取余，确保key在分区范围内
     * @param coreKey core生成的key
     * @return 处理后的key
     */
    private String processKeyForDefaultMode(String coreKey) {
        if (maxKey < Long.MAX_VALUE) {
            try {
                long keyValue = Long.parseLong(coreKey.trim());
                keyValue = keyValue % (maxKey + 1);
                return String.format("%0" + zeropadding + "d", keyValue);
            } catch (NumberFormatException e) {
                if (debug) {
                    System.err.println("Warning: Cannot parse key as number: " + coreKey + ", using original key");
                }
                return coreKey;
            }
        }
        return coreKey;
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
            String processedKey = processKeyForDefaultMode(key);
            client.addRowKeyElement(table,new String[]{"ycsb_key"});
            String[] fs = new String[]{};
            if (fields != null) {
                fs = fields.toArray(new String[]{});
            }
            Iterator i$ = client.get(table, processedKey, fs).entrySet().iterator();
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
            String processedKey = processKeyForDefaultMode(startkey);
            client.addRowKeyElement(table,new String[]{"ycsb_key"});
            TableQuery query = client.query(table);
            query.addScanRange(new Object[] { processedKey }, new Object[] { ObObj.getMax() });
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
            }
            if (result.isEmpty()) {
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
        String processedKey = processKeyForDefaultMode(key);
        Row rowKey  = row(colVal("ycsb_key", processedKey));
        Row row = row();
        for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
            row.add(entry.getKey(), entry.getValue().toString());
        }
        try {
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
        String processedKey = processKeyForDefaultMode(key);
        Row rowKey  = row(colVal("ycsb_key", processedKey));
        Row row = row();
        for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
            row.add(entry.getKey(), entry.getValue().toString());
        }
        try {
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
        valuesMap.forEach((k, v) -> {
            String processedKey = processKeyForDefaultMode(k);
            if (debug) {
                System.out.println("batchPut: {rowKey: " + processedKey + "}");
            }
            Row rowKey = row(colVal("ycsb_key", processedKey));
            Row row = row();
            for (Map.Entry<String, ByteIterator> entry : v.entrySet()) {
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
            client.addRowKeyElement(table,new String[]{"ycsb_key"});
            List<Mutation> mutationList = new ArrayList<>();
            BatchOperation batchOperation = client.batchOperation(table);
            valuesMap.keySet().forEach(key -> {
                String processedKey = processKeyForDefaultMode(key);
                Row rowKey = row(colVal("ycsb_key", processedKey));
                try {
                    batchOperation.addOperation(MutationFactory.query().setRowKey(rowKey));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            BatchOperationResult batchResult = batchOperation.execute();
            List<Object> results = batchResult.getResults();
            if (results == null || results.isEmpty()) {
              return NOT_FOUND;
            }
            for (int i = 0; i < results.size(); i++) {
              Row row = batchResult.get(i).getOperationRow();
              Map<String, Object> getMap = row.getMap();
              HashMap<String, ByteIterator> rowResult = new HashMap<String, ByteIterator>();    
              for (Map.Entry<String, Object> entry : getMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();    
                rowResult.put(key, new StringByteIterator(value.toString()));
              }
              if (debug) {
                System.out.println("batchRead result: " + rowResult);
              }
            }
            return Status.OK;
        } catch (Exception e) {
            e.printStackTrace();
            return Status.ERROR;
        }
    }
 }
