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
    
  // PDF 업로드
  async function uploadPdfToBackend(file: File): Promise<string> {
    const formData = new FormData();
    formData.append("file", file);

    const res = await fetch(`${API_BASE_URL}/api/pdf/upload`, {
      method: "POST",
      body: formData,
    });
    if (!res.ok) {
      const errorText = await res.text();
      throw new Error(`서버 응답 실패: ${res.status} ${errorText}`);
    }

    const data = await res.json();
    return data.url;
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

      page.drawText(annotation.text, {
        x: scaledX,
        y: scaledY,
        size: 10,
        font: customFont,
        color: rgb(1, 0.6, 0),
      });
    }

    const pdfBytes = await pdfDoc.save();
    const blob = new Blob([pdfBytes], { type: "application/pdf" });
    saveAs(blob, "annotated.pdf");
  }


  return (
    <div className="flex flex-col h-screen">
      {/* ✅ 상단 고정된 헤더 */}
      <div className="p-4 flex gap-4 bg-white shadow z-10 items-center sticky top-0">
        <input
          type="file"
          id="pdf-upload"
          accept="application/pdf"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) {
              setPdfFile(file);
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
  
      {/* ✅ 하단 본문: PDF/주석 패널 나란히, 각각 독립 스크롤 */}
      <div className="flex flex-1 h-0 gap-0">
      <PDFViewer
  dropped={dropped}
  setDropped={setDropped}
  file={pdfFile}
  containerWidth={containerWidth}
  setContainerWidth={setContainerWidth}
  setRenderedSizes={setRenderedSizes}

/>
  
        {/* ✅ 주석 스크롤이 따로 움직이도록 */}
  <div className="w-1/3 h-full overflow-y-auto scrollbar-thin scrollbar-track-transparent scrollbar-thumb-gray-400">
  <AnnotationPanel />
</div>
      </div>
    </div>
  );
}  