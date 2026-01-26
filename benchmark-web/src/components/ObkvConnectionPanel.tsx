import React from 'react';
import { Card, Input } from './common';
import type { ObkvConnection } from '../types';
import './ObkvConnectionPanel.css';

interface Props {
  modelType: 'hbase' | 'table';
  connection: ObkvConnection;
  onChange: (connection: ObkvConnection) => void;
}

export const ObkvConnectionPanel: React.FC<Props> = ({ modelType, connection, onChange }: Props) => {
  const handleChange = (field: keyof ObkvConnection, value: string | number) => {
    onChange({ ...connection, [field]: value });
  };

  const handleModeChange = (mode: 'odp' | 'direct') => {
    onChange({ ...connection, mode });
  };

  return (
    <Card
      title="OBKV 连接模式"
      subtitle={modelType === 'hbase' ? "用于 HBase 模式压测的数据库连接" : "用于 Table 模式压测的数据库连接"}
      icon="🔗"
    >
      <div className="obkv-connection-form">
        <div className="mode-selector">
          <button
            className={`mode-btn ${connection.mode === 'odp' ? 'active' : ''}`}
            onClick={() => handleModeChange('odp')}
          >
            <span className="mode-icon">🌐</span>
            <span className="mode-label">ODP 模式 (Proxy)</span>
          </button>
          <button
            className={`mode-btn ${connection.mode === 'direct' ? 'active' : ''}`}
            onClick={() => handleModeChange('direct')}
          >
            <span className="mode-icon">⚡</span>
            <span className="mode-label">直连模式</span>
          </button>
        </div>

        <div className="connection-fields">
          {connection.mode === 'odp' ? (
            <div className="mode-fields">
              <div className="form-row">
                <Input
                  label="ODP IP"
                  value={connection.ip || ''}
                  onChange={(e) => handleChange('ip', e.target.value)}
                  placeholder="10.0.0.1"
                />
                <Input
                  label="ODP Port"
                  type="number"
                  value={connection.port || ''}
                  onChange={(e) => handleChange('port', parseInt(e.target.value) || 0)}
                  placeholder="2885"
                />
              </div>
            </div>
          ) : (
            <div className="mode-fields">
              <div className="form-row">
                <Input
                  label="Param URL"
                  value={connection.paramUrl || ''}
                  onChange={(e) => handleChange('paramUrl', e.target.value)}
                  placeholder="http://..."
                />
              </div>
              <div className="form-row">
                <Input
                  label="Sys UserName"
                  value={connection.sysUserName || ''}
                  onChange={(e) => handleChange('sysUserName', e.target.value)}
                  placeholder="root"
                />
                <Input
                  label="Sys Password"
                  type="password"
                  value={connection.sysPassword || ''}
                  onChange={(e) => handleChange('sysPassword', e.target.value)}
                  placeholder="••••••••"
                />
              </div>
            </div>
          )}

          <div className="common-fields">
            <h4 className="fields-title">公共参数</h4>
            <div className="form-row">
              <Input
                label="Full UserName"
                value={connection.fullUserName}
                onChange={(e) => handleChange('fullUserName', e.target.value)}
                placeholder="user@tenant#cluster"
              />
              <Input
                label="Password"
                type="password"
                value={connection.password}
                onChange={(e) => handleChange('password', e.target.value)}
                placeholder="••••••••"
              />
            </div>
            <div className="form-row">
              <Input
                label="Database"
                value={connection.database}
                onChange={(e) => handleChange('database', e.target.value)}
                placeholder="test"
              />
            </div>
          </div>
        </div>
      </div>
    </Card>
  );
};

