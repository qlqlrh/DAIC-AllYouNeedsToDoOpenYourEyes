package org.example.speaknotebackend.service;

import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.domain.entity.AnnotationBlock;
import org.example.speaknotebackend.domain.repository.AnnotationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AnnotationService {

    private final AnnotationRepository annotationRepository;

    public void saveAnnotation(String sessionId, String original, String refined, int pageNumber, float x, float y) {
        AnnotationBlock block = AnnotationBlock.builder()
                .sessionId(sessionId)
                .originalText(original)
                .refinedText(refined)
                .pageNumber(pageNumber)
                .x(x)
                .y(y)
                .createdAt(LocalDateTime.now())
                .build();

        annotationRepository.save(block);
    }

}