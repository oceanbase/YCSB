/**
 * Configuration management module.
 * Handles saved config CRUD and module descriptor loading.
 */
const Config = (() => {
  let currentModule = null;
  let moduleDesc = null;
  let connMode = 'direct'; // 'direct' or 'odp'
  let currentTestType = null;
  let formChangeCallback = null;
  /** 递增序号：丢弃过期的模块 fetch 结果，避免快速切换时状态与下拉框错位 */
  let moduleSwitchSeq = 0;

  // ---- Module loading ----

  /** 模块与 descriptor 已就绪后再刷新建表向导（避免与异步 switchModule 竞态） */
  function refreshTableWizardFields() {
    if (typeof TableWizard !== 'undefined' && TableWizard.renderCreateFields) {
      TableWizard.renderCreateFields();
    }
  }

  async function loadModuleList() {
    const res = await fetch('/api/modules');
    const modules = await res.json();
    const sel = document.getElementById('moduleSelect');
    sel.innerHTML = '';
    modules.forEach(m => {
      const opt = document.createElement('option');
      opt.value = m.moduleId;
      opt.textContent = m.displayName;
      sel.appendChild(opt);
    });
    if (modules.length > 0) {
      await switchModule(modules[0].moduleId);
    }
  }

  async function switchModule(moduleId, skipConfirm) {
    if (currentModule === moduleId) return;
    if (!skipConfirm && currentModule !== null && hasFormContent()) {
      if (!confirm('切换模块将清空当前配置草稿，是否继续？')) {
        document.getElementById('moduleSelect').value = currentModule;
        return;
      }
    }
    const seq = ++moduleSwitchSeq;
    const prevModule = currentModule;
    try {
      const res = await fetch(`/api/modules/${moduleId}`);
      if (seq !== moduleSwitchSeq) return;
      if (!res.ok) {
        const sel = document.getElementById('moduleSelect');
        if (sel && prevModule != null) sel.value = prevModule;
        alert('加载模块信息失败：HTTP ' + res.status);
        return;
      }
      const desc = await res.json();
      if (seq !== moduleSwitchSeq) return;

      moduleDesc = desc;
      currentModule = moduleId;
      const firstDisplay = moduleDesc.testTypes.find(t => t.id !== 'load') || moduleDesc.testTypes[0];
      currentTestType = firstDisplay ? firstDisplay.id : (moduleDesc.testTypes[0] ? moduleDesc.testTypes[0].id : null);
      renderConnFields();
      renderTableModeFields();
      renderTestTypeButtons();
      renderWorkloadFields();
      const tt = moduleDesc.testTypes && moduleDesc.testTypes.find(t => t.id === currentTestType);
      if (tt) updateProportionFields(tt);
      await loadSavedConfigs().catch(err => console.error('loadSavedConfigs', err));
      if (seq !== moduleSwitchSeq) return;
    } finally {
      if (seq === moduleSwitchSeq) {
        refreshTableWizardFields();
      }
    }
  }

  function hasFormContent() {
    const inputs = document.querySelectorAll('#connFields input, #connFields select');
    for (const inp of inputs) {
      if (inp.type !== 'hidden' && inp.value && inp.value.trim()) return true;
    }
    return false;
  }

  // ---- Connection fields ----

  function renderConnFields() {
    const container = document.getElementById('connFields');
    container.innerHTML = '';
    if (!moduleDesc) return;
    const modes = moduleDesc.connectionModes;
    const modeFields = modes[connMode] ? modes[connMode].fields : [];
    const commonFields = modes.common ? modes.common.fields : [];
    [...modeFields, ...commonFields].forEach(f => {
      if (f.type === 'hidden') return;
      appendField(container, f);
    });
  }

  function appendField(container, field) {
    const label = document.createElement('label');
    label.className = 'field-label';
    label.textContent = field.label;

    let input;
    if (field.type === 'boolean') {
      input = document.createElement('input');
      input.type = 'checkbox';
      input.checked = field.defaultValue === 'true';
    } else if (field.type === 'select' && field.options) {
      input = document.createElement('select');
      input.className = 'field-select';
      field.options.forEach(opt => {
        const o = document.createElement('option');
        o.value = opt; o.textContent = opt;
        input.appendChild(o);
      });
      if (field.defaultValue != null) input.value = field.defaultValue;
    } else {
      input = document.createElement('input');
      input.type = field.type === 'password' ? 'password' : (field.type === 'number' ? 'number' : 'text');
      input.className = 'field-input';
      if (field.defaultValue != null) input.value = field.defaultValue;
      if (field.required) input.required = true;
    }
    input.dataset.key = field.key;
    input.addEventListener('input', () => { if (formChangeCallback) formChangeCallback(); });

    container.appendChild(label);
    container.appendChild(input);

    if (field.key === 'obkv.insertType') {
      const hint = document.createElement('div');
      hint.className = 'field-hint';
      hint.style.cssText = 'grid-column:1/-1;color:#d29922;font-size:12px;margin:-4px 0 4px 0';
      hint.textContent = "使用 put 类型需要设置租户变量：set global binlog_row_image='MINIMAL'";
      hint.style.display = (input.value === 'put') ? '' : 'none';
      container.appendChild(hint);
      input.addEventListener('change', () => {
        hint.style.display = (input.value === 'put') ? '' : 'none';
        if (formChangeCallback) formChangeCallback();
      });
    }
  }

  // ---- Table mode fields ----

  function renderTableModeFields() {
    const container = document.getElementById('tableModeFields');
    container.innerHTML = '';
    if (!moduleDesc || !moduleDesc.tableModes) return;

    // 表模式：与「连接方式」同一风格 —— 标签与按钮同一行
    const modeRow = document.createElement('div');
    modeRow.className = 'toggle-row';
    const modeLabel = document.createElement('label');
    modeLabel.textContent = 'key生成方式：';
    modeRow.appendChild(modeLabel);
    const modeToggle = document.createElement('div');
    modeToggle.className = 'toggle-group';
    modeToggle.id = 'tableModeToggle';
    moduleDesc.tableModes.forEach((tm, idx) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'toggle-btn' + (idx === 0 ? ' active' : '');
      btn.textContent = tm.label;
      btn.dataset.modeId = tm.id;
      btn.addEventListener('click', () => {
        if (btn.classList.contains('mode-locked')) {
          const hint = document.getElementById('tableModeHint');
          if (hint) {
            hint.textContent = '二级分区表必须使用 Prefix 模式';
            hint.style.display = 'block';
            clearTimeout(hint._timer);
            hint._timer = setTimeout(() => { hint.style.display = 'none'; }, 3000);
          }
          return;
        }
        document.querySelectorAll('#tableModeToggle .toggle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        renderTableModeExtraFields(tm);
        if (formChangeCallback) formChangeCallback();
      });
      modeToggle.appendChild(btn);
    });
    modeRow.appendChild(modeToggle);
    // hint element for lock error
    const modeHint = document.createElement('div');
    modeHint.id = 'tableModeHint';
    modeHint.style.cssText = 'display:none;color:#f85149;font-size:12px;margin-top:4px;grid-column:1/-1';
    container.appendChild(modeHint);
    container.appendChild(modeRow);

    const extraDiv = document.createElement('div');
    extraDiv.id = 'tableModeExtra';
    extraDiv.className = 'fields-grid';
    container.appendChild(extraDiv);
    if (moduleDesc.tableModes[0]) {
      renderTableModeExtraFields(moduleDesc.tableModes[0]);
    }

    // 分区类型：与「连接方式」同一风格 —— 标签与按钮同一行
    if (moduleDesc.partitionTypes && moduleDesc.partitionTypes.length > 0) {
      const ptRow = document.createElement('div');
      ptRow.className = 'toggle-row toggle-row-spaced';
      const ptLabel = document.createElement('label');
      ptLabel.textContent = '分区类型：';
      ptRow.appendChild(ptLabel);
      const ptToggle = document.createElement('div');
      ptToggle.className = 'toggle-group';
      ptToggle.id = 'partitionTypeGroup';
      moduleDesc.partitionTypes.forEach((pt, idx) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'toggle-btn' + (idx === 0 ? ' active' : '');
        btn.textContent = pt.label;
        btn.dataset.ptId = pt.id;
        btn.addEventListener('click', () => {
          document.querySelectorAll('#partitionTypeGroup .toggle-btn').forEach(b => b.classList.remove('active'));
          btn.classList.add('active');
          renderPartitionFields(pt);
          // 二级分区强制使用 prefix 模式，一级分区恢复所有模式可选
          const isDouble = pt.id === 'double';
          document.querySelectorAll('#tableModeToggle .toggle-btn').forEach(b => {
            const isDefault = b.dataset.modeId === 'default';
            b.classList.toggle('mode-locked', isDouble && isDefault);
            if (isDouble && isDefault && b.classList.contains('active')) {
              // 被锁定的按钮当前是激活态，切换到 prefix
              const prefixBtn = document.querySelector('#tableModeToggle .toggle-btn[data-mode-id="prefix"]');
              if (prefixBtn) prefixBtn.click();
            }
          });
          // 切回一级分区时隐藏提示
          if (!isDouble) {
            const hint = document.getElementById('tableModeHint');
            if (hint) hint.style.display = 'none';
          }
          if (formChangeCallback) formChangeCallback();
        });
        ptToggle.appendChild(btn);
      });
      ptRow.appendChild(ptToggle);
      container.appendChild(ptRow);

      const ptExtra = document.createElement('div');
      ptExtra.id = 'partitionExtra';
      ptExtra.className = 'fields-grid mt-8';
      container.appendChild(ptExtra);
      if (moduleDesc.partitionTypes[0]) renderPartitionFields(moduleDesc.partitionTypes[0]);
    }
  }

  function renderTableModeExtraFields(tm) {
    const container = document.getElementById('tableModeExtra');
    if (!container) return;
    container.innerHTML = '';
    (tm.fields || []).forEach(f => appendField(container, f));
  }

  function renderPartitionFields(pt) {
    const container = document.getElementById('partitionExtra');
    if (!container) return;
    container.innerHTML = '';
    (pt.fields || []).forEach(f => appendField(container, f));
  }

  // ---- Test type buttons ----

  function renderTestTypeButtons() {
    const container = document.getElementById('testTypeButtons');
    container.innerHTML = '';
    if (!moduleDesc || !moduleDesc.testTypes) return;

    const types = moduleDesc.testTypes.filter(tt => tt.id !== 'load');
    const customOption = { id: 'custom', label: '自定义', isLoad: false, proportions: null };
    const displayTypes = [...types, customOption];

    displayTypes.forEach((tt, idx) => {
      const btn = document.createElement('button');
      btn.className = 'btn-group-item' + (idx === 0 ? ' active' : '');
      btn.textContent = tt.label;
      btn.dataset.typeId = tt.id;
      btn.addEventListener('click', () => {
        document.querySelectorAll('#testTypeButtons .btn-group-item').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        currentTestType = tt.id;
        renderExtraFields(tt.id === 'custom' ? {} : tt);
        if (tt.id === 'custom') {
          clearWorkloadFieldsForCustom();
          updateProportionDetailsOpen(true);
        } else {
          updateProportionFields(tt);
          updateProportionDetailsOpen(false);
        }
        if (formChangeCallback) formChangeCallback();
      });
      container.appendChild(btn);
    });

    const first = displayTypes[0];
    currentTestType = first.id;
    renderExtraFields(first.id === 'custom' ? {} : first);
    if (first.id === 'custom') {
      clearWorkloadFieldsForCustom();
      updateProportionDetailsOpen(true);
    } else {
      updateProportionFields(first);
      updateProportionDetailsOpen(false);
    }
  }

  function updateProportionDetailsOpen(isCustom) {
    const details = document.getElementById('proportionDetails');
    if (details) details.open = !!isCustom;
  }

  function clearWorkloadFieldsForCustom() {
    const keys = ['recordcount','operationcount','threadcount','fieldcount','fieldlength','requestdistribution'];
    keys.forEach(k => {
      const el = document.querySelector(`[data-key="${k}"]`);
      if (el) { el.value = k === 'requestdistribution' ? 'uniform' : ''; }
    });
    const proportionKeys = ['read','update','insert','scan','readmodifywrite','batchput','batchread'];
    proportionKeys.forEach(k => {
      const el = document.querySelector(`[data-key="${k}"]`);
      if (el) el.value = '';
    });
  }

  function updateProportionFields(tt) {
    const proportionKeys = ['readproportion','updateproportion','insertproportion','scanproportion','readmodifywriteproportion','batchputproportion','batchreadproportion'];
    const defaults = (tt && tt.proportions) ? tt.proportions : {};
    proportionKeys.forEach(k => {
      const el = document.querySelector(`[data-key="${k}"]`);
      if (el) el.value = defaults[k] != null ? String(defaults[k]) : '0';
    });
  }

  function renderExtraFields(testTypeOrEmpty) {
    const container = document.getElementById('extraFields');
    container.innerHTML = '';
    const tt = testTypeOrEmpty && testTypeOrEmpty.extraFields ? testTypeOrEmpty : {};
    (tt.extraFields || []).forEach(f => appendField(container, f));
  }

  function renderWorkloadFields() {
    const container = document.getElementById('workloadFields');
    container.innerHTML = '';
    const basicFields = [
      { key: 'recordcount',         label: '记录总数',     type: 'number', defaultValue: '100000' },
      { key: 'operationcount',      label: '操作次数',     type: 'number', defaultValue: '100000' },
      { key: 'threadcount',         label: '线程数',       type: 'number', defaultValue: '10' },
      { key: 'fieldcount',          label: '字段数',       type: 'number', defaultValue: '10' },
      { key: 'fieldlength',         label: '字段长度',     type: 'number', defaultValue: '100' },
      { key: 'requestdistribution', label: '请求分布',     type: 'select', options: ['uniform','zipfian','hotspot','sequential','exponential'], defaultValue: 'uniform' },
    ];
    basicFields.forEach(f => appendField(container, f));

    const details = document.createElement('details');
    details.id = 'proportionDetails';
    details.className = 'proportion-details';
    details.open = false;
    const summary = document.createElement('summary');
    summary.className = 'proportion-details-summary';
    summary.textContent = '比例设置';
    details.appendChild(summary);
    const proportionWrap = document.createElement('div');
    proportionWrap.className = 'fields-grid mt-8';
    const proportionFields = [
      { key: 'readproportion',      label: 'read',   type: 'number', defaultValue: '0' },
      { key: 'updateproportion',    label: 'update', type: 'number', defaultValue: '0' },
      { key: 'insertproportion',    label: 'insert', type: 'number', defaultValue: '0' },
      { key: 'scanproportion',      label: 'scan',   type: 'number', defaultValue: '0' },
      { key: 'readmodifywriteproportion', label: 'readmodifywrite', type: 'number', defaultValue: '0' },
      { key: 'batchputproportion',  label: 'batchput',  type: 'number', defaultValue: '0' },
      { key: 'batchreadproportion', label: 'batchread', type: 'number', defaultValue: '0' },
    ];
    proportionFields.forEach(f => appendField(proportionWrap, f));
    details.appendChild(proportionWrap);
    container.appendChild(details);
  }

  // ---- Form data collection ----

  function getConnModeValue() {
    return connMode === 'odp' ? 'true' : 'false';
  }

  function collectAllFormParams() {
    const params = {};
    params['workload'] = 'site.ycsb.workloads.CoreWorkload';

    const PROPORTION_KEYS = ['readproportion','updateproportion','insertproportion','scanproportion','readmodifywriteproportion','batchputproportion','batchreadproportion'];

    // Common fields
    document.querySelectorAll('[data-key]').forEach(el => {
      const key = el.dataset.key;
      if (!key) return;
      if (el.type === 'checkbox') {
        params[key] = el.checked ? 'true' : 'false';
      } else if (el.type === 'password') {
        // Always include password keys even when empty, so getProperty() in Java
        // returns "" instead of null and avoids IllegalArgumentException
        params[key] = el.value;
      } else if (PROPORTION_KEYS.includes(key)) {
        // Always include proportion keys (use '0' when empty) so workload is explicit
        params[key] = (el.value != null && el.value.trim() !== '') ? el.value.trim() : '0';
      } else if (el.value && el.value.trim()) {
        params[key] = el.value.trim();
      }
    });

    // ODP mode flag
    if (moduleDesc) {
      const modes = moduleDesc.connectionModes;
      if (modes && modes.common) {
        modes.common.fields.forEach(f => {
          if (f.type === 'hidden') params[f.key] = connMode === 'odp' ? 'true' : 'false';
        });
      }
      // Fixed values from current table mode
      const activeModeBtn = document.querySelector('#tableModeToggle .toggle-btn.active');
      if (activeModeBtn && moduleDesc.tableModes) {
        const tm = moduleDesc.tableModes.find(m => m.id === activeModeBtn.dataset.modeId);
        if (tm && tm.fixedValues) {
          Object.entries(tm.fixedValues).forEach(([k, v]) => { params[k] = v; });
        }
      }
    }
    // Do NOT overwrite proportions from tt.proportions - use form values from proportion fields

    // Custom params
    document.querySelectorAll('.custom-param-row').forEach(row => {
      const kInput = row.querySelector('.cp-key');
      const vInput = row.querySelector('.cp-val');
      if (kInput && vInput && kInput.value.trim()) {
        params[kInput.value.trim()] = vInput.value;
      }
    });

    return params;
  }

  function buildWorkloadContent(params, testType, module, isLoad) {
    const lines = [];
    lines.push(`# module=${module}  testType=${testType}  generated=${new Date().toISOString()}`);
    if (isLoad) lines.push('# Note: -load flag will be appended when this test runs');
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== '') lines.push(`${k}=${v}`);
    });
    return lines.join('\n') + '\n';
  }

  function getCurrentWorkload() {
    return buildWorkloadContent(collectAllFormParams(), currentTestType, currentModule, isLoadTest());
  }

  function getActiveTestType() { return currentTestType; }
  function getActiveModule()   { return currentModule; }
  function getModuleDesc()     { return moduleDesc; }
  function getConnMode()       { return connMode; }

  function isLoadTest() {
    if (!moduleDesc || !currentTestType) return false;
    if (currentTestType === 'custom') return false;
    const tt = moduleDesc.testTypes.find(t => t.id === currentTestType);
    return tt ? tt.isLoad : false;
  }

  function getActiveTableMode() {
    const btn = document.querySelector('#tableModeToggle .toggle-btn.active');
    return btn ? btn.dataset.modeId : null;
  }

  // ---- Saved configs ----

  async function loadSavedConfigs() {
    const res = await fetch('/api/configs');
    const configs = await res.json();
    const sel = document.getElementById('savedConfigSelect');
    sel.innerHTML = '<option value="">-- 选择配置 --</option>';
    configs.forEach(c => {
      if (!currentModule || c.module === currentModule) {
        const opt = document.createElement('option');
        opt.value = c.name;
        opt.textContent = c.name;
        sel.appendChild(opt);
      }
    });
  }

  async function loadConfig(name) {
    const res = await fetch(`/api/configs/${name}`);
    if (!res.ok) { alert('配置不存在'); return; }
    const data = await res.json();
    if (data.module && data.module !== currentModule) {
      await switchModule(data.module, true);
      document.getElementById('moduleSelect').value = data.module;
    }
    await parseWorkloadToForm(data.workloadContent || '');
  }

  /**
   * Parse workload content (properties + first-line comment) and apply to form.
   * Sets module, connection mode, table mode, partition type, test type, then all [data-key] fields and custom params.
   */
  async function parseWorkloadToForm(content) {
    const props = {};
    let metaModule = null;
    let metaTestType = null;

    content.split('\n').forEach((line, idx) => {
      const trimmed = line.trim();
      if (idx === 0 && trimmed.startsWith('#')) {
        const mModule = trimmed.match(/module=(\S+)/);
        const mTestType = trimmed.match(/testType=(\S+)/);
        if (mModule) metaModule = mModule[1];
        if (mTestType) metaTestType = mTestType[1];
        return;
      }
      if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('__webui.')) return;
      const eq = trimmed.indexOf('=');
      if (eq > 0) {
        props[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim();
      }
    });

    if (metaModule && metaModule !== currentModule) {
      await switchModule(metaModule, true);
      const sel = document.getElementById('moduleSelect');
      if (sel) sel.value = metaModule;
    }

    if (moduleDesc) {
      // Fix 1: find odpMode key dynamically from module descriptor instead of hardcoding obkv-hbase key
      const hiddenField = moduleDesc.connectionModes && moduleDesc.connectionModes.common
        ? moduleDesc.connectionModes.common.fields.find(f => f.type === 'hidden')
        : null;
      const odpModeKey = hiddenField ? hiddenField.key : null;
      const connModeFromProps = odpModeKey && props[odpModeKey] === 'true' ? 'odp' : 'direct';
      if (connModeFromProps !== connMode) {
        connMode = connModeFromProps;
        document.querySelectorAll('#connModeToggle .toggle-btn').forEach(b => {
          b.classList.toggle('active', b.dataset.mode === connMode);
        });
        renderConnFields();
      }

      // Fix 2: detect table mode generically via fixedValues or unique fields in module descriptor
      let matchedModeId = null;
      for (const tm of moduleDesc.tableModes || []) {
        if (tm.fixedValues && Object.keys(tm.fixedValues).length > 0) {
          if (Object.entries(tm.fixedValues).every(([k, v]) => props[k] === String(v))) {
            matchedModeId = tm.id;
            break;
          }
        } else if (tm.fields && tm.fields.length > 0) {
          if (tm.fields.some(f => props[f.key] !== undefined)) {
            matchedModeId = tm.id;
            break;
          }
        }
      }
      if (matchedModeId) {
        const tableModeBtn = document.querySelector(`#tableModeToggle .toggle-btn[data-mode-id="${matchedModeId}"]`);
        if (tableModeBtn && !tableModeBtn.classList.contains('active')) {
          tableModeBtn.click();
        }
      }

      if (moduleDesc.partitionTypes && moduleDesc.partitionTypes.length > 0) {
        const hasRange = props['obkv.rangePartitionStartTs'] != null && String(props['obkv.rangePartitionStartTs']).trim() !== '';
        const ptId = hasRange ? 'double' : 'single';
        const ptBtn = document.querySelector(`#partitionTypeGroup .toggle-btn[data-pt-id="${ptId}"]`);
        if (ptBtn && !ptBtn.classList.contains('active')) {
          ptBtn.click();
        }
      }

      if (metaTestType) {
        const ttBtn = document.querySelector(`#testTypeButtons .btn-group-item[data-type-id="${metaTestType}"]`);
        if (ttBtn && !ttBtn.classList.contains('active')) {
          ttBtn.click();
        } else if (metaTestType === 'load' && !ttBtn) {
          const loadTt = moduleDesc.testTypes.find(t => t.id === 'load');
          if (loadTt) {
            currentTestType = 'load';
            renderExtraFields(loadTt);
            updateProportionFields(loadTt);
            updateProportionDetailsOpen(false);
          }
        }
      }
    }

    // Fix 3: replace broad knownParams exclusion with a minimal skip-set so that params
    // which have no form field but were added as custom params can be correctly restored.
    // Only exclude: the internal workload class key, fixedValues keys (handled by tableMode
    // button clicks), and hidden connection-mode fields (already applied above).
    const skipAsCustom = new Set(['workload']);
    if (moduleDesc) {
      (moduleDesc.tableModes || []).forEach(tm => {
        if (tm.fixedValues) Object.keys(tm.fixedValues).forEach(k => skipAsCustom.add(k));
      });
      const commonFields = moduleDesc.connectionModes && moduleDesc.connectionModes.common
        ? moduleDesc.connectionModes.common.fields : [];
      commonFields.filter(f => f.type === 'hidden').forEach(f => skipAsCustom.add(f.key));
    }
    const customParams = {};

    Object.entries(props).forEach(([k, v]) => {
      const el = document.querySelector(`[data-key="${k}"]`);
      if (el) {
        if (el.type === 'checkbox') el.checked = v === 'true';
        else el.value = v;
      } else if (!skipAsCustom.has(k)) {
        customParams[k] = v;
      }
    });

    const container = document.getElementById('customParamRows');
    if (container) {
      container.innerHTML = '';
      Object.entries(customParams).forEach(([k, v]) => addCustomParamRow(k, v));
    }

    if (formChangeCallback) formChangeCallback();
  }

  async function saveConfig(name) {
    const content = buildWorkloadContent(collectAllFormParams(), currentTestType, currentModule, false);
    const res = await fetch(`/api/configs/${name}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ module: currentModule, testType: currentTestType, workloadContent: content })
    });
    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.error || 'Save failed');
    }
    await loadSavedConfigs();
  }

  async function deleteConfig(name) {
    await fetch(`/api/configs/${name}`, { method: 'DELETE' });
    await loadSavedConfigs();
  }

  function addCustomParamRow(key, value) {
    const container = document.getElementById('customParamRows');
    const row = document.createElement('div');
    row.className = 'custom-param-row';
    row.innerHTML = `
      <input type="text" class="cp-key" placeholder="参数名" value="${escHtml(key || '')}">
      <input type="text" class="cp-val" placeholder="值" value="${escHtml(value || '')}">
      <button class="remove-param-btn">×</button>
    `;
    row.querySelector('.remove-param-btn').addEventListener('click', () => {
      row.remove();
      if (formChangeCallback) formChangeCallback();
    });
    row.querySelector('.cp-key').addEventListener('input', () => { if (formChangeCallback) formChangeCallback(); });
    row.querySelector('.cp-val').addEventListener('input', () => { if (formChangeCallback) formChangeCallback(); });
    container.appendChild(row);
  }

  function escHtml(s) {
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  }

  function onFormChange(cb) { formChangeCallback = cb; }

  // ---- Init ----

  function init() {
    document.getElementById('moduleSelect').addEventListener('change', e => {
      switchModule(e.target.value);
    });

    document.querySelectorAll('#connModeToggle .toggle-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#connModeToggle .toggle-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        connMode = btn.dataset.mode;
        renderConnFields();
        if (formChangeCallback) formChangeCallback();
      });
    });

    document.getElementById('addCustomParamBtn').addEventListener('click', () => {
      addCustomParamRow('', '');
    });

    document.getElementById('loadConfigBtn').addEventListener('click', () => {
      const name = document.getElementById('savedConfigSelect').value;
      if (name) loadConfig(name);
    });

    document.getElementById('newConfigBtn').addEventListener('click', () => {
      document.querySelectorAll('[data-key]').forEach(el => {
        if (el.type === 'checkbox') el.checked = false;
        else el.value = el.dataset.defaultValue || '';
      });
      document.getElementById('customParamRows').innerHTML = '';
      document.getElementById('savedConfigSelect').value = '';
      if (formChangeCallback) formChangeCallback();
    });

    document.getElementById('saveConfigBtn').addEventListener('click', () => {
      showSaveModal();
    });

    document.getElementById('deleteConfigBtn').addEventListener('click', async () => {
      const name = document.getElementById('savedConfigSelect').value;
      if (!name) return;
      if (confirm(`确认删除配置「${name}」？`)) {
        await deleteConfig(name);
        alert('已删除');
      }
    });

    // Save modal
    document.getElementById('saveModalConfirm').addEventListener('click', async () => {
      const name = document.getElementById('saveConfigName').value.trim();
      const errEl = document.getElementById('saveModalError');
      errEl.textContent = '';
      if (!name) { errEl.textContent = '请输入配置名'; return; }
      if (!/^[a-zA-Z0-9_\-]{1,100}$/.test(name)) {
        errEl.textContent = '名称只允许字母、数字、下划线、连字符（1-100位）';
        return;
      }
      try {
        await saveConfig(name);
        hideSaveModal();
        alert('配置已保存：' + name);
      } catch (e) {
        errEl.textContent = e.message;
      }
    });
    document.getElementById('saveModalCancel').addEventListener('click', hideSaveModal);
    document.getElementById('modalOverlay').addEventListener('click', hideSaveModal);

    loadModuleList();
  }

  function showSaveModal() {
    document.getElementById('saveConfigName').value = '';
    document.getElementById('saveModalError').textContent = '';
    document.getElementById('saveModal').classList.remove('hidden');
    document.getElementById('modalOverlay').classList.remove('hidden');
    document.getElementById('saveConfigName').focus();
  }

  function hideSaveModal() {
    document.getElementById('saveModal').classList.add('hidden');
    document.getElementById('modalOverlay').classList.add('hidden');
  }

  return {
    init,
    switchModule,
    getActiveModule,
    getActiveTestType,
    getActiveTableMode,
    getModuleDesc,
    isLoadTest,
    collectAllFormParams,
    buildWorkloadContent,
    parseWorkloadToForm,
    addCustomParamRow,
    onFormChange,
    loadSavedConfigs,
    getCurrentWorkload,
  };
})();
