package org.example.speaknotebackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.domain.entity.AnnotationBlock;
import org.example.speaknotebackend.domain.repository.AnnotationRepository;
import org.example.speaknotebackend.dto.AnnotationDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/annotations")
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationRepository annotationRepository;

    @Operation(
            summary = "페이지 번호로 주석 조회",
            description = "특정 PDF 페이지 번호에 해당하는 주석(AnnotationBlock) 리스트를 반환합니다."
    )
    @GetMapping
    public List<AnnotationBlock> getAnnotationsByPage(@RequestParam int page) {
        return annotationRepository.findByPageNumber(page);
    }


    @Operation(
            summary = "주석 저장",
            description = "정제된 텍스트, 위치 정보 등을 포함한 주석 데이터를 저장합니다."
    )
    @PostMapping("/api/annotations")
    public ResponseEntity<Void> saveAnnotations(@RequestBody List<AnnotationDto> annotations) {
        for (AnnotationDto dto : annotations) {
            AnnotationBlock block = AnnotationBlock.builder()
                    .refinedText(dto.getText())
                    .x(dto.getX())
                    .y(dto.getY())
                    .pageNumber(dto.getPageNumber())
                    .createdAt(LocalDateTime.now())
                    .build();
            annotationRepository.save(block);
        }
        return ResponseEntity.ok().build();
    }

}

