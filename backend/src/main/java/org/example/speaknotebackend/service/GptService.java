package org.example.speaknotebackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class GptService {

    // 비동기 GPT 처리 메서드
    @Async
    public CompletableFuture<String> refineTextAsync(String inputText) {
        log.info("GPT 요청 시작: {}", inputText);

        // 실제 API 넣기
        String refined = "[GPT 정제 결과] " + inputText;

        // 응답을 가짜로 조금 지연
        try {
            Thread.sleep(500); // 지연 시뮬레이션
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("GPT 정제 완료");
        return CompletableFuture.completedFuture(refined);
    }
}
