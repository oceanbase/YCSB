/**
 * Charts: use Chart.js when available for clearer rendering; fallback to canvas.
 * Live charts use incremental update (append-only) to avoid jumpy redraws.
 */
const Chart = (() => {
  const allowedOpTypes = ['insert', 'read', 'update', 'scan', 'batchput', 'batchread'];
  const liveCharts = new Map();  // canvasId -> Chart instance (for incremental update)
  const MAX_LIVE_POINTS = 120;  // sliding window for smoother display

  function filterOperations(ops) {
    if (!ops || !ops.length) return [];
    return ops.filter(op => op && op.type && allowedOpTypes.includes(String(op.type).toLowerCase()));
  }

  function drawLatencyChart(canvasId, data) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    const filtered = filterOperations(data);

    if (typeof window.Chart !== 'undefined' && window.Chart) {
      const ctx = canvas.getContext('2d');
      if (window.Chart.getChart(canvas)) window.Chart.getChart(canvas).destroy();
      const labels = filtered.map(op => op.type);
      const toMs = v => (v != null && v !== '') ? (Number(v) / 1000) : 0;
      const datasets = [
        { label: 'Avg(ms)', data: filtered.map(op => toMs(op.avgLatencyUs)), backgroundColor: 'rgba(88,166,255,0.8)' },
        { label: 'P95(ms)', data: filtered.map(op => toMs(op.p95LatencyUs)), backgroundColor: 'rgba(63,185,80,0.8)' },
        { label: 'P99(ms)', data: filtered.map(op => toMs(op.p99LatencyUs)), backgroundColor: 'rgba(248,81,73,0.8)' },
      ];
      new window.Chart(ctx, {
        type: 'bar',
        data: { labels, datasets },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            y: { beginAtZero: true, title: { display: true, text: 'ms' } },
          },
          plugins: { legend: { position: 'top' } },
        },
      });
      return;
    }

    // Fallback: canvas (values in ms)
    const ctx = canvas.getContext('2d');
    const W = canvas.width;
    const H = canvas.height;
    ctx.clearRect(0, 0, W, H);
    if (!filtered.length) {
      ctx.fillStyle = '#8b949e';
      ctx.font = '13px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('暂无结果数据', W / 2, H / 2);
      return;
    }
    const padding = { top: 20, right: 20, bottom: 60, left: 60 };
    const chartW = W - padding.left - padding.right;
    const chartH = H - padding.top - padding.bottom;
    const metrics = ['avgLatencyUs', 'p95LatencyUs', 'p99LatencyUs'];
    const colors = ['#58a6ff', '#3fb950', '#f85149'];
    const toMs = v => (v != null && v !== '') ? (Number(v) / 1000) : 0;
    const allVals = filtered.flatMap(op => metrics.map(m => toMs(op[m])));
    const maxVal = Math.max(...allVals, 0.01);
    const groupCount = filtered.length;
    const barGroupW = chartW / groupCount;
    const barW = barGroupW / (metrics.length + 1);
    filtered.forEach((op, gi) => {
      const gx = padding.left + gi * barGroupW + barW / 2;
      metrics.forEach((m, mi) => {
        const val = toMs(op[m]);
        const bh = (val / maxVal) * chartH;
        ctx.fillStyle = colors[mi];
        ctx.fillRect(gx + mi * barW, padding.top + chartH - bh, barW - 2, bh);
      });
      ctx.fillStyle = '#8b949e';
      ctx.font = '11px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(op.type, gx + (metrics.length * barW) / 2 - barW / 2, padding.top + chartH + 16);
    });
  }

  function drawLiveChart(canvasId, data) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    if (typeof window.Chart === 'undefined') {
      drawLiveChartFallback(canvasId, data, 'ops/sec', (d, i) => {
        if (i < 1) return 0;
        const dt = d[i].sec - d[i - 1].sec;
        const dOps = d[i].totalOps - d[i - 1].totalOps;
        return dt > 0 ? dOps / dt : 0;
      });
      return;
    }

    const points = [];
    if (data && data.length >= 2) {
      for (let i = 1; i < data.length; i++) {
        const dt = data[i].sec - data[i - 1].sec;
        const dOps = data[i].totalOps - data[i - 1].totalOps;
        points.push({ x: data[i].sec, y: dt > 0 ? dOps / dt : 0 });
      }
    }

    let chart = liveCharts.get(canvasId);
    if (chart && chart.canvas !== canvas) {
      chart.destroy();
      liveCharts.delete(canvasId);
      chart = null;
    }

    if (chart) {
      const labels = points.slice(-MAX_LIVE_POINTS).map(p => p.x);
      const values = points.slice(-MAX_LIVE_POINTS).map(p => p.y);
      chart.data.labels = labels;
      chart.data.datasets[0].data = values;
      chart.update('none');
      return;
    }

    if (points.length < 1) {
      const ctx = canvas.getContext('2d');
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = '#8b949e';
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('运行中…', canvas.width / 2, canvas.height / 2);
      return;
    }

    const labels = points.slice(-MAX_LIVE_POINTS).map(p => p.x);
    const values = points.slice(-MAX_LIVE_POINTS).map(p => p.y);
    chart = new window.Chart(canvas.getContext('2d'), {
      type: 'line',
      data: {
        labels,
        datasets: [{ label: 'ops/sec', data: values, borderColor: '#58a6ff', fill: false, tension: 0.1 }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        scales: {
          x: { title: { display: true, text: '时间(sec)' } },
          y: { beginAtZero: true, title: { display: true, text: 'ops/sec' } },
        },
      },
    });
    liveCharts.set(canvasId, chart);
  }

  function drawLiveChartRt(canvasId, rtDataByType) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    if (typeof window.Chart === 'undefined') {
      drawLiveChartRtFallback(canvasId, rtDataByType);
      return;
    }

    const series = [];
    const allSecSet = new Set();
    const opTypes = Object.keys(rtDataByType || {}).filter(k => Array.isArray(rtDataByType[k]) && rtDataByType[k].length > 0);
    opTypes.forEach(op => {
      rtDataByType[op].forEach(p => { if (p.sec != null) allSecSet.add(p.sec); });
    });
    const allSec = Array.from(allSecSet).sort((a, b) => a - b);
    const limitedSec = allSec.slice(-MAX_LIVE_POINTS);

    const metricKeys = ['avgMs', 'p99Ms', 'p999Ms'];
    const metricLabels = ['Avg(ms)', '99(ms)', '99.9(ms)'];
    const seriesColors = [
      '#58a6ff', '#3fb950', '#f85149', '#a371f7', '#f0883e', '#79c0ff',
      '#ff7b72', '#56d364', '#e86c60', '#7ee787', '#ffa657', '#d2a8ff'
    ];
    let colorIdx = 0;
    opTypes.forEach(opType => {
      const points = rtDataByType[opType];
      const bySec = {};
      points.forEach(p => { bySec[p.sec] = p; });
      metricKeys.forEach((key, mi) => {
        const label = `${opType} ${metricLabels[mi]}`;
        const data = limitedSec.map(sec => {
          const p = bySec[sec];
          if (!p || (key === 'avgMs' ? p.avgMs : key === 'p99Ms' ? p.p99Ms : p.p999Ms) == null) return null;
          return key === 'avgMs' ? p.avgMs : key === 'p99Ms' ? p.p99Ms : p.p999Ms;
        });
        series.push({
          label,
          data,
          borderColor: seriesColors[colorIdx % seriesColors.length],
          borderDash: mi === 0 ? [] : mi === 1 ? [4, 2] : [2, 2],
          fill: false,
          tension: 0.1,
        });
        colorIdx++;
      });
    });

    let chart = liveCharts.get(canvasId);
    if (chart && chart.canvas !== canvas) {
      chart.destroy();
      liveCharts.delete(canvasId);
      chart = null;
    }

    if (chart) {
      chart.data.labels = limitedSec;
      chart.data.datasets = series;
      chart.options.scales.y.title.display = true;
      chart.options.scales.y.title.text = 'ms';
      chart.update('none');
      return;
    }

    if (series.length < 1 || limitedSec.length < 1) {
      const ctx = canvas.getContext('2d');
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = '#8b949e';
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('暂无实时 RT 数据', canvas.width / 2, canvas.height / 2);
      return;
    }

    chart = new window.Chart(canvas.getContext('2d'), {
      type: 'line',
      data: { labels: limitedSec, datasets: series },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        scales: {
          x: { title: { display: true, text: '时间(sec)' } },
          y: { beginAtZero: true, title: { display: true, text: 'ms' } },
        },
        plugins: { legend: { position: 'top', maxHeight: 120 } },
      },
    });
    liveCharts.set(canvasId, chart);
  }

  function hexToRgb(hex) {
    const m = hex.match(/^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i);
    return m ? { r: parseInt(m[1], 16), g: parseInt(m[2], 16), b: parseInt(m[3], 16) } : null;
  }

  function drawLiveChartRtFallback(canvasId, rtDataByType) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const W = canvas.width;
    const H = canvas.height;
    ctx.clearRect(0, 0, W, H);
    const opTypes = Object.keys(rtDataByType || {}).filter(k => Array.isArray(rtDataByType[k]) && rtDataByType[k].length > 0);
    if (opTypes.length === 0) {
      ctx.fillStyle = '#8b949e';
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('暂无实时 RT 数据', W / 2, H / 2);
      return;
    }
    const allSec = [];
    opTypes.forEach(op => rtDataByType[op].forEach(p => { if (p.sec != null && !allSec.includes(p.sec)) allSec.push(p.sec); }));
    allSec.sort((a, b) => a - b);
    const maxSec = Math.max(...allSec, 1);
    let maxMs = 1;
    opTypes.forEach(op => {
      rtDataByType[op].forEach(p => {
        if (p.avgMs != null) maxMs = Math.max(maxMs, p.avgMs);
        if (p.p99Ms != null) maxMs = Math.max(maxMs, p.p99Ms);
        if (p.p999Ms != null) maxMs = Math.max(maxMs, p.p999Ms);
      });
    });
    const padding = { top: 16, right: 16, bottom: 24, left: 44 };
    const chartW = W - padding.left - padding.right;
    const chartH = H - padding.top - padding.bottom;
    const colors = ['#58a6ff', '#3fb950', '#f85149'];
    opTypes.forEach((op, oi) => {
      const points = rtDataByType[op];
      ['avgMs', 'p99Ms', 'p999Ms'].forEach((key, ki) => {
        const pts = points.map(p => ({ x: p.sec, y: p[key] })).filter(p => p.y != null);
        if (pts.length === 0) return;
        ctx.strokeStyle = colors[ki];
        ctx.lineWidth = 2;
        ctx.setLineDash(ki === 0 ? [] : ki === 1 ? [4, 2] : [2, 2]);
        ctx.beginPath();
        pts.forEach((p, i) => {
          const x = padding.left + (p.x / maxSec) * chartW;
          const y = padding.top + chartH - (p.y / maxMs) * chartH;
          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        });
        ctx.stroke();
      });
    });
    ctx.setLineDash([]);
  }

  function drawLiveChartFallback(canvasId, data, label, getY) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const W = canvas.width;
    const H = canvas.height;
    ctx.clearRect(0, 0, W, H);
    if (!data || (label === 'ops/sec' ? data.length < 2 : data.length < 1)) {
      ctx.fillStyle = '#8b949e';
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(label === 'ops/sec' ? '运行中…' : '暂无实时 RT 数据', W / 2, H / 2);
      return;
    }
    const points = [];
    for (let i = 0; i < data.length; i++) {
      points.push({ sec: data[i].sec, y: getY(data, i) });
    }
    const maxY = Math.max(...points.map(p => p.y), 1);
    const maxSec = Math.max(...points.map(p => p.sec), 1);
    const padding = { top: 16, right: 16, bottom: 24, left: 44 };
    const chartW = W - padding.left - padding.right;
    const chartH = H - padding.top - padding.bottom;
    ctx.strokeStyle = label === 'ops/sec' ? '#58a6ff' : '#3fb950';
    ctx.lineWidth = 2;
    ctx.beginPath();
    points.forEach((p, i) => {
      const x = padding.left + (p.sec / maxSec) * chartW;
      const y = padding.top + chartH - (p.y / maxY) * chartH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();
  }

  return { drawLatencyChart, drawLiveChart, drawLiveChartRt, filterOperations };
})();
