import React, { useEffect, useRef, useState } from 'react';
import { Card, Button } from './common';
import { taskApi } from '../api';
import type { TaskStatus } from '../types';
import './LogPanel.css';

interface TaskInfo {
  taskId: string;
  workload: string;
  operation: string;
  status: TaskStatus;
  logs: string[];
  result?: string;
}

interface Props {
  taskId: string | null;
  workload: string;
  operation: string;
}

export const LogPanel: React.FC<Props> = ({ taskId, workload, operation }) => {
  const [task, setTask] = useState<TaskInfo | null>(null);
  const [autoScroll, setAutoScroll] = useState(true);
  const logContainerRef = useRef<HTMLDivElement>(null);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!taskId) {
      setTask(null);
      return;
    }

    // Initialize task
    setTask({
      taskId,
      workload,
      operation,
      status: 'RUNNING',
      logs: [],
    });

    // Connect to WebSocket
    const wsUrl = `ws://${window.location.host}/ws/task`;
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      console.log('WebSocket connected');
      ws.send(JSON.stringify({ action: 'subscribe', taskId }));
    };

    ws.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        
        if (message.type === 'LOG') {
          setTask(prev => prev ? {
            ...prev,
            logs: [...prev.logs, message.message],
          } : null);
        } else if (message.type === 'STATUS') {
          setTask(prev => prev ? {
            ...prev,
            status: message.status as TaskStatus,
          } : null);
        } else if (message.type === 'RESULT') {
          setTask(prev => prev ? {
            ...prev,
            result: message.result,
          } : null);
        }
      } catch (e) {
        console.error('Failed to parse WebSocket message:', e);
      }
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

    ws.onclose = () => {
      console.log('WebSocket disconnected');
    };

    // Also poll for status updates as backup
    const pollInterval = setInterval(async () => {
      try {
        const response = await taskApi.getStatus(taskId);
        if (response.success && response.data) {
          setTask(prev => prev ? {
            ...prev,
            status: response.data!.status,
            result: response.data!.result || prev.result,
            logs: response.data!.logs || prev.logs,
          } : null);
        }
      } catch (e) {
        console.error('Failed to poll task status:', e);
      }
    }, 2000);

    return () => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ action: 'unsubscribe', taskId }));
        ws.close();
      }
      clearInterval(pollInterval);
    };
  }, [taskId, workload, operation]);

  // Auto scroll to bottom
  useEffect(() => {
    if (autoScroll && logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
    }
  }, [task?.logs, autoScroll]);

  const handleStop = async () => {
    if (!taskId) return;
    
    try {
      await taskApi.stop(taskId);
    } catch (e) {
      console.error('Failed to stop task:', e);
    }
  };

  const handleClear = () => {
    setTask(null);
  };

  const getStatusBadge = (status: TaskStatus) => {
    const badges: Record<TaskStatus, { color: string; text: string }> = {
      PENDING: { color: 'warning', text: '等待中' },
      RUNNING: { color: 'primary', text: '运行中' },
      COMPLETED: { color: 'success', text: '已完成' },
      FAILED: { color: 'error', text: '失败' },
      STOPPED: { color: 'secondary', text: '已停止' },
    };
    const badge = badges[status];
    return <span className={`status-badge status-${badge.color}`}>{badge.text}</span>;
  };

  if (!task) {
    return (
      <Card
        title="执行日志"
        subtitle="实时查看任务执行状态"
        icon="📜"
      >
        <div className="log-empty">
          <div className="empty-icon">📋</div>
          <div className="empty-text">暂无任务运行</div>
          <div className="empty-hint">选择一个 Workload 并点击 Load 或 Run 开始执行</div>
        </div>
      </Card>
    );
  }

  return (
    <Card
      title="执行日志"
      subtitle={`${task.workload.toUpperCase()} - ${task.operation.toUpperCase()}`}
      icon="📜"
      actions={
        <div className="log-actions">
          {getStatusBadge(task.status)}
          {task.status === 'RUNNING' && (
            <Button variant="danger" size="sm" onClick={handleStop}>
              停止
            </Button>
          )}
          {task.status !== 'RUNNING' && (
            <Button variant="ghost" size="sm" onClick={handleClear}>
              清除
            </Button>
          )}
        </div>
      }
    >
      <div className="log-panel">
        <div className="log-toolbar">
          <label className="auto-scroll-toggle">
            <input
              type="checkbox"
              checked={autoScroll}
              onChange={(e) => setAutoScroll(e.target.checked)}
            />
            自动滚动
          </label>
          <span className="log-count">{task.logs.length} 行</span>
        </div>

        <div className="log-container" ref={logContainerRef}>
          {task.logs.map((log, index) => (
            <div 
              key={index} 
              className={`log-line ${getLogClass(log)}`}
            >
              <span className="log-number">{index + 1}</span>
              <span className="log-content">{log}</span>
            </div>
          ))}
          {task.status === 'RUNNING' && (
            <div className="log-line log-cursor">
              <span className="log-number">_</span>
              <span className="cursor-blink">▌</span>
            </div>
          )}
        </div>

        {task.result && (
          <div className="log-result">
            <div className="result-title">📊 测试结果</div>
            <pre className="result-content">{task.result}</pre>
          </div>
        )}
      </div>
    </Card>
  );
};

function getLogClass(log: string): string {
  if (log.includes('[ERROR]') || log.includes('Error') || log.includes('Exception')) {
    return 'log-error';
  }
  if (log.includes('[WARN]') || log.includes('Warning')) {
    return 'log-warning';
  }
  if (log.includes('[OVERALL]')) {
    return 'log-overall';
  }
  if (log.includes('[READ]') || log.includes('[UPDATE]') || log.includes('[INSERT]') || 
      log.includes('[SCAN]') || log.includes('[READ-MODIFY-WRITE]')) {
    return 'log-metric';
  }
  return '';
}

