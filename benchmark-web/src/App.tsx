import React, { useState } from 'react';
import {
  SqlConnectionPanel,
  DdlPanel,
  ObkvConnectionPanel,
  ClientConfigPanel,
  WorkloadPanel,
  LogPanel,
} from './components';
import type { 
  SqlConnection, 
  ObkvConnection, 
  ClientConfig, 
  TableConfig 
} from './types';
import './App.css';

function App() {
  // Benchmark model type
  const [modelType, setModelType] = useState<'hbase' | 'table'>('hbase');

  const handleModelTypeChange = (type: 'hbase' | 'table') => {
    setModelType(type);
    if (type === 'table') {
      setTableConfig(prev => ({
        ...prev,
        tableName: 'ycsb_table',
        partitionType: 'key', // Default to key partition for table mode
      }));
    } else {
      setTableConfig(prev => ({
        ...prev,
        tableName: 'ycsb_test',
        partitionType: 'range',
      }));
    }
  };

  // SQL Connection state (for DDL)
  const [sqlConnection, setSqlConnection] = useState<SqlConnection>({
    ip: '',
    port: 2883,
    user: '',
    password: '',
    database: '',
  });

  // Table config state
  const [tableConfig, setTableConfig] = useState<TableConfig>({
    partitionType: 'range',
    tableName: 'ycsb_test',
    columnFamily: 'cf',
    maxKey: 1000000,
    partitionCount: 4,
    keyLength: 12,
    keyPartitionCount: 4,
  });

  // OBKV Connection state (for benchmark)
  const [obkvConnection, setObkvConnection] = useState<ObkvConnection>({
    mode: 'odp',
    ip: '',
    port: 2885,
    fullUserName: '',
    password: '',
    database: '',
  });

  // Client config state
  const [clientConfig, setClientConfig] = useState<ClientConfig>({
    connectionPoolSize: 20,
    rpcOperationTimeout: 10000,
    rpcExecuteTimeout: 15000,
    debug: false,
  });

  // Current task state
  const [currentTask, setCurrentTask] = useState<{
    taskId: string;
    workload: string;
    operation: string;
  } | null>(null);

  const handleTaskStart = (taskId: string, workload: string, operation: string) => {
    setCurrentTask({ taskId, workload, operation });
  };

  return (
    <div className="app">
      <header className="app-header">
        <div className="header-content">
          <div className="header-left">
            <div className="logo">
              <span className="logo-icon">⚡</span>
              <span className="logo-text">OBKV Benchmark</span>
            </div>
            <h1 className="header-title">压测控制台</h1>
          </div>
          <div className="header-center">
            <div className="model-switcher">
              <button 
                className={`model-btn ${modelType === 'hbase' ? 'active hbase' : ''}`}
                onClick={() => handleModelTypeChange('hbase')}
              >
                HBase Model
              </button>
              <button 
                className={`model-btn ${modelType === 'table' ? 'active table' : ''}`}
                onClick={() => handleModelTypeChange('table')}
              >
                Table Model
              </button>
            </div>
          </div>
          <div className="header-right">
            <div className="header-badge">{modelType.toUpperCase()} Mode</div>
          </div>
        </div>
      </header>

      <main className="app-main">
        <div className="main-content">
          <div className="panels-grid">
            <div className="panel-column left-column">
              <SqlConnectionPanel
                connection={sqlConnection}
                onChange={setSqlConnection}
              />

              <DdlPanel
                modelType={modelType}
                sqlConnection={sqlConnection}
                tableConfig={tableConfig}
                onTableConfigChange={setTableConfig}
              />

              <ObkvConnectionPanel
                modelType={modelType}
                connection={obkvConnection}
                onChange={setObkvConnection}
              />

              <ClientConfigPanel
                config={clientConfig}
                onChange={setClientConfig}
              />
            </div>

            <div className="panel-column right-column">
              <WorkloadPanel
                modelType={modelType}
                tableConfig={tableConfig}
                obkvConnection={obkvConnection}
                clientConfig={clientConfig}
                onTaskStart={handleTaskStart}
              />

              <LogPanel
                taskId={currentTask?.taskId || null}
                workload={currentTask?.workload || ''}
                operation={currentTask?.operation || ''}
              />
            </div>
          </div>
        </div>
      </main>

      <footer className="app-footer">
        <div className="footer-content">
          <span>OBKV Benchmark Console v1.1.0</span>
          <span className="footer-divider">•</span>
          <span>Powered by YCSB</span>
        </div>
      </footer>
    </div>
  );
}

export default App;

