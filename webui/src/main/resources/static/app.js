/**
 * App bootstrap: tab navigation, theme, config panel collapse, global init.
 */
document.addEventListener('DOMContentLoaded', () => {
  // Theme: init from localStorage
  const theme = localStorage.getItem('ycsb-theme') || 'dark';
  document.documentElement.setAttribute('data-theme', theme);
  const themeBtn = document.getElementById('themeToggle');
  if (themeBtn) {
    themeBtn.textContent = theme === 'light' ? '🌙' : '☀️';
    themeBtn.addEventListener('click', () => {
      const next = document.documentElement.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
      document.documentElement.setAttribute('data-theme', next);
      localStorage.setItem('ycsb-theme', next);
      themeBtn.textContent = next === 'light' ? '🌙' : '☀️';
    });
  }

  // Tab navigation
  document.querySelectorAll('.nav-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const tabId = btn.dataset.tab;
      document.querySelectorAll('.tab-section').forEach(s => s.classList.remove('active'));
      document.getElementById('tab-' + tabId).classList.add('active');
      if (tabId === 'history') History.load();
      if (tabId === 'table') TableWizard.renderCreateFields();
    });
  });

  // Config panel collapse
  const configPanel = document.getElementById('configPanel');
  const configPanelWrapper = document.getElementById('configPanelWrapper');
  const configToggle = document.getElementById('configPanelToggle');
  const resizeHandle = document.getElementById('resizeHandle');
  const splitLayout = configPanel && configPanel.closest('.split-layout');
  if (configToggle && configPanel && configPanelWrapper && splitLayout) {
    configToggle.addEventListener('click', () => {
      const willCollapse = !configPanel.classList.contains('collapsed');
      configPanel.classList.toggle('collapsed');
      configPanelWrapper.classList.toggle('collapsed', configPanel.classList.contains('collapsed'));
      splitLayout.classList.toggle('config-panel-collapsed', configPanel.classList.contains('collapsed'));
      if (willCollapse) configPanelWrapper.style.width = '';
      const isCollapsed = configPanel.classList.contains('collapsed');
      configToggle.textContent = isCollapsed ? '▶' : '◀';
      configToggle.title = isCollapsed ? '打开配置栏' : '收起配置栏';
      configToggle.setAttribute('aria-label', isCollapsed ? '打开配置栏' : '收起配置栏');
    });
  }

  // Step 区域收起/展开（点击标题旁 −/+ 按钮）
  document.addEventListener('click', (e) => {
    const btn = e.target.closest('.section-toggle');
    if (!btn) return;
    const section = btn.closest('.panel-section');
    if (!section) return;
    section.classList.toggle('collapsed');
    const isCollapsed = section.classList.contains('collapsed');
    btn.textContent = isCollapsed ? '+' : '−';
    btn.classList.toggle('collapsed', isCollapsed);
    btn.title = isCollapsed ? '展开' : '收起';
  });

  // Left panel resize handle (horizontal drag) - 调整的是 wrapper 宽度
  const leftPanel = document.querySelector('.split-layout .config-panel-wrapper');
  if (resizeHandle && leftPanel) {
    let startX = 0, startW = 0;
    resizeHandle.addEventListener('mousedown', e => {
      e.preventDefault();
      startX = e.clientX;
      startW = leftPanel.offsetWidth;
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
      function onMove(e) {
        const dx = e.clientX - startX;
        let w = startW + dx;
        const min = 260, max = Math.min(window.innerWidth * 0.6, 800);
        w = Math.max(min, Math.min(max, w));
        leftPanel.style.width = w + 'px';
      }
      function onUp() {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      }
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  }

  // Focus config btn (return to config draft) - removed from UI; keep handler safe if re-added
  const focusConfigBtn = document.getElementById('focusConfigBtn');
  if (focusConfigBtn) {
    focusConfigBtn.addEventListener('click', () => {
      const panel = document.querySelector('.config-panel');
      const wrapper = document.getElementById('configPanelWrapper');
      if (panel && panel.classList.contains('collapsed')) {
        panel.classList.remove('collapsed');
        if (wrapper) wrapper.classList.remove('collapsed');
        if (splitLayout) splitLayout.classList.remove('config-panel-collapsed');
        if (configToggle) { configToggle.textContent = '◀'; configToggle.title = '收起配置栏'; }
      }
      panel && panel.scrollIntoView({ behavior: 'smooth' });
    });
  }

  // Initialize modules
  Config.init();
  Test.init();
  TableWizard.init();
  History.init();

  // 预览配置：复用右侧配置预览逻辑（当前表单生成的 workload 内容）
  const previewConfigBtn = document.getElementById('previewConfigBtn');
  const previewConfigModal = document.getElementById('previewConfigModal');
  const previewConfigContent = document.getElementById('previewConfigContent');
  const previewConfigClose = document.getElementById('previewConfigClose');
  if (previewConfigBtn && previewConfigModal && previewConfigContent) {
    previewConfigBtn.addEventListener('click', () => {
      const content = Config.getCurrentWorkload ? Config.getCurrentWorkload() : '';
      previewConfigContent.textContent = content || '(当前无配置内容)';
      previewConfigModal.classList.remove('hidden');
      document.getElementById('modalOverlay').classList.remove('hidden');
    });
  }
  if (previewConfigClose && previewConfigModal) {
    previewConfigClose.addEventListener('click', () => {
      previewConfigModal.classList.add('hidden');
      document.getElementById('modalOverlay').classList.add('hidden');
    });
  }
  // 点击遮罩时同时关闭保存弹窗与预览弹窗
  const modalOverlay = document.getElementById('modalOverlay');
  if (modalOverlay) {
    modalOverlay.addEventListener('click', () => {
      document.getElementById('saveModal').classList.add('hidden');
      if (previewConfigModal) previewConfigModal.classList.add('hidden');
      modalOverlay.classList.add('hidden');
    });
  }
});
