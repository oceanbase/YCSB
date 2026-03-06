/**
 * Table creation wizard module.
 */
const TableWizard = (() => {
  let generatedSql = '';

  function init() {
    document.getElementById('generateSqlBtn').addEventListener('click', generateSql);
    document.getElementById('executeSqlBtn').addEventListener('click', executeSql);
    document.getElementById('testConnBtn').addEventListener('click', testConnection);
    document.getElementById('copySqlBtn').addEventListener('click', copySql);

    // Watch module changes
    document.getElementById('moduleSelect').addEventListener('change', () => {
      renderCreateFields();
    });
    renderCreateFields();
  }

  function renderCreateFields() {
    const module = Config.getActiveModule();
    const desc = Config.getModuleDesc();
    const container = document.getElementById('tableCreateFields');
    container.innerHTML = '';
    if (!desc) return;

    if (module === 'obkv-hbase') {
      renderHbaseFields(container);
    } else if (module === 'obkv-table') {
      renderTableFields(container);
    }
  }

  function addRow(container, labelText, inputEl) {
    const label = document.createElement('label');
    label.className = 'field-label';
    label.textContent = labelText;
    container.appendChild(label);
    container.appendChild(inputEl);
  }

  function makeInput(id, type, defaultVal, placeholder) {
    const inp = document.createElement('input');
    inp.id = id;
    inp.type = type || 'text';
    inp.className = 'field-input';
    if (defaultVal != null) inp.value = defaultVal;
    if (placeholder) inp.placeholder = placeholder;
    return inp;
  }

  function makeSelect(id, options, defaultVal) {
    const sel = document.createElement('select');
    sel.id = id;
    sel.className = 'field-select';
    options.forEach(([v, l]) => {
      const o = document.createElement('option');
      o.value = v; o.textContent = l;
      if (v === defaultVal) o.selected = true;
      sel.appendChild(o);
    });
    return sel;
  }

  function renderHbaseFields(container) {
    addRow(container, '表模型', makeSelect('tc-type', [['hbase','HBase (KQTV)'],['ts','TimeSeries (KTSV)']], 'hbase'));
    addRow(container, '表名',   makeInput('tc-table-name', 'text', 'ycsb_test'));
    addRow(container, '列族',   makeInput('tc-family', 'text', 'cf'));

    const partSel = makeSelect('tc-part-mode', [['first_part','一级分区'],['sec_part','二级分区']], 'first_part');
    addRow(container, '分区层级', partSel);

    // Single partition fields
    const spDiv = document.createElement('div');
    spDiv.id = 'tc-sp-fields';
    spDiv.className = 'fields-grid';
    spDiv.style.cssText = 'grid-column:1/-1;';
    container.appendChild(spDiv);
    renderHbaseSinglePartFields(spDiv);

    // Double partition fields
    const dpDiv = document.createElement('div');
    dpDiv.id = 'tc-dp-fields';
    dpDiv.className = 'fields-grid';
    dpDiv.style.cssText = 'grid-column:1/-1;display:none;';
    container.appendChild(dpDiv);
    renderHbaseDoublePartFields(dpDiv);

    partSel.addEventListener('change', () => {
      const isSingle = partSel.value === 'first_part';
      spDiv.style.display = isSingle ? '' : 'none';
      dpDiv.style.display = isSingle ? 'none' : '';
    });
  }

  function renderHbaseSinglePartFields(container) {
    addRow(container, '分区类型', makeSelect('tc-sp-partition-type', [['range','range'],['key','key']], 'range'));
    addRow(container, '分区数量 *', makeInput('tc-sp-partition-count', 'number', '', '必填，如 128'));
    addRow(container, '最大 Key', makeInput('tc-sp-max-key', 'number', '9223372036854775807'));
    addRow(container, 'Key 长度', makeInput('tc-sp-key-length', 'number', '20'));

    const ptSel      = document.getElementById('tc-sp-partition-type');
    const maxKeyEl   = document.getElementById('tc-sp-max-key');
    const keyLenEl   = document.getElementById('tc-sp-key-length');

    function toggleRangeOnlyFields() {
      const hide = ptSel.value === 'key';
      [maxKeyEl, maxKeyEl.previousElementSibling,
       keyLenEl, keyLenEl.previousElementSibling].forEach(el => {
        el.style.display = hide ? 'none' : '';
      });
    }

    ptSel.addEventListener('change', toggleRangeOnlyFields);
  }

  function renderHbaseDoublePartFields(container) {
    addRow(container, 'Range 分区数 *',  makeInput('tc-dp-range-count', 'number', '', '必填，如 30'));
    addRow(container, '起始时间戳(ms) *',makeInput('tc-dp-start-ts', 'number', '', '必填，如 1704067200000'));
    addRow(container, '分区时长(ms) *',  makeInput('tc-dp-duration', 'number', '', '必填，如 86400000'));
    addRow(container, 'Key 子分区数 *',  makeInput('tc-dp-key-sub', 'number', '', '必填，如 40'));
  }

  function renderTableFields(container) {
    addRow(container, '字段数', makeInput('tc-fields', 'number', '1'));
    const modeSel = makeSelect('tc-table-mode', [['range','range'],['key','key'],['key_range','key_range']], 'range');
    addRow(container, '分区模式', modeSel);

    const rangeDiv = document.createElement('div');
    rangeDiv.id = 'tc-range-fields';
    rangeDiv.className = 'fields-grid';
    rangeDiv.style.cssText = 'grid-column:1/-1;';
    container.appendChild(rangeDiv);
    renderTableRangeFields(rangeDiv);

    const keyDiv = document.createElement('div');
    keyDiv.id = 'tc-key-fields';
    keyDiv.className = 'fields-grid';
    keyDiv.style.cssText = 'grid-column:1/-1;display:none;';
    container.appendChild(keyDiv);
    renderTableKeyFields(keyDiv);

    const rkDiv = document.createElement('div');
    rkDiv.id = 'tc-rk-fields';
    rkDiv.className = 'fields-grid';
    rkDiv.style.cssText = 'grid-column:1/-1;display:none;';
    container.appendChild(rkDiv);
    renderTableRangeKeyFields(rkDiv);

    modeSel.addEventListener('change', () => {
      const val = modeSel.value;
      rangeDiv.style.display = val === 'range' ? '' : 'none';
      keyDiv.style.display   = val === 'key' ? '' : 'none';
      rkDiv.style.display    = val === 'key_range' ? '' : 'none';
    });
  }

  function renderTableRangeFields(container) {
    addRow(container, '分区数量 *',   makeInput('tc-r-num-parts', 'number', '', '必填，如 128'));
    addRow(container, '最大 Key *',   makeInput('tc-r-max-key', 'number', '9223372036854775807'));
    addRow(container, 'Key 长度',     makeInput('tc-r-key-length', 'number', '20'));
  }

  function renderTableKeyFields(container) {
    addRow(container, '分区数量 *',   makeInput('tc-k-num-parts', 'number', '', '必填，如 128'));
  }

  function renderTableRangeKeyFields(container) {
    addRow(container, 'Range 分区数 *', makeInput('tc-rk-range-count', 'number', '', '必填，如 30'));
    addRow(container, 'Key 子分区数 *', makeInput('tc-rk-key-sub', 'number', '', '必填，如 40'));
    addRow(container, '起始时间戳 *',   makeInput('tc-rk-start-ts', 'number', '', '必填，如 1704067200000'));
    addRow(container, '分区时长(ms) *', makeInput('tc-rk-duration', 'number', '', '必填，如 86400000'));
  }

  function collectHbaseParams() {
    const mode = document.getElementById('tc-part-mode').value;
    const params = {
      type:       document.getElementById('tc-type').value,
      table_name: document.getElementById('tc-table-name').value,
      family:     document.getElementById('tc-family').value,
      mode:       mode,
    };
    if (mode === 'first_part') {
      const partitionType = document.getElementById('tc-sp-partition-type').value;
      Object.assign(params, {
        partition_type:  partitionType,
        partition_count: document.getElementById('tc-sp-partition-count').value,
        ...(partitionType === 'range' ? {
          max_key:    document.getElementById('tc-sp-max-key').value,
          key_length: document.getElementById('tc-sp-key-length').value,
        } : {}),
      });
    } else {
      Object.assign(params, {
        range_partition_count:    document.getElementById('tc-dp-range-count').value,
        range_start_timestamp:    document.getElementById('tc-dp-start-ts').value,
        range_partition_duration_ms: document.getElementById('tc-dp-duration').value,
        key_subpartition_count:   document.getElementById('tc-dp-key-sub').value,
      });
    }
    return params;
  }

  function collectTableParams() {
    const mode = document.getElementById('tc-table-mode').value;
    const params = {
      mode:   mode,
      fields: document.getElementById('tc-fields').value,
    };
    if (mode === 'range') {
      Object.assign(params, {
        num_partitions: document.getElementById('tc-r-num-parts').value,
        max_key:        document.getElementById('tc-r-max-key').value,
        key_length:     document.getElementById('tc-r-key-length').value,
      });
    } else if (mode === 'key') {
      Object.assign(params, {
        num_partitions: document.getElementById('tc-k-num-parts').value,
      });
    } else {
      Object.assign(params, {
        range_partition_count:   document.getElementById('tc-rk-range-count').value,
        key_subpartition_count:  document.getElementById('tc-rk-key-sub').value,
        start_timestamp:         document.getElementById('tc-rk-start-ts').value,
        partition_duration_ms:   document.getElementById('tc-rk-duration').value,
      });
    }
    return params;
  }

  function validateParams(module, params) {
    const errors = [];
    if (module === 'obkv-hbase') {
      const isDouble = params.mode === 'sec_part';
      if (!isDouble) {
        if (!params.partition_count) errors.push('分区数量 不能为空');
      } else {
        if (!params.range_partition_count)    errors.push('Range 分区数 不能为空');
        if (!params.range_start_timestamp)    errors.push('起始时间戳 不能为空');
        if (!params.range_partition_duration_ms) errors.push('分区时长 不能为空');
        if (!params.key_subpartition_count)   errors.push('Key 子分区数 不能为空');
      }
    } else if (module === 'obkv-table') {
      if (params.mode === 'range') {
        if (!params.num_partitions) errors.push('分区数量 不能为空');
        if (!params.max_key)        errors.push('最大 Key 不能为空');
      } else if (params.mode === 'key') {
        if (!params.num_partitions) errors.push('分区数量 不能为空');
      } else {
        if (!params.range_partition_count)  errors.push('Range 分区数 不能为空');
        if (!params.key_subpartition_count) errors.push('Key 子分区数 不能为空');
        if (!params.start_timestamp)        errors.push('起始时间戳 不能为空');
        if (!params.partition_duration_ms)  errors.push('分区时长 不能为空');
      }
    }
    return errors;
  }

  async function generateSql() {
    const module = Config.getActiveModule();
    const params = module === 'obkv-hbase' ? collectHbaseParams() : collectTableParams();

    const errors = validateParams(module, params);
    if (errors.length > 0) {
      showResult('sqlExecResult', false, '请填写必填参数：' + errors.join('；'));
      return;
    }

    const btn = document.getElementById('generateSqlBtn');
    btn.disabled = true;
    btn.textContent = '生成中…';
    try {
      const res = await fetch('/api/table/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ module, params }),
      });
      const data = await res.json();
      if (data.success) {
        generatedSql = data.sql;
        document.getElementById('sqlPreview').value = generatedSql;
        showResult('sqlExecResult', true, 'SQL 生成成功');
      } else {
        showResult('sqlExecResult', false, data.message || 'SQL 生成失败');
      }
    } catch (e) {
      showResult('sqlExecResult', false, e.message);
    } finally {
      btn.disabled = false;
      btn.textContent = '生成 SQL';
    }
  }

  async function executeSql() {
    const sql = document.getElementById('sqlPreview').value.trim();
    if (!sql) { alert('请先生成 SQL'); return; }
    const btn = document.getElementById('executeSqlBtn');
    btn.disabled = true;
    btn.textContent = '执行中…';
    try {
      const res = await fetch('/api/table/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(getConnConfig(sql)),
      });
      const data = await res.json();
      showResult('sqlExecResult', data.success, data.message || (data.success ? '建表成功' : '执行失败'));
    } catch (e) {
      showResult('sqlExecResult', false, e.message);
    } finally {
      btn.disabled = false;
      btn.textContent = '执行建表';
    }
  }

  async function testConnection() {
    const btn = document.getElementById('testConnBtn');
    btn.disabled = true;
    btn.textContent = '测试中…';
    try {
      const res = await fetch('/api/table/test-connection', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(getConnConfig('')),
      });
      const data = await res.json();
      showResult('sqlExecResult', data.success, data.message);
    } catch (e) {
      showResult('sqlExecResult', false, e.message);
    } finally {
      btn.disabled = false;
      btn.textContent = '测试连接';
    }
  }

  function getConnConfig(sql) {
    return {
      host:     document.getElementById('dbHost').value,
      port:     parseInt(document.getElementById('dbPort').value) || 2881,
      username: document.getElementById('dbUser').value,
      password: document.getElementById('dbPass').value,
      database: document.getElementById('dbName').value,
      sql,
      dropIfExists: document.getElementById('dropIfExists') ? document.getElementById('dropIfExists').checked : true,
    };
  }

  function copySql() {
    const sql = document.getElementById('sqlPreview').value;
    if (sql) navigator.clipboard.writeText(sql);
  }

  function showResult(elId, success, msg) {
    const el = document.getElementById(elId);
    if (!el) return;
    el.className = 'result-msg mt-8 ' + (success ? 'success' : 'error');
    el.textContent = msg;
  }

  return { init, renderCreateFields };
})();
