/**
 * History tab module.
 * Displays historical test records with filtering and pagination.
 */
const History = (() => {
  let page = 0;
  const pageSize = 20;
  let allRecords = [];
  let activeRecordId = null;

  function init() {
    document.getElementById('refreshHistoryBtn').addEventListener('click', load);
    document.getElementById('historyPrevBtn').addEventListener('click', () => { page--; render(); });
    document.getElementById('historyNextBtn').addEventListener('click', () => { page++; render(); });
    document.getElementById('filterModule').addEventListener('change', () => { page = 0; render(); });
    document.getElementById('filterStatus').addEventListener('change', () => { page = 0; render(); });
  }

  async function load() {
    const res = await fetch(`/api/tests?page=0&size=200`);
    allRecords = await res.json();
    page = 0;
    populateModuleFilter();
    render();
  }

  function populateModuleFilter() {
    const modules = [...new Set(allRecords.map(r => r.module).filter(Boolean))];
    const sel = document.getElementById('filterModule');
    const prev = sel.value;
    sel.innerHTML = '<option value="">全部模块</option>';
    modules.forEach(m => {
      const o = document.createElement('option');
      o.value = m; o.textContent = m;
      if (m === prev) o.selected = true;
      sel.appendChild(o);
    });
  }

  function getFiltered() {
    const mod = document.getElementById('filterModule').value;
    const status = document.getElementById('filterStatus').value;
    return allRecords.filter(r => {
      if (mod && r.module !== mod) return false;
      if (status && r.status !== status) return false;
      return true;
    });
  }

  function render() {
    const filtered = getFiltered();
    const total = filtered.length;
    const start = page * pageSize;
    const end = Math.min(start + pageSize, total);
    const slice = filtered.slice(start, end);

    document.getElementById('historyPageInfo').textContent = `第 ${page + 1} 页 / 共 ${Math.ceil(total / pageSize) || 1} 页`;
    document.getElementById('historyPrevBtn').disabled = page === 0;
    document.getElementById('historyNextBtn').disabled = end >= total;

    const list = document.getElementById('historyList');
    list.innerHTML = '';
    if (slice.length === 0) {
      list.innerHTML = '<p style="padding:12px;color:#8b949e;font-size:12px">无记录</p>';
      return;
    }

    slice.forEach(r => {
      const item = document.createElement('div');
      item.className = 'history-item' + (r.testId === activeRecordId ? ' active' : '');
      item.innerHTML = `
        <div class="history-item-row1">
          <span class="history-item-title">
            <span class="status-badge status-${r.status || 'UNKNOWN'}">${statusLabel(r.status)}</span>
            ${escHtml(r.testType || '-')}
          </span>
          <span class="history-item-time">${formatTime(r.startTime)}</span>
        </div>
        <div class="history-item-row2">
          <span>${r.module || '-'}</span>
          ${r.durationMs ? `<span>${(r.durationMs/1000).toFixed(1)}s</span>` : ''}
          ${r.configName ? `<span>📄 ${escHtml(r.configName)}</span>` : ''}
        </div>
      `;
      item.addEventListener('click', () => showDetail(r));
      list.appendChild(item);
    });
  }

  async function showDetail(record) {
    activeRecordId = record.testId;
    render();

    const detail = document.getElementById('historyDetail');
    detail.innerHTML = `
      <div class="history-meta-card">
        <dl class="history-meta-grid">
          <dt>测试ID</dt><dd style="font-size:11px;word-break:break-all">${record.testId}</dd>
          <dt>模块</dt><dd>${record.module || '-'}</dd>
          <dt>测试类型</dt><dd>${record.testType || '-'}</dd>
          <dt>表模式</dt><dd>${record.tableMode || '-'}</dd>
          <dt>状态</dt><dd><span class="status-badge status-${record.status}">${statusLabel(record.status)}</span></dd>
          <dt>开始时间</dt><dd>${record.startTime || '-'}</dd>
          <dt>结束时间</dt><dd>${record.endTime || '-'}</dd>
          <dt>时长</dt><dd>${record.durationMs ? (record.durationMs/1000).toFixed(2)+'s' : '-'}</dd>
        </dl>
        <div class="btn-row mt-8">
          <button class="btn btn-sm" id="hist-view-log">查看日志</button>
          <button class="btn btn-sm" id="hist-view-result">查看结果</button>
          <button class="btn btn-sm btn-primary" id="hist-rerun">用此配置重新测试</button>
          <button class="btn btn-sm btn-danger" id="hist-delete">删除记录</button>
        </div>
      </div>
      <div id="hist-detail-content"></div>
    `;

    detail.querySelector('#hist-view-log').addEventListener('click', () => loadLog(record.testId));
    detail.querySelector('#hist-view-result').addEventListener('click', () => loadResult(record.testId));
    detail.querySelector('#hist-rerun').addEventListener('click', () => rerun(record.testId));
    detail.querySelector('#hist-delete').addEventListener('click', () => deleteRecord(record.testId));
  }

  async function loadLog(testId) {
    const content = document.getElementById('hist-detail-content');
    content.innerHTML = '<p style="color:#8b949e;padding:10px;font-size:12px">加载中…</p>';
    const res = await fetch(`/api/tests/${testId}/log?limit=2000`);
    const text = await res.text();
    content.innerHTML = `
      <div style="padding:8px">
        <div class="log-container" style="max-height:400px;overflow-y:auto;border:1px solid #30363d;border-radius:6px;padding:8px">${
          text.split('\n').map(l => {
            let cls = '';
            if (/\[OVERALL\]|\[READ\]|\[INSERT\]/.test(l)) cls = 'hl-result';
            else if (/ERROR|FAILED/i.test(l)) cls = 'hl-error';
            return `<div class="log-line ${cls}">${escHtml(l)}</div>`;
          }).join('')
        }</div>
      </div>`;
  }

  async function loadResult(testId) {
    const content = document.getElementById('hist-detail-content');
    const res = await fetch(`/api/tests/${testId}/results`);
    if (!res.ok) {
      content.innerHTML = '<p style="color:#8b949e;padding:10px;font-size:12px">暂无结果数据</p>';
      return;
    }
    const result = await res.json();
    const filtered = Chart.filterOperations(result.operations || []);
    const canvasId = 'hist-chart-' + testId.substring(0, 8);
    content.innerHTML = `
      <div class="result-panel">
        <div class="result-cards">
          <div class="result-card">
            <div class="result-card-label">吞吐量</div>
            <div class="result-card-value">${Math.round(result.throughput).toLocaleString()}</div>
            <div class="result-card-unit">ops/sec</div>
          </div>
          <div class="result-card">
            <div class="result-card-label">运行时长</div>
            <div class="result-card-value">${(result.runTimeMs/1000).toFixed(1)}</div>
            <div class="result-card-unit">秒</div>
          </div>
        </div>
        ${renderLatencyTable(filtered)}
        <div class="chart-wrap"><canvas id="${canvasId}" width="560" height="200"></canvas></div>
      </div>`;
    if (filtered.length) Chart.drawLatencyChart(canvasId, filtered);
  }

  function renderLatencyTable(ops) {
    if (!ops || !ops.length) return '';
    const rows = ops.map(op => `
      <tr>
        <td>${op.type}</td>
        <td>${(op.count||0).toLocaleString()}</td>
        <td>${fmtMs(op.avgLatencyUs)}</td>
        <td>${fmtMs(op.p95LatencyUs)}</td>
        <td>${fmtMs(op.p99LatencyUs)}</td>
      </tr>`).join('');
    return `<table class="result-table">
      <thead><tr><th>类型</th><th>操作数</th><th>Avg(ms)</th><th>P95(ms)</th><th>P99(ms)</th></tr></thead>
      <tbody>${rows}</tbody></table>`;
  }

  async function rerun(testId) {
    const res = await fetch(`/api/tests/${testId}/workload`);
    if (!res.ok) { alert('无法获取历史配置'); return; }
    const workload = await res.text();
    document.querySelector('[data-tab="test"]').click();
    await Config.parseWorkloadToForm(workload);
  }

  async function deleteRecord(testId) {
    if (!confirm('确认删除该历史记录？此操作不可恢复。')) return;
    const res = await fetch(`/api/tests/${testId}/record`, { method: 'DELETE' });
    if (res.ok) {
      allRecords = allRecords.filter(r => r.testId !== testId);
      render();
      document.getElementById('historyDetail').innerHTML =
        '<div class="no-session-hint"><p>选择左侧历史记录查看详情</p></div>';
    } else {
      const data = await res.json();
      alert('删除失败：' + (data.error || ''));
    }
  }

  function statusLabel(s) {
    const map = { RUNNING:'运行中', COMPLETED:'完成', FAILED:'失败', STOPPED:'已停止', UNKNOWN:'未知', DISK_FULL:'磁盘满', ERROR:'错误' };
    return map[s] || s || '未知';
  }

  function formatTime(t) {
    if (!t) return '-';
    return t.replace('T', ' ');
  }

  function fmt(v) { return v != null ? Math.round(v).toLocaleString() : '-'; }
  function fmtMs(v) { return v != null ? (Number(v) / 1000).toFixed(2) : '-'; }

  function escHtml(s) {
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }

  return { init, load };
})();
