package com.oceanbase.obkv.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oceanbase.obkv.console.model.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Service for WebSocket communication
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Map of task ID to subscribed WebSocket sessions
     */
    private final Map<String, Set<WebSocketSession>> taskSubscriptions = new ConcurrentHashMap<>();

    /**
     * Subscribe a session to a task
     */
    public void subscribe(String taskId, WebSocketSession session) {
        taskSubscriptions.computeIfAbsent(taskId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("Session {} subscribed to task {}", session.getId(), taskId);
    }

    /**
     * Unsubscribe a session from a task
     */
    public void unsubscribe(String taskId, WebSocketSession session) {
        Set<WebSocketSession> sessions = taskSubscriptions.get(taskId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                taskSubscriptions.remove(taskId);
            }
        }
        log.debug("Session {} unsubscribed from task {}", session.getId(), taskId);
    }

    /**
     * Unsubscribe a session from all tasks
     */
    public void unsubscribeAll(WebSocketSession session) {
        for (Map.Entry<String, Set<WebSocketSession>> entry : taskSubscriptions.entrySet()) {
            entry.getValue().remove(session);
            if (entry.getValue().isEmpty()) {
                taskSubscriptions.remove(entry.getKey());
            }
        }
        log.debug("Session {} unsubscribed from all tasks", session.getId());
    }

    /**
     * Send task log to all subscribed sessions
     */
    public void sendTaskLog(String taskId, String log) {
        sendMessage(taskId, new TaskMessage("LOG", taskId, log, null, null));
    }

    /**
     * Send task status update to all subscribed sessions
     */
    public void sendTaskStatus(String taskId, TaskStatus status, String message) {
        sendMessage(taskId, new TaskMessage("STATUS", taskId, message, status.name(), null));
    }

    /**
     * Send task result to all subscribed sessions
     */
    public void sendTaskResult(String taskId, String result) {
        sendMessage(taskId, new TaskMessage("RESULT", taskId, null, null, result));
    }

    /**
     * Send a message to all sessions subscribed to a task
     */
    private void sendMessage(String taskId, TaskMessage message) {
        Set<WebSocketSession> sessions = taskSubscriptions.get(taskId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(textMessage);
                        }
                    } catch (IOException e) {
                        log.warn("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize message: {}", e.getMessage());
        }
    }

    /**
     * Message structure for WebSocket communication
     */
    public static class TaskMessage {
        public String type;
        public String taskId;
        public String message;
        public String status;
        public String result;
        public long timestamp;

        public TaskMessage(String type, String taskId, String message, String status, String result) {
            this.type = type;
            this.taskId = taskId;
            this.message = message;
            this.status = status;
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
    }
}

