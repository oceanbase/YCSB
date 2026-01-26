import React, { useState } from 'react';
import { Card, Input, Button } from './common';
import { ddlApi } from '../api';
import type { SqlConnection, TableConfig } from '../types';
import './DdlPanel.css';

interface Props {
  modelType: 'hbase' | 'table';
  sqlConnection: SqlConnection;
  tableConfig: TableConfig;
  onTableConfigChange: (config: TableConfig) => void;
}

export const DdlPanel: React.FC<Props> = ({ 
  modelType,
  sqlConnection, 
  tableConfig, 
  onTableConfigChange 
}) => {
  const [loading, setLoading] = useState<string | null>(null);
  const [result, setResult] = useState<{ success: boolean; message: string; sql?: string } | null>(null);
  const [showSql, setShowSql] = useState(false);
  const [previewSql, setPreviewSql] = useState<string>('');

  const handleChange = (field: keyof TableConfig, value: string | number) => {
    // Handle partition type change specially
    if (field === 'partitionType') {
      const newConfig = { ...tableConfig, [field]: value };
      // Reset related fields when changing partition type
      if (value === 'key') {
        newConfig.maxKey = 1000000;
        newConfig.partitionCount = 4;
        newConfig.keyLength = 12;
      } else {
        newConfig.keyPartitionCount = 4;
      }
      onTableConfigChange(newConfig);
    } else {
      onTableConfigChange({ ...tableConfig, [field]: value });
    }
    setResult(null);
  };

  const handleGenerateSql = async () => {
    setLoading('generate');
    try {
      const response = await ddlApi.generateSql({ ...tableConfig, modelType });
      if (response.success && response.data) {
        setPreviewSql(response.data);
        setShowSql(true);
      } else {
        setResult({ success: false, message: response.message || 'SQL 生成失败' });
      }
    } catch (error) {
      setResult({ success: false, message: `生成失败: ${error instanceof Error ? error.message : '未知错误'}` });
    } finally {
      setLoading(null);
    }
  };

  const handleCreateTable = async () => {
    if (!confirm('确定要创建表吗？')) return;
    
    setLoading('create');
    setResult(null);
    try {
      const response = await ddlApi.createTable(sqlConnection, { ...tableConfig, modelType });
      if (response.success && response.data) {
        setResult({
          success: response.data.success,
          message: response.data.message,
          sql: response.data.sql,
        });
      } else {
        setResult({ success: false, message: response.message || '建表失败' });
      }
    } catch (error) {
      setResult({ success: false, message: `建表失败: ${error instanceof Error ? error.message : '未知错误'}` });
    } finally {
      setLoading(null);
    }
  };

  const handleTruncateTable = async () => {
    if (!confirm(`确定要清空表 ${tableConfig.tableName} 吗？此操作不可恢复！`)) return;
    
    setLoading('truncate');
    setResult(null);
    try {
      const response = await ddlApi.truncateTable(sqlConnection, tableConfig.tableName, tableConfig.columnFamily, modelType);
      if (response.success && response.data) {
        setResult({
          success: response.data.success,
          message: response.data.message,
          sql: response.data.sql,
        });
      } else {
        setResult({ success: false, message: response.message || '清空表失败' });
      }
    } catch (error) {
      setResult({ success: false, message: `清空表失败: ${error instanceof Error ? error.message : '未知错误'}` });
    } finally {
      setLoading(null);
    }
  };

  const handleDropTable = async () => {
    if (!confirm(`确定要删除表 ${tableConfig.tableName} 吗？此操作不可恢复！`)) return;
    
    setLoading('drop');
    setResult(null);
    try {
      const response = await ddlApi.dropTable(sqlConnection, tableConfig.tableName, tableConfig.columnFamily, modelType);
      if (response.success && response.data) {
        setResult({
          success: response.data.success,
          message: response.data.message,
          sql: response.data.sql,
        });
      } else {
        setResult({ success: false, message: response.message || '删除表失败' });
      }
    } catch (error) {
      setResult({ success: false, message: `删除表失败: ${error instanceof Error ? error.message : '未知错误'}` });
    } finally {
      setLoading(null);
    }
  };

  return (
    <Card
      title="DDL 操作"
      subtitle="表管理：建表 / 清空表 / 删表"
      icon="📋"
    >
      <div className="ddl-form">
        <div className="form-section">
          <h4 className="section-title">表信息</h4>
          <div className="form-row">
            <Input
              label="表名"
              value={tableConfig.tableName}
              onChange={(e) => handleChange('tableName', e.target.value)}
              placeholder="ycsb_test"
            />
            {modelType === 'hbase' && (
              <Input
                label="列族"
                value={tableConfig.columnFamily}
                onChange={(e) => handleChange('columnFamily', e.target.value)}
                placeholder="cf"
              />
            )}
          </div>
        </div>

        <div className="form-section">
          <h4 className="section-title">分区配置</h4>
          <div className="form-row">
            <div className="input-group">
              <label>分区类型</label>
              <select
                value={tableConfig.partitionType}
                onChange={(e) => handleChange('partitionType', e.target.value)}
                className="partition-type-select"
              >
                <option value="range">Range 分区</option>
                <option value="key">Key 分区</option>
              </select>
            </div>
          </div>
        </div>

        {/* Range 分区参数 */}
        {tableConfig.partitionType === 'range' && (
          <div className="form-section">
            <h4 className="section-title">Range 分区参数</h4>
            <div className="form-row form-row-3">
              <Input
                label="总行数"
                type="number"
                value={tableConfig.maxKey}
                onChange={(e) => handleChange('maxKey', parseInt(e.target.value) || 0)}
                placeholder="1000000"
              />
              <Input
                label="分区数"
                type="number"
                value={tableConfig.partitionCount}
                onChange={(e) => handleChange('partitionCount', parseInt(e.target.value) || 0)}
                placeholder="4"
              />
              <Input
                label="Key 长度"
                type="number"
                value={tableConfig.keyLength}
                onChange={(e) => handleChange('keyLength', parseInt(e.target.value) || 0)}
                placeholder="12"
              />
            </div>
          </div>
        )}

        {/* Key 分区参数 */}
        {tableConfig.partitionType === 'key' && (
          <div className="form-section">
            <h4 className="section-title">Key 分区参数</h4>
            <div className="form-row">
              <Input
                label="分区数"
                type="number"
                value={tableConfig.keyPartitionCount}
                onChange={(e) => handleChange('keyPartitionCount', parseInt(e.target.value) || 0)}
                placeholder="4"
              />
            </div>
          </div>
        )}

        <div className="ddl-actions">
          <Button
            variant="secondary"
            onClick={handleGenerateSql}
            loading={loading === 'generate'}
          >
            预览 SQL
          </Button>
          <Button
            variant="success"
            onClick={handleCreateTable}
            loading={loading === 'create'}
          >
            建表
          </Button>
          <Button
            variant="secondary"
            onClick={handleTruncateTable}
            loading={loading === 'truncate'}
          >
            清空表
          </Button>
          <Button
            variant="danger"
            onClick={handleDropTable}
            loading={loading === 'drop'}
          >
            删表
          </Button>
        </div>

        {result && (
          <div className={`ddl-result ${result.success ? 'success' : 'error'}`}>
            <span className="result-icon">{result.success ? '✓' : '✗'}</span>
            <span className="result-message">{result.message}</span>
          </div>
        )}

        {showSql && previewSql && (
          <div className="sql-preview">
            <div className="sql-preview-header">
              <span>SQL 预览</span>
              <button className="close-btn" onClick={() => setShowSql(false)}>✕</button>
            </div>
            <pre className="sql-code">{previewSql}</pre>
          </div>
        )}
      </div>
    </Card>
  );
};

