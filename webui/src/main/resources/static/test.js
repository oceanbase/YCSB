/**
 * Test session management module.
 * Handles: launch test, session tabs, config preview (two-way binding),
 * real-time log, result panel.
 */
const Test = (() => {
  const sessions = [];    // { testId, label, meta, logLines, result }
  let activeIdx = -1;
  let sessionCounter = 0;

  function init() {
    // Launch button(s) - may be one or two (load + run for read/scan)
    updateLaunchButtons();
    Config.onFormChange(() => updateLaunchButtons());
  }

  function updateLaunchButtons() {
    const wrap = document.getElementById('launchButtonsWrap');
    if (!wrap) return;
    const isReadOrScan = () => { const t = Config.getActiveTestType(); return t === 'read' || t === 'scan'; };
    if (isReadOrScan()) {
      wrap.innerHTML = '<button id="loadDataBtn" class="btn btn-primary btn-lg" style="margin-bottom:8px">1. 加载数据</button><button id="startTestBtn" class="btn btn-primary btn-lg">2. 执行测试</button>';
      document.getElementById('startTestBtn').addEventListener('click', () => launchTest(false));
      const lb = document.getElementById('loadDataBtn');
      if (lb) lb.addEventListener('click', () => launchTest(true));
    } else {
      wrap.innerHTML = '<button id="startTestBtn" class="btn btn-primary btn-lg">▶ 发起新测试</button>';
      document.getElementById('startTestBtn').addEventListener('click', () => launchTest(false));
    }
  }

  // ---- Launch test ----

  async function launchTest(forceLoadPhase) {
    const testType = forceLoadPhase ? 'load' : Config.getActiveTestType();
    let workloadContent;
    if (forceLoadPhase) {
      const params = Config.collectAllFormParams();
      params['insertproportion'] = '1';
      params['readproportion'] = '0';
      params['scanproportion'] = '0';
      params['updateproportion'] = '0';
      workloadContent = Config.buildWorkloadContent(params, 'load', Config.getActiveModule(), true);
    } else {
      workloadContent = Config.buildWorkloadContent(
        Config.collectAllFormParams(), Config.getActiveTestType(), Config.getActiveModule(), Config.isLoadTest()
      );
    }

    const body = {
      module:          Config.getActiveModule(),
      testType:        testType,
      tableMode:       Config.getActiveTableMode(),
      workloadContent: workloadContent,
    };

    const btn = document.getElementById('startTestBtn');
    const loadBtn = document.getElementById('loadDataBtn');
    if (btn) { btn.disabled = true; btn.textContent = '⏳ 启动中…'; }
    if (loadBtn) loadBtn.disabled = true;

    try {
      const res = await fetch('/api/tests', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (!res.ok) {
        alert('启动失败：' + (data.error || res.statusText));
        return;
      }
      sessionCounter++;
      const label = `${body.testType}#${sessionCounter}`;
      addSession(data.testId, label, body.testType, workloadContent);
    } catch (e) {
      alert('启动失败：' + e.message);
    } finally {
      updateLaunchButtons();
    }
  }

  // ---- Session management ----

  function addSession(testId, label, testType, workloadSnapshot) {
    const session = {
      testId, label, testType,
      status: 'RUNNING',
      workloadSnapshot,
      logLines: [],
      chartData: [],  // { sec, totalOps } for live ops chart
      rtData: {},   // { opType: [{ sec, avgMs, p99Ms, p999Ms }], ... } from real-time log [OP: Avg=, 99=, 99.9=]
      result: null,
      autoScroll: true,
    };
    sessions.push(session);
    const idx = sessions.length - 1;
    renderSessionTab(idx, session);
    activateSession(idx);
    startLogStream(session, idx);
  }

  function renderSessionTab(idx, session) {
    const bar = document.getElementById('sessionTabs');
    const tab = document.createElement('button');
    tab.className = 'session-tab active';
    tab.dataset.idx = idx;
    tab.id = `session-tab-${idx}`;
    tab.innerHTML = `
      <span class="session-status-dot dot-${session.status.toLowerCase()}"></span>
      <span class="session-label">${session.label}</span>
      <button class="session-close" data-idx="${idx}" title="关闭">×</button>
    `;
    tab.addEventListener('click', e => {
      if (e.target.classList.contains('session-close')) return;
      activateSession(idx);
    });
    tab.querySelector('.session-close').addEventListener('click', () => closeSession(idx));
    bar.appendChild(tab);
  }

  function activateSession(idx) {
    activeIdx = idx;
    document.querySelectorAll('.session-tab').forEach(t => t.classList.remove('active'));
    const tab = document.getElementById(`session-tab-${idx}`);
    if (tab) tab.classList.add('active');
    renderSessionContent(idx);
  }

  function renderSessionContent(idx) {
    const container = document.getElementById('sessionContent');
    const session = sessions[idx];
    if (!session) {
      container.innerHTML = '<div class="no-session-hint"><p>在左侧填写配置，点击「▶ 发起新测试」开始测试</p></div>';
      return;
    }

    container.innerHTML = `
      <div class="session-view" id="session-view-${idx}" style="position:relative">
        <div class="session-grid-2x2" id="session-grid-${idx}" data-col0="" data-row0="">
          <div class="session-cell" id="cell-preview-${idx}">
            <div class="session-cell-title">配置预览</div>
            <div class="session-cell-body workload-preview-wrap">
              <pre class="workload-textarea" style="margin:0;white-space:pre-wrap;word-break:break-all">${escHtml(session.workloadSnapshot || '')}</pre>
            </div>
          </div>
          <div class="session-cell" id="cell-log-${idx}">
            <div class="session-cell-title" style="display:flex;align-items:center;justify-content:space-between">
              <span>实时日志</span>
              <label style="font-size:12px;display:flex;align-items:center;gap:6px">
                <input type="checkbox" id="auto-scroll-${idx}" ${session.autoScroll ? 'checked' : ''}> 自动滚动
              </label>
            </div>
            <div class="session-cell-body log-container" id="log-${idx}"></div>
          </div>
          <div class="session-cell" id="cell-chart-${idx}">
            <div class="session-cell-title">实时监控</div>
            <div class="session-cell-body live-chart-wrap live-chart-split">
              <div class="live-chart-item">
                <div class="live-chart-label">ops/sec</div>
                <canvas id="live-chart-ops-${idx}" width="400" height="120"></canvas>
              </div>
              <div class="live-chart-item">
                <div class="live-chart-label">RT (ms)</div>
                <canvas id="live-chart-rt-${idx}" width="400" height="120"></canvas>
              </div>
            </div>
          </div>
          <div class="session-cell" id="cell-result-${idx}">
            <div class="session-cell-title">测试结果</div>
            <div class="session-cell-body result-panel" id="result-panel-${idx}">
              <p style="color:var(--text-muted);font-size:12px">测试完成后显示结果…</p>
            </div>
          </div>
          <div class="session-divider-v" id="session-div-v-${idx}" title="拖动调整左右宽度"></div>
          <div class="session-divider-h" id="session-div-h-${idx}" title="拖动调整上下高度"></div>
        </div>
      </div>
    `;

    const asChk = document.getElementById(`auto-scroll-${idx}`);
    if (asChk) asChk.addEventListener('change', () => { session.autoScroll = asChk.checked; });

    updateConfigBarStopButton(idx, session);

    initSessionResize(idx);

    if (session.logLines.length > 0) {
      const logContainer = document.getElementById(`log-${idx}`);
      session.logLines.forEach(line => appendLogLine(logContainer, line));
      if (session.autoScroll) logContainer.scrollTop = logContainer.scrollHeight;
    }
    if (session.chartData.length > 0 && typeof Chart.drawLiveChart === 'function') {
      Chart.drawLiveChart(`live-chart-ops-${idx}`, session.chartData);
    }
    if (session.rtData && Object.keys(session.rtData).length > 0 && typeof Chart.drawLiveChartRt === 'function') {
      Chart.drawLiveChartRt(`live-chart-rt-${idx}`, session.rtData);
    }
    if (session.result) renderResult(idx, session.result);
  }

  function updateConfigBarStopButton(idx, session) {
    const slot = document.getElementById('configBarStopSlot');
    if (!slot) return;
    if (session && session.status === 'RUNNING' && activeIdx === idx) {
      slot.innerHTML = '<button class="btn btn-sm btn-danger" id="config-bar-stop-btn">■ 停止</button>';
      const btn = document.getElementById('config-bar-stop-btn');
      if (btn) {
        btn.addEventListener('click', async () => {
          const s = sessions[activeIdx];
          if (!s || s.status !== 'RUNNING') return;
          if (!confirm('确认停止该测试？')) return;
          await fetch(`/api/tests/${s.testId}`, { method: 'DELETE' });
          btn.disabled = true;
          btn.textContent = '停止中…';
        });
      }
    } else {
      slot.innerHTML = '';
    }
  }

  function initSessionResize(idx) {
    const grid = document.getElementById(`session-grid-${idx}`);
    const divV = document.getElementById(`session-div-v-${idx}`);
    const divH = document.getElementById(`session-div-h-${idx}`);
    if (!grid || !divV || !divH) return;
    const minSize = 120;

    divV.addEventListener('mousedown', e => {
      e.preventDefault();
      let startX = e.clientX;
      const rect = grid.getBoundingClientRect();
      let col0 = grid.dataset.col0 ? parseInt(grid.dataset.col0, 10) : Math.round(rect.width / 2 - 3);
      const onMove = (e2) => {
        col0 = col0 + (e2.clientX - startX);
        startX = e2.clientX;
        col0 = Math.min(Math.max(col0, minSize), rect.width - 6 - minSize);
        grid.dataset.col0 = String(col0);
        grid.style.gridTemplateColumns = `${col0}px 6px 1fr`;
      };
      const onUp = () => {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        document.body.style.cursor = '';
      };
      document.body.style.cursor = 'col-resize';
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });

    divH.addEventListener('mousedown', e => {
      e.preventDefault();
      let startY = e.clientY;
      const rect = grid.getBoundingClientRect();
      let row0 = grid.dataset.row0 ? parseInt(grid.dataset.row0, 10) : Math.round(rect.height / 2 - 3);
      const onMove = (e2) => {
        row0 = row0 + (e2.clientY - startY);
        startY = e2.clientY;
        row0 = Math.min(Math.max(row0, minSize), rect.height - 6 - minSize);
        grid.dataset.row0 = String(row0);
        grid.style.gridTemplateRows = `${row0}px 6px 1fr`;
      };
      const onUp = () => {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        document.body.style.cursor = '';
      };
      document.body.style.cursor = 'row-resize';
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  }

  function closeSession(idx) {
    const session = sessions[idx];
    if (!session) return;
    if (session.status === 'RUNNING') {
      if (!confirm('测试正在运行中，关闭将停止测试。确认？')) return;
      fetch(`/api/tests/${session.testId}`, { method: 'DELETE' });
    }
    SSE.disconnect(session.testId);
    session._closed = true;
    const tab = document.getElementById(`session-tab-${idx}`);
    if (tab) tab.remove();
    if (activeIdx === idx) {
      const nextIdx = sessions.findIndex((s, i) => i !== idx && s && !s._closed);
      if (nextIdx >= 0) activateSession(nextIdx);
      else {
        activeIdx = -1;
        document.getElementById('sessionContent').innerHTML =
          '<div class="no-session-hint"><p>在左侧填写配置，点击「▶ 发起新测试」开始测试</p></div>';
        updateConfigBarStopButton(-1, null);
      }
    }
  }

  // ---- Log streaming ----

  function startLogStream(session, idx) {
    let lastSec = 0;
    SSE.connect(session.testId, {
      onLog: line => {
        session.logLines.push(line);
        if (session.logLines.length > 1000) session.logLines.shift();
        const statusMatch = line.match(/(\d+)\s+sec:\s*(\d+)\s+operations/);
        if (statusMatch) {
          const sec = parseInt(statusMatch[1], 10);
          lastSec = sec;
          const totalOps = parseInt(statusMatch[2], 10);
          session.chartData.push({ sec, totalOps });
          if (activeIdx === idx) {
            const opsCanvas = document.getElementById(`live-chart-ops-${idx}`);
            if (opsCanvas && typeof Chart.drawLiveChart === 'function') Chart.drawLiveChart(`live-chart-ops-${idx}`, session.chartData);
          }
        }
        // Parse RT from [OP: Count=..., Avg=..., 99=..., 99.9=...] (values in us -> ms). Only main op types.
        const RT_OP_TYPES = ['INSERT', 'READ', 'UPDATE', 'SCAN', 'BATCH_READ', 'BATCH_PUT'];
        const blockRe = /\[([^\]:]+):\s*([^\]]+)\]/g;
        let blockM;
        while ((blockM = blockRe.exec(line)) !== null) {
          const opType = blockM[1].trim().toUpperCase();
          if (!RT_OP_TYPES.includes(opType)) continue;
          const rest = blockM[2];
          const avgM = rest.match(/Avg=([\d.]+)/);
          const p99M = rest.match(/\b99=([\d.]+)/);
          const p999M = rest.match(/99\.9=([\d.]+)/);
          if (opType && lastSec >= 0 && (avgM || p99M || p999M)) {
            const avgUs = avgM ? parseFloat(avgM[1], 10) : null;
            const p99Us = p99M ? parseFloat(p99M[1], 10) : null;
            const p999Us = p999M ? parseFloat(p999M[1], 10) : null;
            const avgMs = avgUs != null ? avgUs / 1000 : null;
            const p99Ms = p99Us != null ? p99Us / 1000 : null;
            const p999Ms = p999Us != null ? p999Us / 1000 : null;
            if (!session.rtData[opType]) session.rtData[opType] = [];
            session.rtData[opType].push({ sec: lastSec, avgMs, p99Ms, p999Ms });
            if (activeIdx === idx) {
              const rtCanvas = document.getElementById(`live-chart-rt-${idx}`);
              if (rtCanvas && typeof Chart.drawLiveChartRt === 'function') Chart.drawLiveChartRt(`live-chart-rt-${idx}`, session.rtData);
            }
          }
        }
        if (activeIdx === idx) {
          const logContainer = document.getElementById(`log-${idx}`);
          if (logContainer) {
            appendLogLine(logContainer, line);
            while (logContainer.childElementCount > 1000) logContainer.removeChild(logContainer.firstChild);
            if (session.autoScroll) logContainer.scrollTop = logContainer.scrollHeight;
          }
        }
      },
      onDone: () => {
        updateSessionStatus(idx, 'completed');
        fetchResult(session, idx);
      },
      onError: () => {
        updateSessionStatus(idx, 'failed');
      }
    });
  }

  function appendLogLine(container, line) {
    const div = document.createElement('div');
    div.className = 'log-line';
    if (/\[OVERALL\]|\[READ\]|\[INSERT\]|\[UPDATE\]|\[SCAN\]/.test(line)) div.classList.add('hl-result');
    else if (/ERROR|FAILED/i.test(line)) div.classList.add('hl-error');
    else if (/sec:/.test(line)) div.classList.add('hl-status');
    div.textContent = line;
    container.appendChild(div);
  }

  function updateSessionStatus(idx, statusClass) {
    const session = sessions[idx];
    if (!session) return;
    session.status = statusClass.toUpperCase();
    const dot = document.querySelector(`#session-tab-${idx} .session-status-dot`);
    if (dot) {
      dot.className = `session-status-dot dot-${statusClass}`;
    }
    if (activeIdx === idx) updateConfigBarStopButton(idx, session);
  }

  async function fetchResult(session, idx) {
    try {
      const res = await fetch(`/api/tests/${session.testId}/results`);
      if (res.ok) {
        session.result = await res.json();
        if (activeIdx === idx) {
          renderResult(idx, session.result);
          const panel = document.getElementById(`result-panel-${idx}`);
          if (panel) panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
      }
    } catch (e) { /* ignore */ }
  }

  function renderResult(idx, result) {
    const panel = document.getElementById(`result-panel-${idx}`);
    if (!panel || !result) return;

    panel.innerHTML = `
      <div class="result-cards">
        <div class="result-card">
          <div class="result-card-label">吞吐量</div>
          <div class="result-card-value">${Math.round(result.throughput).toLocaleString()}</div>
          <div class="result-card-unit">ops/sec</div>
        </div>
        <div class="result-card">
          <div class="result-card-label">运行时长</div>
          <div class="result-card-value">${(result.runTimeMs / 1000).toFixed(1)}</div>
          <div class="result-card-unit">秒</div>
        </div>
        <div class="result-card">
          <div class="result-card-label">总操作数</div>
          <div class="result-card-value">${(result.totalOps || 0).toLocaleString()}</div>
          <div class="result-card-unit">ops</div>
        </div>
      </div>
      ${renderLatencyTable(result.operations)}
      <div class="chart-wrap">
        <canvas id="chart-${idx}" width="560" height="220"></canvas>
      </div>
    `;

    if (result.operations && result.operations.length > 0) {
      const filtered = Chart.filterOperations(result.operations);
      if (filtered.length) Chart.drawLatencyChart(`chart-${idx}`, filtered);
    }
  }

  function renderLatencyTable(ops) {
    const allowed = ['insert', 'read', 'update', 'scan', 'batchput', 'batchread'];
    const filtered = (ops || []).filter(op => op && op.type && allowed.includes(String(op.type).toLowerCase()));
    if (filtered.length === 0) return '';
    const rows = filtered.map(op => `
      <tr>
        <td>${op.type}</td>
        <td>${op.count ? op.count.toLocaleString() : '-'}</td>
        <td>${fmt(op.avgLatencyUs)}</td>
        <td>${fmt(op.p95LatencyUs)}</td>
        <td>${fmt(op.p99LatencyUs)}</td>
        <td>${fmt(op.minLatencyUs)}</td>
        <td>${fmt(op.maxLatencyUs)}</td>
      </tr>`).join('');
    return `
      <table class="result-table">
        <thead><tr>
          <th>操作类型</th><th>操作数</th>
          <th>Avg(ms)</th><th>P95(ms)</th><th>P99(ms)</th>
          <th>Min(ms)</th><th>Max(ms)</th>
        </tr></thead>
        <tbody>${rows}</tbody>
      </table>`;
  }

  function fmt(v) { return v != null ? (Number(v) / 1000).toFixed(2) : '-'; }

  function escHtml(s) {
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  }

  return { init, updateWorkloadPreview: function() {} };
})();
