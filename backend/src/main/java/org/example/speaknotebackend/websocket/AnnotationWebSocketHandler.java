package org.example.speaknotebackend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.dto.AnnotationMessage;
import org.example.speaknotebackend.service.AnnotationService;
import org.example.speaknotebackend.service.GptService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnnotationWebSocketHandler extends TextWebSocketHandler {

    private final GptService gptService;
    private final AnnotationService annotationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info(" WebSocket 연결됨: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {

        try {

            AnnotationMessage annotationMessage = objectMapper.readValue(message.getPayload(), AnnotationMessage.class);
            String sessionId = session.getId();
            String originalText = annotationMessage.getText();

            log.info("수신 메시지: {}", originalText);

            gptService.refineTextAsync(originalText)
                    .thenAccept(refinedText -> {
                        try {
                            // MongoDB 저장
                            annotationService.saveAnnotation(
                                    sessionId,
                                    originalText,
                                    refinedText,
                                    annotationMessage.getPageNumber(),
                                    annotationMessage.getX(),
                                    annotationMessage.getY()
                            );

                            // ️응답 메시지 구성
                            AnnotationMessage response = new AnnotationMessage();
                            response.setText(refinedText);
                            response.setPageNumber(annotationMessage.getPageNumber());
                            response.setX(annotationMessage.getX());
                            response.setY(annotationMessage.getY());

                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                        } catch (Exception e) {
                            log.error(" 응답 전송 중 오류", e);
                        }
                    });

        } catch (Exception e) {
            log.error(" 메시지 처리 실패", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket 연결 종료: {}", session.getId());
    }
}
