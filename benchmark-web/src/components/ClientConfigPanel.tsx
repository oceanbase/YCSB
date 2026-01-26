import React from 'react';
import { Card, Input, Checkbox } from './common';
import type { ClientConfig } from '../types';
import './ClientConfigPanel.css';

interface Props {
  config: ClientConfig;
  onChange: (config: ClientConfig) => void;
}

export const ClientConfigPanel: React.FC<Props> = ({ config, onChange }) => {
  const handleChange = (field: keyof ClientConfig, value: number | boolean) => {
    onChange({ ...config, [field]: value });
  };

  return (
    <Card
      title="客户端参数"
      subtitle="连接池、超时、调试配置"
      icon="⚙️"
    >
      <div className="client-config-form">
        <div className="config-grid">
          <Input
            label="连接池大小"
            type="number"
            value={config.connectionPoolSize}
            onChange={(e) => handleChange('connectionPoolSize', parseInt(e.target.value) || 20)}
            suffix="个"
          />
          <Input
            label="服务端执行超时"
            type="number"
            value={config.rpcOperationTimeout}
            onChange={(e) => handleChange('rpcOperationTimeout', parseInt(e.target.value) || 10000)}
            suffix="ms"
          />
          <Input
            label="客户端等待超时"
            type="number"
            value={config.rpcExecuteTimeout}
            onChange={(e) => handleChange('rpcExecuteTimeout', parseInt(e.target.value) || 15000)}
            suffix="ms"
          />
          <div className="checkbox-wrapper">
            <Checkbox
              label="开启 Debug 日志"
              checked={config.debug}
              onChange={(e) => handleChange('debug', e.target.checked)}
            />
          </div>
        </div>
      </div>
    </Card>
  );
};

