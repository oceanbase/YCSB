import React, { useState } from 'react';
import { Card, Input, Button } from './common';
import { sqlApi } from '../api';
import type { SqlConnection } from '../types';
import './SqlConnectionPanel.css';

interface Props {
  connection: SqlConnection;
  onChange: (connection: SqlConnection) => void;
}

export const SqlConnectionPanel: React.FC<Props> = ({ connection, onChange }) => {
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);

  const handleChange = (field: keyof SqlConnection, value: string | number) => {
    onChange({ ...connection, [field]: value });
    setTestResult(null);
  };

  const handleTestConnection = async () => {
    setTesting(true);
    setTestResult(null);
    
    try {
      const response = await sqlApi.testConnection(connection);
      setTestResult({
        success: response.success,
        message: response.message || (response.success ? '连接成功' : '连接失败'),
      });
    } catch (error) {
      setTestResult({
        success: false,
        message: `连接失败: ${error instanceof Error ? error.message : '未知错误'}`,
      });
    } finally {
      setTesting(false);
    }
  };

  return (
    <Card
      title="SQL 连接参数"
      subtitle="用于 DDL 操作的数据库连接"
      icon="🔌"
    >
      <div className="sql-connection-form">
        <div className="form-row">
          <Input
            label="IP 地址"
            value={connection.ip}
            onChange={(e) => handleChange('ip', e.target.value)}
            placeholder="10.0.0.1"
          />
          <Input
            label="端口"
            type="number"
            value={connection.port}
            onChange={(e) => handleChange('port', parseInt(e.target.value) || 2883)}
            placeholder="2883"
          />
        </div>
        <div className="form-row">
          <Input
            label="用户名"
            value={connection.user}
            onChange={(e) => handleChange('user', e.target.value)}
            placeholder="root@tenant"
          />
          <Input
            label="密码"
            type="password"
            value={connection.password}
            onChange={(e) => handleChange('password', e.target.value)}
            placeholder="••••••••"
          />
        </div>
        <div className="form-row">
          <Input
            label="数据库"
            value={connection.database}
            onChange={(e) => handleChange('database', e.target.value)}
            placeholder="test"
          />
          <div className="test-connection-wrapper">
            <Button
              variant="secondary"
              onClick={handleTestConnection}
              loading={testing}
            >
              测试连接
            </Button>
            {testResult && (
              <span className={`test-result ${testResult.success ? 'success' : 'error'}`}>
                {testResult.success ? '✓' : '✗'} {testResult.message}
              </span>
            )}
          </div>
        </div>
      </div>
    </Card>
  );
};

