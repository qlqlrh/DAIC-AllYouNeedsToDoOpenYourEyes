package org.example.speaknotebackend.service;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfService {

    @Value("${custom.pdf.allowed-origin}")
    private String fastapiBaseUrl;

    public String saveTempPDF(MultipartFile file) {
        try {
            // 저장할 임시 폴더 경로
            Path uploadDir = Paths.get("uploads/temp");

            // 폴더가 없다면 생성
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // UUID 기반 파일명 생성
            String fileId = UUID.randomUUID().toString();
            String fileName = fileId + ".pdf";
            Path filePath = uploadDir.resolve(fileName);

            // 파일 저장
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);


            // 저장한 파일 ID 반환
            return fileId;
        } catch (IOException e) {
            throw new RuntimeException("PDF 임시 저장 실패", e);
        }
    }

    public String sendPdfFileToFastAPI(MultipartFile file) {
        try {
            String boundary = "----SpringToFastAPI";
            HttpClient client = HttpClient.newHttpClient();

            String fileName = file.getOriginalFilename();
            String mimeType = file.getContentType();
            byte[] fileBytes = file.getBytes();

            String bodyHeader = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                    "Content-Type: " + mimeType + "\r\n\r\n";
            String bodyFooter = "\r\n--" + boundary + "--\r\n";

            byte[] requestBody = concatenate(
                    bodyHeader.getBytes(),
                    fileBytes,
                    bodyFooter.getBytes()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fastapiBaseUrl))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            // 동기 호출
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();

        } catch (Exception e) {
            e.printStackTrace();
            return "FastAPI 호출 실패";
        }
    }

    private byte[] concatenate(byte[]... parts) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            outputStream.write(part);
        }
        return outputStream.toByteArray();
    }


}
