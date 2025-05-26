package org.example.speaknotebackend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.service.TextRefineService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioWebSocketHandler extends BinaryWebSocketHandler {

    private final TextRefineService textRefineService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile String testText = null;


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("클라이언트 WebSocket 연결됨: {}", session.getId());

        // (임시) 5초마다 한 번씩 AI 서버에 POST 요청
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (session.isOpen() && testText != null) {
                    String refinedText = textRefineService.refine(testText);
                    session.sendMessage(new TextMessage(refinedText));
                    log.info("정제된 텍스트 전송 완료: {}", refinedText);
                }
            } catch (Exception e) {
                log.error("5초 주기 정제 처리 중 오류", e);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    @Override
    protected void handleBinaryMessage(
            WebSocketSession session,
            BinaryMessage message) throws Exception {
        ByteBuffer payload = message.getPayload();
        byte[] audioBytes = new byte[payload.remaining()];
        payload.get(audioBytes);

        log.info("오디오 chunk 수신 ({} bytes)", audioBytes.length);

        // TODO: Google STT에 byte[] 전송
        // TODO: Google STT 연결 예정

        // (임시) 하드코딩된 텍스트 사용
        testText =
                 """
                 자동차가 그래서 이걸 이제 마이그레이션이라고 부르는 거 하나의 물리 머신에서 동작하고 있던 게 계속 이렇게 이동을 하는 거 그리고 마이그레이션이 일반적인 클라우드 컴퓨팅 환경에서는 이제 물리 머신의 유지 보수 타입이 됐다거나 아니면 크리티컬한 장애가 발생했다거나 이제 그런 순간이 왔을 때 하나의 물리 머신에서 동작하고 있던 가상 머신을 또는 가상 자원을 다른 올리 머신으로 옮기는 거 그걸 이제 마이그레이션이라고 하죠.
                 그래서 아마 대부분 설명드린 내용이 중복일 거니까 아마 디테일하게 설명드릴 추가로 설명드릴 부분은 없을 것 같네요.
                 그래서 나중에 저희가 AWS랑 ms 에저에서 오토스케일링이 어떻게 구현되는지를 살펴볼 텐데 사실 마이그레이션 같은 경우에는 사용자가 직접 컨트롤 하도록 내버려 두지 않습니다.
                 왜냐하면 이 가상화 기반의 클라우드 컴퓨팅의 기본적인 아이디어 자체가 사용자는 자기가 원하는 만큼의 리소스를 받기만 하면 돼요.
                 """;
    }

    public void afterConnectionClosed(
            WebSocketSession session, CloseStatus status) {
        log.info("WebSocket 연결 종료 {}", session.getId());

        // STT 종료 예정

        // 세션 종료 시 스케줄러도 shutdown
        scheduler.shutdown();
    }
}
