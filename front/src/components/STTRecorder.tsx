"use client";

import { useEffect, useRef, useState } from "react";
import { useAnnotation } from "./AnnotationContext"; // 주석 연결

export default function STTRecorder() {
  const { addAnnotation } = useAnnotation(); // 주석 추가 기능
  const [socket, setSocket] = useState<WebSocket | null>(null);
  const [isRecording, setIsRecording] = useState(false);
  const API_WSS_URL = process.env.NEXT_PUBLIC_API_WSS_URL;

  const audioContextRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  
  const convertFloat32ToInt16 = (buffer: Float32Array) => {
    const l = buffer.length;
    const result = new Int16Array(l);
    for (let i = 0; i < l; i++) {
      const s = Math.max(-1, Math.min(1, buffer[i]));
      result[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
      result[i] = Math.floor(result[i]);
    }
    return result;
  };

  const startRecording = async () => {
    try {
      const ws = new WebSocket(API_WSS_URL!); // websocket 연결 
      setSocket(ws);

      ws.onopen = async () => {
        console.log("WebSocket 연결됨");

        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        streamRef.current = stream;

        const audioContext = new AudioContext({ sampleRate: 16000 });
        audioContextRef.current = audioContext;

        const source = audioContext.createMediaStreamSource(stream);
        sourceRef.current = source;

        const processor = audioContext.createScriptProcessor(4096, 1, 1);
        processorRef.current = processor;

        source.connect(processor);
        processor.connect(audioContext.destination);

        processor.onaudioprocess = (e) => {
          const input = e.inputBuffer.getChannelData(0);
          const pcm = convertFloat32ToInt16(input);

          if (ws.readyState === WebSocket.OPEN) {
            ws.send(pcm.buffer);
          }
        };

        setIsRecording(true);
      };

      ws.onmessage = (event) => {
        console.log("서버 응답:", event.data);

        // ✅ 주석 추가 로직
        const newText = event.data;
        const parsed = JSON.parse(newText);
        const newAnnotation = {
          id: crypto.randomUUID(),
          text: newText,
          markdown: null, // 필요 시 후처리
          answerState: parsed.answerState ?? 1,
        };
        addAnnotation(newAnnotation);
      };

      ws.onerror = (err) => {
        console.error("WebSocket 오류:", err);
      };

      ws.onclose = () => {
        console.log("WebSocket 연결 종료됨");
        stopRecordingInternal();
        setSocket(null);
      };
    } catch (err) {
      console.error("녹음 시작 실패:", err);
      alert("마이크 권한이 필요하거나 연결에 실패했습니다.");
    }
  };

  const stopRecordingInternal = () => {
    processorRef.current?.disconnect();
    sourceRef.current?.disconnect();
    audioContextRef.current?.close();

    streamRef.current?.getTracks().forEach((track) => track.stop());

    processorRef.current = null;
    sourceRef.current = null;
    audioContextRef.current = null;
    streamRef.current = null;

    setIsRecording(false);
  };

  const stopRecording = () => {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.close();
    } else {
      stopRecordingInternal();
      setSocket(null);
    }
  };

  return (
    <button
      className={`px-4 py-2 rounded text-white transition ${
        isRecording ? "bg-red-500" : "bg-green-500"
      }`}
      onClick={isRecording ? stopRecording : startRecording}
    >
      {isRecording ? "녹음 중지" : "녹음 시작"}
    </button>
  );
}
