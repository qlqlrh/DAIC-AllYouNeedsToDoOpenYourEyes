"use client";

import React, { createContext, useContext, useState } from "react";

interface Annotation {
  id: string;
  text: string;
  markdown?: string | null;
}

interface AnnotationContextType {
  annotations: Annotation[];
  addAnnotation: (a: Annotation) => void;
  editAnnotation: (id: string, newText: string) => void;
  setAnnotations: React.Dispatch<React.SetStateAction<Annotation[]>>; // ✅ 추가
}

const AnnotationContext = createContext<AnnotationContextType | null>(null);

export function AnnotationProvider({ children }: { children: React.ReactNode }) {
  const [annotations, setAnnotations] = useState<Annotation[]>([]);

  const addAnnotation = (a: Annotation) => {
    setAnnotations((prev) => [...prev, a]);
  };

  const editAnnotation = (id: string, newText: string) => {
    setAnnotations((prev) =>
      prev.map((a) => (a.id === id ? { ...a, text: newText } : a))
    );
  };

  return (
    <AnnotationContext.Provider
      value={{ annotations, addAnnotation, editAnnotation, setAnnotations }} // ✅ 추가됨
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
