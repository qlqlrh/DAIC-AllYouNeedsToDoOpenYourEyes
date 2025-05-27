package org.example.speaknotebackend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.service.PdfService;

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
        String fileId = pdfService.saveTempPDF(file);
        String fastApiResponse = pdfService.sendPdfFileToFastAPI(file);  //응답 받아오기
        System.out.println("FastAPI 응답: " + fastApiResponse);  //로그 출력

        String status = "error";
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(fastApiResponse);
            status = jsonNode.get("status").asText();

            System.out.println("FastAPI 응답: " + status);  //로그 출력
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok(Map.of(
                "fileId", fileId,
                "status", status
        ));
    }
}
