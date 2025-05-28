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
import os
import requests
from dotenv import load_dotenv
from typing import Annotated, List
from typing_extensions import TypedDict
from pydantic import BaseModel, Field

from openai import OpenAI
from langchain.schema import Document
from langchain.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_openai import ChatOpenAI
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.vectorstores import FAISS
from langchain_teddynote.models import get_model_name, LLMs
from langchain_teddynote.tools.tavily import TavilySearch
from langchain_teddynote import logging
from langgraph.graph import END, StateGraph, START

import myPDFparser
import myUpstageRAG
import prompt

# 김예슬은 이것만 보면됨.
# intput 
document_path = "data/10 Vector Calculus.pdf"       # (str) : pdf 경로를 담고있는 str
question = "삼성전자가 개발한 생성형 AI 의 이름은?"         # (str) : 동인이 백엔드 서버에서 들어올 텍스트 부분
answer = ""                                         # (str) : output를 저장할 str (예시 :  "삼성전자가 개발한 생성형 AI의 이름은 '삼성 가우스'(Samsung Gauss)입니다.") 
answer_state = 0                                    #  0 : web search 없이 그대로 반환, 1 : web search 기반으로 반환.



# ===========================
# Initialization
# ===========================
load_dotenv()
logging.langsmith("myCRAG")


document = myPDFparser.upstageParser2Document(file_path=document_path)
rag_pipeline = myUpstageRAG.myRAG(document)

GPT4o = get_model_name(LLMs.GPT4o)
MODEL_NAME = get_model_name(LLMs.GPT4)
llm = ChatOpenAI(model=MODEL_NAME, temperature=0)

# ===========================
# Grader Definition
# ===========================
class GradeDocuments(BaseModel):
    binary_score: str = Field(description="Documents are relevant to the question, 'yes' or 'no'")

structured_llm_grader = llm.with_structured_output(GradeDocuments)
grade_prompt = prompt.grade_prompt
# grade_prompt = ChatPromptTemplate.from_messages([
#     ("system", "You are a grader assessing relevance of a retrieved document to a user question.\n"
#                 "If the document contains keyword(s) or semantic meaning related to the question, grade it as relevant.\n"
#                 "Give a binary score 'yes' or 'no' score to indicate whether the document is relevant to the question."),
#     ("human", "Retrieved document: \n\n {document} \n\n User question: {question}")
# ])
retrieval_grader = grade_prompt | structured_llm_grader

# ===========================
# Web Search Tool & Question Rewrite
# ===========================
web_search_tool = TavilySearch(max_results=3)
re_write_prompt = prompt.re_write_prompt
# re_write_prompt = ChatPromptTemplate.from_messages([
#     ("system", "You are a question re-writer that converts an input question to a better version optimized for web search."),
#     ("human", "Here is the initial question: \n\n {question} \n Formulate an improved question.")
# ])
question_rewriter = re_write_prompt | llm | StrOutputParser() # solar-pro 대비 context가 주어지지 않았을때는, 답변 품질 향상을 위해서 실험 후 다시 gpt로 변경.
 
# ===========================
# Graph State Definition
# ===========================
class GraphState(TypedDict):
    question: Annotated[str, "The question to answer"]
    generation: Annotated[str, "The generation from the LLM"]
    web_search: Annotated[str, "Whether to add search"]
    documents: Annotated[List[str], "The documents retrieved"]

# ===========================
# Graph Nodes
# ===========================
def retrieve(state: GraphState):
    print("\n==== RETRIEVE ====\n")
    question = state["question"]
    documents = rag_pipeline.retriever.invoke(question)
    return {"documents": documents}

def grade_documents(state: GraphState):
    print("\n==== [CHECK DOCUMENT RELEVANCE TO QUESTION] ====\n")
    question, documents = state["question"], state["documents"]
    filtered_docs, relevant_doc_count = [], 0

    for d in documents:
        grade = retrieval_grader.invoke({"question": question, "document": d.page_content}).binary_score
        if grade == "yes":
            print("==== [GRADE: DOCUMENT RELEVANT] ====")
            filtered_docs.append(d)
            relevant_doc_count += 1
        else:
            print("==== [GRADE: DOCUMENT NOT RELEVANT] ====")

    return {"documents": filtered_docs, "web_search": "Yes" if relevant_doc_count == 0 else "No"}

def query_rewrite(state: GraphState):
    print("\n==== [REWRITE QUERY] ====\n")
    better_question = question_rewriter.invoke(state["question"])
    return {"question": better_question}

def web_search(state: GraphState):
    print("\n==== [WEB SEARCH] ====\n")
    question = state["question"]
    docs = web_search_tool.invoke({"query": question})
    context = docs[0] if docs else llm.invoke(question)

    # prompt =  """You are an assistant for question-answering tasks
    # nUse the following pieces of retrieved context to answer the question.
    # If you don't know the answer, just say that you don't know.
    # Answer in Korean.
    
    
    # #Context:
    # {context}
    
    # #Question:
    # {question}
    # """
    web_search_prompt = prompt.prompt_to_refine_text
    formatted = web_search_prompt.format(context=context, question=question)
    messages = [{"role": "user", "content": formatted}]
    generation = rag_pipeline.chat_with_solar(messages)
    return {"generation": generation}

def generate(state: GraphState):
    print("\n==== GENERATE ====\n")
    generation = rag_pipeline.RAG_chain_invoke(state["question"])
    return {"generation": generation}

def passthrough(state: GraphState):
    print("==== [PASS THROUGH] ====")
    print("Final Answer:", state["generation"])
    return {"generation": state["generation"]}

def decide_to_generate(state: GraphState):
    print("==== [ASSESS GRADED DOCUMENTS] ====")
    return "query_rewrite" if state["web_search"] == "Yes" else "generate"

# ===========================
# Workflow Definition
# ===========================
# 그래프 상태 초기화
workflow = StateGraph(GraphState)

# 노드 정의
workflow.add_node("retrieve", retrieve)
workflow.add_node("grade_documents", grade_documents)
workflow.add_node("generate", generate)
workflow.add_node("query_rewrite", query_rewrite)
workflow.add_node("web_search_node", web_search)
workflow.add_node("pass", passthrough)

# 엣지 연결
workflow.add_edge(START, "query_rewrite")
workflow.add_edge("query_rewrite", "retrieve")
workflow.add_edge("retrieve", "grade_documents")

# 문서 평가 노드에서 조건부 엣지 추가
workflow.add_conditional_edges(
    "grade_documents",
    decide_to_generate,
    {
        "web_search_node": "web_search_node",
        "generate": "generate",
    },
)

# 엣지 연결
# 모든 END 직전 노드는 pass로 향하게!
workflow.add_edge("generate", "pass")
workflow.add_edge("web_search_node", "pass")
workflow.add_edge("pass", END)


# 그래프 컴파일
app = workflow.compile()

# ===========================
# Execution Entry Point
# ===========================
# answer 값을 실제로 반환하기 위해서 stream형식 대신, 함수를 사용. (그럼으로 조건부 노드도 제거함.)
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
answer = "음성인식내용:" + rewrited_text + "\n\n" + "내용 정리 및 부가설명:" + state["generation"]    # 실제 사용할 응답. (str) output를 저장할 str (예시 :  "삼성전자가 개발한 생성형 AI의 이름은 '삼성 가우스'(Samsung Gauss)입니다.") , answer_state = 0
print(answer)
