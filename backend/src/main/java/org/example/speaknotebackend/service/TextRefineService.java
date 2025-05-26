package org.example.speaknotebackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextRefineService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.server.url}")
    private String aiServerUrl;
    /**
     * AI 정제 서버에 원본 텍스트를 전송하고, 성공 여부 판단하는 메서드.
     * @param originalText Google STT로부터 받은 원본 텍스트
     * @return 정제 요청 결과 상태 문자열: [정제 성공], [정제 실패], [정제 오류]
     */
    public Map<String,Object> refine(String originalText) {

        HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("text", originalText), headers
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(aiServerUrl, request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK) { // 200
                return response.getBody(); // refinedText, refinedMarkdown 포함된 Map
            } else {
                log.warn("AI 서버 응답 실패: {}", response.getStatusCode());

            }
        } catch (Exception e) { // AI 서버가 꺼져있거나 예외가 발생한 경우
            log.error("AI 서버 요청 중 오류", e);

        }
        return Map.of( // 실패 시 기본값
                "refinedText", "[AI 정제 실패]",
                "refinedMarkdown", null
        );
    }
}
