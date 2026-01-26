package com.oceanbase.obkv.console.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oceanbase.obkv.console.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler for task updates
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketService webSocketService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message: {}", payload);

        try {
            JsonNode json = objectMapper.readTree(payload);
            String action = json.has("action") ? json.get("action").asText() : "";
            String taskId = json.has("taskId") ? json.get("taskId").asText() : "";

            switch (action) {
                case "subscribe":
                    if (!taskId.isEmpty()) {
                        webSocketService.subscribe(taskId, session);
                        sendAck(session, "subscribed", taskId);
                        log.info("Session {} subscribed to task {}", session.getId(), taskId);
                    }
                    break;

                case "unsubscribe":
                    if (!taskId.isEmpty()) {
                        webSocketService.unsubscribe(taskId, session);
                        sendAck(session, "unsubscribed", taskId);
                        log.info("Session {} unsubscribed from task {}", session.getId(), taskId);
                    }
                    break;

                default:
                    log.warn("Unknown action: {}", action);
                    sendError(session, "Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("Failed to process message: {}", e.getMessage());
            sendError(session, "Invalid message format");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: {} ({})", session.getId(), status);
        webSocketService.unsubscribeAll(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        webSocketService.unsubscribeAll(session);
    }

    /**
     * Send acknowledgment message
     */
    private void sendAck(WebSocketSession session, String action, String taskId) {
        try {
            String message = String.format("{\"type\":\"ACK\",\"action\":\"%s\",\"taskId\":\"%s\",\"timestamp\":%d}",
                    action, taskId, System.currentTimeMillis());
            synchronized (session) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (Exception e) {
            log.error("Failed to send ACK: {}", e.getMessage());
        }
    }

    /**
     * Send error message
     */
    private void sendError(WebSocketSession session, String error) {
        try {
            String message = String.format("{\"type\":\"ERROR\",\"message\":\"%s\",\"timestamp\":%d}",
                    error, System.currentTimeMillis());
            synchronized (session) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (Exception e) {
            log.error("Failed to send error: {}", e.getMessage());
        }
    }
}

