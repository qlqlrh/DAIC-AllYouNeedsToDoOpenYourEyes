package org.example.speaknotebackend.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import lombok.RequiredArgsConstructor;
import org.example.speaknotebackend.domain.entity.AnnotationBlock;
import org.example.speaknotebackend.domain.repository.AnnotationRepository;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfService {

    private final AnnotationRepository annotationRepository;

    @Value("${custom.pdf.upload-path}")
    private String uploadPath;

    @Value("${custom.pdf.allowed-origin}")
    private String fastapiBaseUrl;

    public byte[] generatePdfWithAnnotations(String fileId) throws Exception {
        List<AnnotationBlock> annotations = annotationRepository.findAll();

        // 페이지 수 계산
        int maxPage = annotations.stream()
                .mapToInt(AnnotationBlock::getPageNumber)
                .max()
                .orElse(1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc, PageSize.A4);

        // 페이지 먼저 생성
        for (int i = 0; i < maxPage; i++) {
            pdfDoc.addNewPage();
        }

        for (AnnotationBlock block : annotations) {
            PdfPage page = pdfDoc.getPage(block.getPageNumber());
            PdfCanvas canvas = new PdfCanvas(page);

            // 텍스트 스타일
            canvas.beginText();
            canvas.setFontAndSize(PdfFontFactory.createFont(), 10);
            canvas.setFillColor(ColorConstants.BLACK);

            // 좌표 기반 주석 출력
            canvas.moveText(block.getX(), block.getY());
            canvas.showText(block.getRefinedText());
            canvas.endText();
        }

        doc.close();
        return out.toByteArray();
    }

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


            //FastAPI 호출
            sendPdfFileToFastAPI(file);


            // 저장한 파일 ID 반환
            return fileId;
        } catch (IOException e) {
            throw new RuntimeException("PDF 임시 저장 실패", e);
        }
    }

    public void sendPdfFileToFastAPI(MultipartFile file) {
        try {
            String boundary = "----SpringToFastAPI";
            HttpClient client = HttpClient.newHttpClient();

            // 바디 만들기
            String fileName = file.getOriginalFilename();
            String mimeType = file.getContentType();
            byte[] fileBytes = file.getBytes();

            String bodyHeader = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                    "Content-Type: " + mimeType + "\r\n\r\n";

            String bodyFooter = "\r\n--" + boundary + "--\r\n";

            // 전체 바디 조립
            byte[] requestBody = concatenate(
                    bodyHeader.getBytes(),
                    fileBytes,
                    bodyFooter.getBytes()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:9000/predict/pdf"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        System.out.println("FastAPI 응답: " + response.body());
                    });

        } catch (Exception e) {
            e.printStackTrace();
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
