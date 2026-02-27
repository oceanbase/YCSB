/**
 * SSE connection manager.
 * Maintains a single EventSource per test run (identified by testId).
 * Uses Last-Event-ID for reconnection from the correct offset.
 */
const SSE = (() => {
  const connections = {};   // testId -> EventSource

  function connect(testId, handlers) {
    disconnect(testId);
    const { onLog, onDone, onError } = handlers;
    let lastEventId = 0;

    function createSource() {
      const url = `/api/tests/${testId}/stream`;
      const es = new EventSource(url);
      connections[testId] = es;

      es.addEventListener('log', e => {
        lastEventId = parseInt(e.lastEventId) || lastEventId;
        if (onLog) onLog(e.data);
      });

      es.addEventListener('done', () => {
        if (onDone) onDone();
        es.close();
        delete connections[testId];
      });

      es.addEventListener('error', e => {
        if (es.readyState === EventSource.CLOSED) {
          delete connections[testId];
          if (onError) onError(e);
        }
        // If CONNECTING, browser will auto-retry with Last-Event-ID header
      });
    }

    createSource();
  }

  function disconnect(testId) {
    if (connections[testId]) {
      connections[testId].close();
      delete connections[testId];
    }
  }

  function disconnectAll() {
    Object.keys(connections).forEach(disconnect);
  }

  return { connect, disconnect, disconnectAll };
})();
