package org.example.speaknotebackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.BidiStreamObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.util.SttTextBuffer;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Service
public class GoogleSpeechService {

    // Google STT 클라이언트 객체
    private SpeechClient speechClient;

    // Google에 오디오 chunk를 전송한 stream 객체
    private ClientStream<StreamingRecognizeRequest> requestStream;

    // 인식된 텍스트를 전달할 콜백 함수
    private Consumer<String> transcriptConsumer;

    // 스트리밍 세션이 활성화 상태인지를 알려주는 flag
    private final AtomicBoolean streamingStarted = new AtomicBoolean(false);

    private final SttTextBuffer textBuffer = new SttTextBuffer();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final TextRefineService textRefineService;

    /**
     * 애플리케이션 시작 시 Google STT 클라이언트를 초기화한다.
     */
    public GoogleSpeechService(TextRefineService textRefineService) throws Exception {
        this.textRefineService = textRefineService;
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new FileInputStream("src/main/resources/stt-credentials.json")
            );

            // 인증 정보를 포함한 STT 클라이언트 설정
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            speechClient = SpeechClient.create(settings);
            log.info("Google SpeechClient 초기화 완료");

        } catch (Exception e) {
            log.error("Google STT 초기화 실패", e);
        }
    }

    /**
     * Google STT 스트리밍을 시작한다.
     */
    public void startStreaming(WebSocketSession session) {
        try {
            streamingStarted.set(true);

            // 1초마다 누적 텍스트 전송
            scheduler.scheduleAtFixedRate(() -> {
                String context = textBuffer.getAccumulatedContextAndClear();
                log.warn("[AI 전송] 누적 context: {}", context);
                if (context != null && !context.isBlank()) {
                    try {
                        // TODO AI 서버 켠 후 활성화하면 됨
                        Map<String,Object> result = textRefineService.refine(context);
                        log.info("AI 서버 정제 결과: {}", result);
                      
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("refinedText", result.get("refinedText"));
                        payload.put("refinedMarkdown", result.get("refinedMarkdown"));

                        ObjectMapper mapper = new ObjectMapper();
                        String json = mapper.writeValueAsString(payload);
                        session.sendMessage(new TextMessage(json));

                        log.info("정제된 결과 WebSocket 전송 완료");
                        log.info("AI 응답 내용: refinedText={}, refinedMarkdown={}",
                                result.get("refinedText"), result.get("refinedMarkdown"));

                    } catch (Exception e) {
                        log.error("AI 정제 및 전송 중 오류", e);
                    }

                }
            }, 5, 5, TimeUnit.SECONDS); // 5초 후 최초 실행, 이후 5초마다 반복

            // 양방향 스트리밍을 위한 BidiStreamObserver 구현
            speechClient.streamingRecognizeCallable().call(
                    new BidiStreamObserver<>() {

                        @Override
                        public void onStart(StreamController controller) {
                            log.info("STT 스트리밍 시작됨");
                        }

                        @Override
                        public void onResponse(StreamingRecognizeResponse response) {
                            // Google이 반환한 음성 인식 결과를 처리
                            for (StreamingRecognitionResult result : response.getResultsList()) {
                                if (result.getAlternativesCount() > 0) {
                                    String transcript = result.getAlternatives(0).getTranscript();
                                    textBuffer.append(transcript);
//                                    log.warn("transcript: {}", transcript);
                                }
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            log.error("STT 오류", t);
                        }

                        @Override
                        public void onComplete() {
                            log.info("STT 스트림 종료됨");
                        }

                        @Override
                        public void onReady(ClientStream<StreamingRecognizeRequest> stream) {
                            log.info("STT 스트림 전송 준비 완료");
                            requestStream = stream;

                            // 초기 환경설정 요청 전송
                            sendInitialRequest();
                            streamingStarted.set(true);
                        }
                    },
                    GrpcCallContext.createDefault()  // gRPC 호출 컨텍스트
            );

        } catch (Exception e) {
            log.error("STT 스트리밍 시작 실패", e);
        }
    }

    /**
     * 초기 STT 환경설정 요청을 Google에 전송한다.
     * - 샘플레이트, 인코딩, 언어 등
     */
    private void sendInitialRequest() {
        try {
            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("ko-KR") // 기본 언어 : 한국어
                    .build();

            StreamingRecognitionConfig streamingConfig = StreamingRecognitionConfig.newBuilder()
                    .setConfig(recognitionConfig)
                    .setInterimResults(true)    // 중간 인식 결과 포함
                    .setSingleUtterance(false)  // 단일 발화로 자동 종료 X
                    .build();

            StreamingRecognizeRequest initialRequest = StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingConfig)
                    .build();

            requestStream.send(initialRequest);
            log.info("STT 초기 설정 전송 완료");

        } catch (Exception e) {
            log.error("STT 초기 요청 전송 실패", e);
        }
    }

    /**
     * 프론트엔드에서 수신한 오디오 chunk를 실시간으로 Google STT 서버에 전송한다.
     * @param audioBytes 오디오 chunk (LINEAR16 PCM)
     */
    public void sendAudioChunk(byte[] audioBytes) {
        if (!streamingStarted.get() || requestStream == null) return;

        try {
            StreamingRecognizeRequest audioRequest = StreamingRecognizeRequest.newBuilder()
                    .setAudioContent(ByteString.copyFrom(audioBytes))
                    .build();
            requestStream.send(audioRequest);
        } catch (Exception e) {
            log.warn("오디오 chunk 전송 실패", e);
        }
    }

    /**
     * 스트리밍 세션을 종료하고 리소스를 해제한다.
     */
    public void stopStreaming() {
        try {
            if (requestStream != null) {
                requestStream.closeSend();
                requestStream = null;
                streamingStarted.set(false);
                textBuffer.clearAll();  // ← 반드시 버퍼 초기화!
                log.info("STT 스트리밍 종료");
            }
        } catch (Exception e) {
            log.warn("STT 종료 중 오류", e);
        }
    }
}
