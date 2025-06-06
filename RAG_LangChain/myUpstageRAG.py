'''
upstage의 document parser을 토대로, RAG 체인을 생성한다.
'''
import os
import requests
from bs4 import BeautifulSoup
from openai import OpenAI # openai==1.52.2
from typing import Callable, Dict
from langchain.prompts import PromptTemplate
from langchain_core.runnables import RunnablePassthrough
from langchain_core.output_parsers import StrOutputParser

from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import PyMuPDFLoader
from langchain_community.vectorstores import FAISS
from langchain_core.output_parsers import StrOutputParser
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain.schema import Document

from collections import defaultdict
from langchain_teddynote import logging
from dotenv import load_dotenv

import prompt


api_key = os.getenv("UPSTAGE_API_KEY")
# API 키를 환경변수로 관리하기 위한 설정 파일
load_dotenv() # API 키 정보 로드
logging.langsmith("myRAG") # 프로젝트 이름을 입력합니다.

class myRAG():
    def __init__(self, documents):
        '''
        documents (Document) : upstage의 document parser과 llm을 이용한 도표, 수식 데이터를 포함한 Document생성.
        vectorstore : documents내용의 임베딩 벡터들을 저장하는 DB
        client : OpenAI (upstage.ai) 정의하기.
        '''
        # 앞으로의 대화를 기록할 메세지 정의
        self.messages = [{"role": "user", "content": ""}]

        # 문서 생성
        self.documents = documents

        # Prompt 정의
        # self.prompt =  """You are an assistant for question-answering tasks. 
        #     Use the following pieces of retrieved context to answer the question. 
        #     If you don't know the answer, just say that you don't know. 
        #     Answer in Korean.

        #     #Context: 
        #     {context}

        #     #Question:
        #     {question}
        #     """
        self.prompt = prompt.prompt_to_refine_text

        # 2. 문서 분할 (chunking)
        self.text_splitter = RecursiveCharacterTextSplitter(chunk_size=500, chunk_overlap=50)
        self.split_documents = self.text_splitter.split_documents(self.documents)

        # 3. 임베딩 생성
        self.embeddings = OpenAIEmbeddings()

        # 4. 벡터스토어 생성
        self.vectorstore = FAISS.from_documents(documents=self.split_documents, embedding=self.embeddings)

        # 5. retriever 생성.
        self.retriever = self.vectorstore.as_retriever()

        # 6. LLM 생성
        # llm = ChatOpenAI(model_name=model_name, temperature=0)
        self.client = OpenAI(
            api_key=api_key,
            base_url="https://api.upstage.ai/v1"
        )

    def modify_prompt(self, prompt : str):
        self.prompt = prompt

    def chat_with_solar(self, messages):
        response = self.client.chat.completions.create(
            model="solar-pro",
            messages=messages
        )
        return response.choices[0].message.content
    
    def RAG_chain_invoke(self, question = "AI 기술 유형 평균 기술 대비 갖는 임금 프리미엄이 가장 높은 AI 기술은?",prompt=None, top_k = 3 ):
        # 1. question 정의
        context_docs = self.retriever.get_relevant_documents(question)[:top_k]
        context = "\n\n".join(doc.page_content for doc in context_docs)

        # 2. prompt와 question을 조합해 user 메시지 생성
        if prompt == None:
            formatted_prompt = self.prompt.format(context=context, question=question)
        else:
            formatted_prompt = prompt
        # 3. chat_with_solar에 넣을 messages 구성
        messages = [{"role": "user", "content": formatted_prompt}]
        answer = self.chat_with_solar(messages)
        self.messages.append({"role": "assistant", "content": answer})
        print()
        print("question : ", question)
        print("answer : ", answer)
        print()
        return answer


# import myPDFparser

# file_path = "data/sample_pdf.pdf" # ex: ./image.png
# question1 = "AI 기술 유형 평균 기술 대비 갖는 임금 프리미엄이 가장 높은 AI 기술은?"
# no_context_question = [{"role": "user", "content": question1}]
# # question2 = "내가 이전에 기술 유형에 대해 어떤 질문을 했었지?"
# document = myPDFparser.upstageParser2Document(file_path=file_path)
# myRAG = myRAG(document)


# no_context_answer = myRAG.chat_with_solar(no_context_question)
# print()
# print(no_context_answer)
# print()

# context_answer = myRAG.RAG_chain_invoke(question1)
# print()
# print(context_answer)
# print()
