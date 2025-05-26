"use client";

import { useEffect, useRef, useState } from "react";

export default function STTRecorder() {
  const [socket, setSocket] = useState<WebSocket | null>(null);
  const [isRecording, setIsRecording] = useState(false);

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
      const ws = new WebSocket("ws://localhost:8080/ws/audio");
      setSocket(ws);

      ws.onopen = async () => {
        console.log("WebSocket 연결됨");

        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        streamRef.current = stream;

        const audioContext = new AudioContext({ sampleRate: 16000 });
        audioContextRef.current = audioContext;

        const source = audioContext.createMediaStreamSource(stream);
        sourceRef.current = source;

        const processor = audioContext.createScriptProcessor(4096, 1, 1);  // 버퍼 크기, 입력 채널, 출력 채널
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
      socket.close(); // 서버에서도 STT 세션 종료됨
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
