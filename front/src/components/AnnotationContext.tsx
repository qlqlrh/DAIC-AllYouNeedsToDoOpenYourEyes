"use client";

import React, { createContext, useContext, useState } from "react";

interface Annotation {
  id: string;
  text: string;
  markdown?: string | null;
  answerState?: number; //새로 적용
}

interface AnnotationContextType {
  annotations: Annotation[];
  addAnnotation: (a: Annotation) => void;
  editAnnotation: (id: string, newText: string) => void;
  setAnnotations: React.Dispatch<React.SetStateAction<Annotation[]>>; 
}

const AnnotationContext = createContext<AnnotationContextType | null>(null);

export function AnnotationProvider({ children }: { children: React.ReactNode }) {
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const addAnnotation = (a: Annotation) => {
    try {
      const parsed = JSON.parse(a.text);
      const paragraphs: string[] = (parsed.refinedText ?? "")
        .split(/\n\s*\n/)
        .map((para: string) => para.trim())
        .filter(Boolean);
      const answerState = parsed.answerState ?? 1;
      console.log("answerState 파싱 결과:", answerState);
      const newAnnotations: Annotation[] = [];
      const parsedAnswerState = parsed.answerState ?? 1;


          // voice가 존재하면 먼저 추가 (파란색)
      if (parsed.voice) {
        newAnnotations.push({
          id: `${a.id}-voice`,
          text: JSON.stringify({ refinedText: parsed.voice }),
          markdown: a.markdown ?? null,
          answerState: 2,
        });
      }

      // refinedText가 있으면 문단마다 추가 (노란색)
      if (paragraphs.length > 0) {
        newAnnotations.push(
          ...paragraphs.map((para, idx) => ({
            id: `${a.id}-p${idx}`,
            text: JSON.stringify({ refinedText: para }),
            markdown: a.markdown ?? null,
            answerState: parsedAnswerState,
          }))
        );
      }


  
    // 실제 등록
    setAnnotations((prev) => [...prev, ...newAnnotations]);
  } catch (err) {
    console.error("🔴 JSON 파싱 실패. 원본 그대로 추가:", err);
    setAnnotations((prev) => [...prev, a]);
  }
};
  
  
  

  const editAnnotation = (id: string, newText: string) => {
    setAnnotations((prev) =>
      prev.map((a) => (a.id === id ? { ...a, text: newText } : a))
    );
  };

  return (
    <AnnotationContext.Provider
      value={{ annotations, addAnnotation, editAnnotation, setAnnotations }} 
    >
      {children}
    </AnnotationContext.Provider>
  );
}

export const useAnnotation = () => {
  const context = useContext(AnnotationContext);
  if (!context)
    throw new Error("useAnnotation must be used within AnnotationProvider");
  return context;
};
