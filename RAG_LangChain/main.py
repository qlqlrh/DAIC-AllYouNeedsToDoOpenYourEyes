# server.py
import os
import time
from flask import Flask, request, jsonify
from dotenv import load_dotenv

from myPDFparser import upstageParser2Document
from myUpstageRAG import myRAG

load_dotenv()
api_key = os.getenv("UPSTAGE_API_KEY")

app = Flask(__name__)

# PDF 문서를 기반으로 RAG 준비
DOCUMENT_PATH = "data/10 Vector Calculus.pdf"
document = upstageParser2Document(file_path=DOCUMENT_PATH)
rag_pipeline = myRAG(document)

@app.route("/ask", methods=["POST"])
def ask_question():
    """
    클라이언트가 질문을 보내면,
    RAG 기반으로 답변을 생성해 반환한다.
    """
    data = request.get_json()
    question = data.get("question")

    if not question:
        return jsonify({"error": "Missing 'question' field"}), 400

    try:
        answer = rag_pipeline.RAG_chain_invoke(question)
        return jsonify({"refinedText": answer})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route("/health", methods=["GET"])
def health():
    return "RAG server is up.", 200

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000, debug=True)
