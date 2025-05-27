"use client";

import React, { useState, useEffect } from "react"; // useEffect 포함
import { useAnnotation } from "./AnnotationContext";
import { Pencil, Trash2 } from "lucide-react";

export default function AnnotationPanel() {
  const { annotations, editAnnotation, setAnnotations } = useAnnotation(); 
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");

  const startEdit = (id: string, text: string) => {
    try {
      const parsed = JSON.parse(text);
      setEditValue(parsed.refinedText || "");
    } catch {
      setEditValue(text); // fallback
    }
    setEditingId(id);
  };

  const finishEdit = () => {
    if (editingId !== null) {
      editAnnotation(editingId, editValue);
      setEditingId(null);
      setEditValue("");
    }
  };

  const deleteAnnotation = (id: string) => {
    const newList = annotations.filter((a) => a.id !== id);
    setAnnotations(newList);
  };

  

  const handleDragStart = (e: React.DragEvent, id: string) => {
    const dragged = annotations.find((a) => a.id === id);
    if (!dragged) return;
  
    // isDragged: true 플래그 추가해서 PDF 쪽에서만 처리하게끔
  // 실제 DOM element의 크기 계산
  const target = e.currentTarget as HTMLElement;
  const width = target.offsetWidth;
  const height = target.offsetHeight;

  e.dataTransfer.setData(
    "text/plain",
    JSON.stringify({
      ...dragged,
      width,
      height,
      isDragged: true,
    })
  );    
    // 리스트에서 제거하면 안됨.
  };

  useEffect(() => {
    const handler = (e: Event) => {
      const id = (e as CustomEvent<string>).detail;
      setAnnotations((prev) => prev.filter((a) => a.id !== id));
    };
  
    window.addEventListener("annotation-dropped", handler);
    return () => window.removeEventListener("annotation-dropped", handler);
  }, []);
  
  

  return (
    <div className="w-full h-full items-end bg-white border-l p-5 overflow-y-auto">
      <h2 className="text-lg font-bold mb-2">실시간 요약</h2>
      <div className="flex flex-col items-end	 gap-2">
        {annotations.map((anno) => (
          
<div
  key={anno.id}
  className={`w-full rounded max-w-full p-2 shadow text-sm group flex justify-between items-center box-border ${
      anno.answerState === 0
        ? "bg-pink-200"
        : anno.answerState === 2
        ? "bg-blue-200" 
        : "bg-yellow-200"
    }`}  draggable
  onDragStart={(e) => handleDragStart(e, anno.id)}
>


{editingId === anno.id ? (
  <textarea
    value={editValue}
    onChange={(e) => setEditValue(e.target.value)}
    onBlur={finishEdit}
    onKeyDown={(e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        finishEdit();
      }
    }}
    rows={3} // 기본 줄 수
    autoFocus
    className="w-full bg-white border p-2 text-sm rounded resize-none"
  />
) : (
  <>
    <div className="flex-1 whitespace-pre-wrap">
      {(() => {
        try {
          return JSON.parse(anno.text).refinedText;
        } catch {
          return anno.text;
        }
      })()}
      {anno.markdown && (
        <div className="text-xs text-gray-600 mt-1 whitespace-pre-wrap">
          {anno.markdown}
        </div>
      )}
    </div>
    <div className="flex gap-2 ml-2 opacity-0 group-hover:opacity-100 transition">
      <button onClick={() => startEdit(anno.id, anno.text)}>
        <Pencil size={16} />
      </button>
      <button onClick={() => deleteAnnotation(anno.id)}>
        <Trash2 size={16} />
      </button>
    </div>
  </>
)}

          </div>
        ))}
      </div>
    </div>
  );
}
