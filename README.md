<!--
Copyright (c) 2010 Yahoo! Inc., 2012 - 2016 YCSB contributors.
All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You
may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. See accompanying
LICENSE file.
-->


# YCSB for OBKV
This YCSB distribution includes two OBKV model bindings:

## OBKV-HBase Binding

The **obkv-hbase** binding is designed for testing OceanBase HBase-compatible mode performance.

**Features:**
- Support for both ODP mode and direct connection mode
- Range partitioning with key subpartitions
- Multi-version mode support
- Batch operations (batchPut, batchRead)
- Automatic table creation script

**Quick Start:**
```sh
cd obkv-hbase
./build.sh
./create_table.sh 1 40
./run_fast_test.sh load
./run_fast_test.sh read
```

For detailed documentation, see [obkv-hbase/README.md](obkv-hbase/README.md).

## OBKV-Table Binding

The **obkv-table** binding is designed for testing OceanBase Table model performance.

**Features:**
- Support for range partitioning and range+key partitioning
- Flexible table creation with configurable partition strategies
- Support for various workload types

**Quick Start:**
```sh
cd obkv-table
./build.sh
./create_table.sh --mode range 4 1000
./run_fast_test.sh load
./run_fast_test.sh read
```

For detailed documentation, see [obkv-table/README.md](obkv-table/README.md).

