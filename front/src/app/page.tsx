"use client";

import PDFViewer from "@/components/PDFViewer";
import AnnotationPanel from "@/components/AnnotationPanel";
import { useRef, useState, useEffect } from "react";
import { PDFDocument, rgb } from "pdf-lib";
import fontkit from "@pdf-lib/fontkit";
import { saveAs } from "file-saver";
import { DroppedAnnotation } from "@/components/types";
import STTRecorder from "@/components/STTRecorder";


export default function Home() {
  const captureRef = useRef<HTMLDivElement>(null);
  const [dropped, setDropped] = useState<DroppedAnnotation[]>([]);
  const [pdfFile, setPdfFile] = useState<File | null>(null);
  const [containerWidth, setContainerWidth] = useState<number>(600); // 임시 기본값
  const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;
  const [renderedSizes, setRenderedSizes] = useState<Record<number, { width: number; height: number }>>({});
  const [isPdfReady, setIsPdfReady] = useState(false); // FastAPI 응답 수신 여부
  const [isLoading, setIsLoading] = useState(false);       // 업로드 중 상태

  // PDF 업로드
  async function uploadPdfToBackend(file: File): Promise<void> {
    const formData = new FormData();
    formData.append("file", file);
  
    // 업로드 시작 시 로딩 상태
    setIsLoading(true);
  
    try {
      const res = await fetch(`${API_BASE_URL}/api/pdf/upload`, {
        method: "POST",
        body: formData,
      });
  
      if (!res.ok) {
        const errorText = await res.text();
        throw new Error(`서버 응답 실패: ${res.status} ${errorText}`);
      }
  
      const text = await res.text(); //  JSON 인코딩 깨질 경우 디버깅용
      console.log("FastAPI 응답(raw text):", text);
  
      const data = JSON.parse(text);
      console.log("FastAPI 응답 파싱:", data);
  
      if (data.status === "ready") {
        setIsPdfReady(true); // 준비 완료 시 렌더링 허용
      } else {
        console.warn("FastAPI 응답에 status: 'ready' 없음");
      }
  
    } catch (e) {
      alert("PDF 업로드 실패");
      console.error(e);
    } finally {
      setIsLoading(false); // 무조건 로딩 종료
    }
  }
  

  // 주석 포함 PDF 저장
  async function handleSaveWithAnnotations(
    file: File,
    droppedAnnotations: DroppedAnnotation[],
    renderedSizes: Record<number, { width: number; height: number }>

  ) {
    const existingPdfBytes = await file.arrayBuffer();
    const pdfDoc = await PDFDocument.load(existingPdfBytes);
    pdfDoc.registerFontkit(fontkit);
    const fontBytes = await fetch("/fonts/MaruBuri-Bold.ttf").then((res) =>
      res.arrayBuffer()
    );
    const customFont = await pdfDoc.embedFont(fontBytes);

    const pages = pdfDoc.getPages();
    for (const annotation of droppedAnnotations) {
      const page = pages[annotation.pageNumber - 1];
      const pageWidth = page.getWidth();
      const pageHeight = page.getHeight();

      const rendered = renderedSizes[annotation.pageNumber];
      if (!rendered) continue; // 안전 처리
      
      const scaledX = (annotation.x / rendered.width) * pageWidth;
      const scaledY = pageHeight - (annotation.y / rendered.height) * pageHeight;

      for (const annotation of droppedAnnotations) {
        const page = pages[annotation.pageNumber - 1];
        const pageWidth = page.getWidth();
        const pageHeight = page.getHeight();
        
      
        const rendered = renderedSizes[annotation.pageNumber];
        if (!rendered) continue;
      
        const scaledX = (annotation.x / rendered.width) * pageWidth;
        const scaledY = pageHeight - (annotation.y / rendered.height) * pageHeight;
        const answerState = annotation.answerState ?? 1;
        const textColor = answerState === 0 ? rgb(1, 0, 0) : rgb(1, 0.6, 0); // 빨간색 or 주황색

        try {
          const parsed = JSON.parse(annotation.text);
          const refined: string = parsed.refinedText || "";
          const lines: string[] =
          parsed.lines ??
          parsed.refinedText.split("\n"); // fallback
          if (lines.length === 1) {
            lines.push(" ");
          }
          const lineHeight = 12; // px 단위
          lines.forEach((line, i) => {
            page.drawText(line, {
              x: scaledX,
              y: scaledY - i * lineHeight,
              size: 12,
              font: customFont,
              color: textColor,
              maxWidth: annotation.width,
            });
          });
        } catch (e) {
          // fallback: 원본 문자열 출력
          page.drawText(annotation.text, {
            x: scaledX,
            y: scaledY,
            size: 12,
            font: customFont,
            color: textColor,
          });
        }
      }
      
    }

    const pdfBytes = await pdfDoc.save();
    const blob = new Blob([pdfBytes], { type: "application/pdf" });
    saveAs(blob, "annotated.pdf");
  }


  return (
    <div className="flex flex-col h-screen">
      {/* 상단 고정된 헤더 */}
      <div className="px-8 py-5 flex gap-6 bg-white shadow-md z-10 items-center sticky top-0 text-lg">
        <input
          type="file"
          id="pdf-upload"
          accept="application/pdf"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) {
              setPdfFile(file);
              setIsPdfReady(false); // 다시 초기화
              uploadPdfToBackend(file);
            }
          }}
        />
        <label
          htmlFor="pdf-upload"
          className="px-4 py-2 bg-gray-600 text-white rounded cursor-pointer"
        >
          PDF 업로드
        </label>

        <STTRecorder />

        <button
          className="px-4 py-2 bg-blue-500 text-white rounded"
          onClick={() => {
            if (pdfFile) {
              handleSaveWithAnnotations(pdfFile, dropped, renderedSizes);
            } else {
              alert("PDF 파일을 먼저 업로드하세요.");
            }
          }}
        >
          PDF 다운로드
        </button>
      </div>
  
      {/* PDF/주석 패널 나란히, 각각 독립 스크롤 */}
      <div className="flex flex-1 h-0 gap-0">
  {!pdfFile ? (
    // 1. 파일 선택 전
    <div className="w-full flex items-center justify-center text-gray-400 text-3xl">
      📄 PDF 업로드
    </div>
  ) : !isPdfReady ? (
    // 2. 파일 선택 후 & FastAPI 응답 대기 중
    <div className="w-full flex items-center justify-center text-gray-600 text-3xl animate-pulse ">
      ⏳ PDF 분석 중입니다...
    </div>
  ) : (
    // 3. 응답 완료된 경우 PDF 렌더링
    <PDFViewer
      dropped={dropped}
      setDropped={setDropped}
      file={pdfFile}
      containerWidth={containerWidth}
      setContainerWidth={setContainerWidth}
      setRenderedSizes={setRenderedSizes}
    />
  )}

  {/* 주석 패널은 항상 오른쪽에 표시 */}
  <div className="w-1/3 h-full overflow-y-auto scrollbar-thin scrollbar-track-transparent scrollbar-thumb-gray-400">
    <AnnotationPanel />
  </div>
</div>
    </div>
  );
}  
