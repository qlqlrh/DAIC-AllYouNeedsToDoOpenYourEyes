package org.example.speaknotebackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.service.PdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pdf")
public class PdfController {

    private final PdfService pdfService;

    @Operation(
            summary = "PDF 파일 업로드",
            description = "사용자가 업로드한 PDF 파일을 서버의 temp 디렉토리에 저장합니다."
    )
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadForModeling(@RequestParam("file") MultipartFile file) {
        String fileId = pdfService.saveTempPDF(file);  // temp 폴더에만 저장
        return ResponseEntity.ok(Map.of("fileId", fileId));
    }

    // 주석 포함된 PDF 다운로드 (fileId 기반으로)
// 예: /api/pdf/annotated?fileId=abc-123
    @Operation(
            summary = "주석 포함된 PDF 다운로드",
            description = "fileId를 기반으로 주석이 포함된 PDF를 생성하고 반환합니다."
    )
    @GetMapping("/annotated")
    public ResponseEntity<byte[]> downloadAnnotatedPdf(@RequestParam String fileId) throws Exception {
        byte[] pdfBytes = pdfService.generatePdfWithAnnotations(fileId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=annotated.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }


//    @PostMapping
//    public ResponseEntity<byte[]> downloadPdf() throws Exception {
//        byte[] pdfBytes = pdfService.generatePdfWithAnnotations();
//
//        return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=annotations.pdf")
//                .contentType(MediaType.APPLICATION_PDF)
//                .body(pdfBytes);
//    }
}
