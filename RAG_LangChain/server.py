from flask import Flask, request, jsonify
from dotenv import load_dotenv
from uuid import uuid4
import os
from typing import Annotated, List
from typing_extensions import TypedDict
from pydantic import BaseModel, Field

from langchain_core.output_parsers import StrOutputParser
from langchain_openai import ChatOpenAI
from langchain_teddynote.models import get_model_name, LLMs
from langchain_teddynote.tools.tavily import TavilySearch
from langgraph.graph import END, START, StateGraph

import myPDFparser
import myUpstageRAG
import prompt
import logging
'''
CRAG 프로세스를 채택하여서, 강의자료와 관련없는 내용에 대해서도 강건성을 확보했다.
document_path (str) : pdf 강의자료의 경로다.
question (str) : STT 반환 서버에서의 텍스트가 들어갈 주석 텍스트
answer (str) : CRAG 프로세스를 거친후, AI의 주석관련 내용 요약 및 정리 텍스트.

예시.
document_path = "data/10 Vector Calculus.pdf"
question = "삼성전자가 개발한 생성형 AI 의 이름은?"

answer = "삼성전자가 개발한 생성형 AI의 이름은 '삼성 가우스'(Samsung Gauss)입니다."
answer_state = 0                                    #  0 : web search 없이 그대로 반환, 1 : web search 기반으로 반환.
'''
def run_CRAG_pipeline(rag_pipeline, question: str) -> tuple[str, int]:
    GPT4o = get_model_name(LLMs.GPT4o)
    MODEL_NAME = get_model_name(LLMs.GPT4)
    llm = ChatOpenAI(model=MODEL_NAME, temperature=0)

    # Grader
    class GradeDocuments(BaseModel):
        binary_score: str = Field(description="Documents are relevant to the question, 'yes' or 'no'")
    structured_llm_grader = llm.with_structured_output(GradeDocuments)
    retrieval_grader = prompt.grade_prompt | structured_llm_grader

    web_search_tool = TavilySearch(max_results=3)
    question_rewriter = prompt.re_write_prompt | llm | StrOutputParser()

    # GraphState 정의
    class GraphState(TypedDict):
        question: Annotated[str, "The question to answer"]
        generation: Annotated[str, "The generation from the LLM"]
        web_search: Annotated[str, "Whether to add search"]
        documents: Annotated[List[str], "The documents retrieved"]

    # 노드 함수들 정의
    def retrieve(state: GraphState):
        question = state["question"]
        documents = rag_pipeline.retriever.invoke(question)
        return {"documents": documents}

    def grade_documents(state: GraphState):
        question, documents = state["question"], state["documents"]
        filtered_docs, relevant_doc_count = [], 0
        for d in documents:
            grade = retrieval_grader.invoke({"question": question, "document": d.page_content}).binary_score
            if grade == "yes":
                filtered_docs.append(d)
                relevant_doc_count += 1
        return {"documents": filtered_docs, "web_search": "Yes" if relevant_doc_count == 0 else "No"}

    def query_rewrite(state: GraphState):
        better_question = question_rewriter.invoke(state["question"])
        return {"question": better_question}

    def web_search(state: GraphState):
        question = state["question"]
        docs = web_search_tool.invoke({"query": question})
        context = docs[0] if docs else llm.invoke(question)

        if isinstance(context, dict):
            context = context.get("text", str(context))
        elif not isinstance(context, str):
            context = str(context)

        formatted = prompt.prompt_to_refine_text.format(context=context, question=question)
        messages = [{"role": "user", "content": formatted}]
        generation = rag_pipeline.chat_with_solar(messages)
        return {"generation": generation}

    def generate(state: GraphState):
        generation = rag_pipeline.RAG_chain_invoke(state["question"])
        return {"generation": generation}

    def passthrough(state: GraphState):
        return {"generation": state["generation"]}

    def decide_to_generate(state: GraphState):
        return "web_search_node" if state["web_search"] == "Yes" else "generate"

    # 그래프 구성
    workflow = StateGraph(GraphState)
    workflow.add_node("retrieve", retrieve)
    workflow.add_node("grade_documents", grade_documents)
    workflow.add_node("generate", generate)
    workflow.add_node("query_rewrite", query_rewrite)
    workflow.add_node("web_search_node", web_search)
    workflow.add_node("pass", passthrough)
    workflow.add_edge(START, "query_rewrite")
    workflow.add_edge("query_rewrite", "retrieve")
    workflow.add_edge("retrieve", "grade_documents")
    workflow.add_conditional_edges("grade_documents", decide_to_generate, {
        "web_search_node": "web_search_node",
        "generate": "generate",
    })
    workflow.add_edge("generate", "pass")
    workflow.add_edge("web_search_node", "pass")
    workflow.add_edge("pass", END)

    app = workflow.compile()
    
    
    # state = {"question": question}
    # state.update(app.invoke(state))
    # answer = state["generation"]
    # answer_state = 1 if state.get("web_search") == "Yes" else 0
    state = {"question": question}
    state.update(query_rewrite(state))
    rewrited_text = state["question"]
    state.update(retrieve(state))
    state.update(grade_documents(state))

    if state["web_search"] == "Yes":
        state.update(web_search(state))
        answer_state = 1
    else:
        state.update(generate(state))
        answer_state = 0

    state.update(passthrough(state))
    voice = rewrited_text
    answer = state["generation"]    
    # 실제 사용할 응답. (str) output를 저장할 str (예시 :  "삼성전자가 개발한 생성형 AI의 이름은 '삼성 가우스'(Samsung Gauss)입니다.") , answer_state = 0
    print(answer)
    
    return voice,answer, answer_state

app = Flask(__name__)
load_dotenv()

SAVE_DIR = "./pdf_input"
os.makedirs(SAVE_DIR, exist_ok=True)

# 전역 상태
global_rag = None
global_document = None
annotations = []


@app.post("/text")
def receive_text_question():
    global global_rag

    data = request.get_json()
    question = data.get("text", "").strip()

    if not global_rag:
        return jsonify({"error": "먼저 PDF를 업로드해야 합니다."}), 400
    if not question:
        return jsonify({"error": "질문(text)이 비어 있습니다."}), 400

    try:
        voice, answer, answer_state = run_CRAG_pipeline(global_rag, question)
        annotation = {"id": str(uuid4()), "text": question, "answer": answer}
        annotations.append(annotation)
        print("answer_State : ", answer_state)
        return jsonify({
            "voice" : voice,
            "refinedText": answer,
            "refinedMarkdown": f"## 답변\n- {answer}",
            "answerState": answer_state
        })
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500
    
@app.post("/pdf")
def upload_pdf():
    global global_document, global_rag

    file = request.files.get("file")
    if not file:
        return jsonify({"error": "PDF 파일이 없습니다."}), 400

    try:
        save_path = os.path.join(SAVE_DIR, file.filename)
        file.save(save_path)

        # 문서 파싱 및 RAG 초기화
        global_document = myPDFparser.upstageParser2Document(file_path=save_path)
        print("문서파싱완료1")
        global_rag = myUpstageRAG.myRAG(global_document)
        print("문서파싱완료")
        return jsonify({"status": "ready", "message": "PDF 업로드 및 분석 완료"})
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.get("/annotations")
def get_all_annotations():
    return jsonify(annotations)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000, debug=True)