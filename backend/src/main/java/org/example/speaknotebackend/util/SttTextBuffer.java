package org.example.speaknotebackend.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SttTextBuffer {

    /** 현재 세션에서 AI 전송을 위해 누적 중인 순수 델타 텍스트 */
    private final StringBuilder context = new StringBuilder();

    /** 마지막으로 처리한 STT 텍스트 전체(raw) */
    private String lastFullText = "";

    /**
     * Google STT가 넘겨주는 텍스트(부분 또는 전체)를 받아,
     * 공백을 무시하고 계산한 공통 접두사 길이만큼 raw newText에서 잘라낸 뒤
     * 나머지 델타만 context에 누적합니다.
     */
    public synchronized void append(String newText) {
        if (newText == null || newText.isBlank()) {
            return;
        }

        String raw = newText;
        String prev = lastFullText == null ? "" : lastFullText;

        // 1) 공백 제거한 normalized 버전
        String normRaw = raw.replaceAll("\\s+", "");
        String normPrev = prev.replaceAll("\\s+", "");

        // 2) 길이가 줄어들거나 같으면(부분 수정·재전송) 무시
        if (normRaw.length() <= normPrev.length()) {
            return;
        }

        // 3) 공백 무시한 상태에서 공통 접두사 길이 계산
        int commonLen = commonPrefixLength(normPrev, normRaw);

        // 4) raw 문자열에서 공백을 제외하고 commonLen글자가 지나간 지점을 찾아 인덱스 계산
        int count = 0, cutIndex = 0;
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isWhitespace(raw.charAt(i))) {
                count++;
                if (count == commonLen) {
                    cutIndex = i + 1;
                    break;
                }
            }
        }
        // 5) 공통 부분 뒤에 남은 실제 델타
        String delta = raw.substring(cutIndex).trim();
        if (!delta.isEmpty()) {
            context.append(delta);
        }

        // 6) 다음 비교를 위해 전체 raw 텍스트 갱신
        lastFullText = raw;
    }

    /**
     * (예: 5초 주기) AI로 보낼 누적 context를 반환하고,
     * 세션 버퍼만 초기화합니다. lastFullText는 유지해 다음 비교 기준으로 사용합니다.
     */
    public synchronized String getAccumulatedContextAndClear() {
        if (context.length() == 0) {
            return null;
        }
        String result = context.toString();
        context.setLength(0);
        return result;
    }

    /** 연속된 두 normalized 문자열의 공통 접두사 길이를 반환 */
    private int commonPrefixLength(String a, String b) {
        int i = 0, j = 0, matched = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (ca != cb) {
                break;
            }
            matched++;
            i++;
            j++;
        }
        return matched;
    }

    /** (필요 시) 세션 전체 초기화 */
    public synchronized void clearAll() {
        context.setLength(0);
        lastFullText = "";

    }
}
