"use client";

import React, { useState, useEffect } from "react"; // useEffect 포함
import { useAnnotation } from "./AnnotationContext";
import { Pencil, Trash2 } from "lucide-react";

export default function AnnotationPanel() {
  const { annotations, editAnnotation, setAnnotations } = useAnnotation(); // ✅ 수정됨
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");

  const startEdit = (id: string, text: string) => {
    setEditingId(id);
    setEditValue(text);
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
  
    // 📌 isDragged: true 플래그 추가해서 PDF 쪽에서만 처리하게끔
    e.dataTransfer.setData("text/plain", JSON.stringify({ ...dragged, isDragged: true }));
    
    // ❌ 리스트에서 제거하지 않음!
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
  className="w-full bg-blue-100 rounded max-w-full p-2 shadow text-sm group flex justify-between items-center box-border"
  draggable
  onDragStart={(e) => handleDragStart(e, anno.id)}
>


            {editingId === anno.id ? (
              <input
                value={editValue}
                onChange={(e) => setEditValue(e.target.value)}
                onBlur={finishEdit}
                onKeyDown={(e) => {
                  if (e.key === "Enter") finishEdit();
                }}
                autoFocus
                className="w-full bg-white border p-1 text-sm rounded"
              />
            ) : (
              <>
                <div className="flex-1">
                  {anno.text}
                  {anno.markdown && (
                    <div className="text-xs text-gray-600 mt-1">{anno.markdown}</div>
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
