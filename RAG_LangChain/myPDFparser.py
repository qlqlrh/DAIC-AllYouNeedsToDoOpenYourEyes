'''
upstage parser를 이용해서, document 형식에 맞게 시퀀셜 자연어 형태로 pdf내용을 읽어온다.
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
api_key = "up_qAeV4wltdGnqdmxavmqmxXyRBpmcZ"
# API 키를 환경변수로 관리하기 위한 설정 파일
load_dotenv() # API 키 정보 로드

# hanlder처리를 위한 ai client 생성.
client = OpenAI(
    api_key=api_key,
    base_url="https://api.upstage.ai/v1"
)



# pdf에 도표나 공식이 많을경우, 비동기 처리기법을 통해서 처리 속도를 늘리는 개선방안이 필요하다.
def content_convert(prompt, data):
    massage = prompt + data

    stream = client.chat.completions.create(
        model="solar-pro",
        messages=[
            {
                "role": "user",
                "content": massage
            }
        ],
        stream=True,
    )

    result = ""
    for chunk in stream:
        if chunk.choices[0].delta.content is not None:
            result += chunk.choices[0].delta.content
    return result


def text_handler(figure_data: dict | None) -> str:
    """
    어떤 HTML 데이터든 시퀀셜한 자연어 텍스트로 반환.
    None, 형식 불일치, HTML 파싱 실패 등도 모두 "" 또는 안전한 문자열 반환.
    """
    if figure_data is None or not isinstance(figure_data, dict):
        return ""

    try:
        html = figure_data.get("content", {}).get("html", "")
        if not html:
            return ""

        soup = BeautifulSoup(html, "html.parser")

        img = soup.find("img")
        if img and img.get("alt"):
            alt_text = img.get("alt").strip()
            return f"markdown: {alt_text}, text: {alt_text}"

        text = soup.get_text(separator="\n", strip=True)
        return text
    except Exception as e:
        # 에러가 발생해도 빈 문자열 반환
        return ""

def equation_handler(equation_data_raw: tuple):
    # equation_handler_prompt = (
    #     "너는 수학 수식을 설명하는 한국어 선생님이야.\n"
    #     "아래에 주어진 수학 수식의 LaTeX 마크다운(markdown) 표현과 일반 텍스트(text) 표현을 보고, "
    #     "이 수식이 의미하는 수학적 개념 또는 연산 과정을 한국어 자연어 문장으로 간결하게 설명해줘.\n"
    #     "결과 문장은 논리적 흐름을 가지는 완전한 문장이어야 하며, 수학적 표현이 자연스럽게 해석되도록 구성되어야 해.\n"
    #     "수식의 구조적 의미에 초점을 맞춰 설명해줘.\n"
    #     "형식 예시: 이 수식은 k=1부터 n까지의 합을 나타내며, 분자는 2k+1이고 분모는 1^2부터 k^2까지의 제곱합이다."
    # )
    equation_handler_prompt = prompt.equation_handler_prompt
    # 실제 방정식 정보를 지닌 데이터.
    equation_data = equation_data_raw[0]
    # 방성식 정보위에 있는 문단의 데이터. (방정식에 대한 설명을 하고있을 확률 높음.) 해당 데이터는 None 이 들어올수있음. 그래도 오류가 나지않게 예외처리 해야함.
    equation_descript_text = text_handler(equation_data_raw[1])
    
    # 문자열 파싱을 통한 텍스트 추출
    markdown = equation_data["content"].get("markdown", "").strip()
    text = equation_data["content"].get("text", "").strip()
    equation_text =  f"markdown: {markdown}, text: {text}"
    print(equation_descript_text)
    # llm에 context와 함께 제공해서, 시퀀셜한 자연어 문장으로 결과값 받기.
    llm_input = equation_descript_text + " the equation is : " +equation_text
    result = content_convert(prompt=equation_handler_prompt, data=llm_input)
    return result


def chart_handler(chart_data_raw: tuple):
    # chart_handler
    # chart_handler_prompt = (
    #     "너는 표에 포함된 수치를 한국어로 요약 설명하는 AI야.\n"
    #     "아래에 제공된 표 내용(markdown, text)을 보고, 각 항목과 수치를 한국어 문장으로 자연스럽게 설명해줘.\n"
    #     "표는 항목별 수치를 나타내는 도표이며, 각 항목의 이름과 그에 대응하는 수치를 연결하여 설명해야 해.\n"
    #     "결과는 문장 형태의 한국어 설명이어야 하며, 사람이 쉽게 이해할 수 있는 시퀀셜한 설명으로 구성돼야 해.\n"
    #     "형식 예시: 이 도표는 에너지 생산량 비중을 나타내며, 핵에너지는 4%, 재생에너지는 4%, 석유는 34%의 비중을 차지한다."
    # )
    chart_handler_prompt = prompt.chart_handler_prompt

    # 실제 데이터 도표 html 정보.
    chart_data = chart_data_raw[0]["content"]["html"]

    # 도표에 대한 설명이 담긴 텍스트 (도표 윗문장으로, 도표에 대한 설명이 있을 확률이 높다.)
    chart_descript_text = text_handler(chart_data_raw[1])
    print(chart_descript_text)
    # llm에 context와 함께 제공해서, 시퀀셜한 자연어 문장으로 결과값 받기.
    llm_input = chart_descript_text + " the chart's html contents are: " +  chart_data
    llm_output = content_convert(prompt=chart_handler_prompt, data=llm_input)
    return llm_output



def figure_handler(figure_data: tuple):

    # figure_handler
    # figure_handler_prompt = (
    #     "너는 도표 이미지를 설명하는 한국어 요약 전문가야.\n"
    #     "이미지의 대체 텍스트(markdown과 text)를 기반으로, 그 이미지가 무엇을 나타내는지 자연스럽게 설명해줘.\n"
    #     "수치가 포함된 경우에는 항목별로 나열하면서 간결하게 수치를 언급해주고, 전체적으로 어떤 내용을 담고 있는지 한국어로 시퀀셜하게 요약해줘.\n"
    #     "결과는 임베딩 벡터로 사용할 수 있도록 완전한 문장 구조를 가져야 하며, 단순 나열이 아닌 설명적인 형태로 구성되어야 해.\n"
    #     "형식 예시: 이 도표는 에너지 자원별 생산 비율을 보여준다. 석유는 34%, 석탄은 27%, 천연가스는 24% 등의 비중으로 구성된다."
    # )
    figure_handler_prompt = prompt.figure_handler_prompt
    # figure 원본 html 데이터.
    markdown = figure_data[0]["content"]["html"]

    # 방성식 정보위에 있는 문단의 데이터. (방정식에 대한 설명을 하고있을 확률 높음.) 해당 데이터는 None 이 들어올수있음. 그래도 오류가 나지않게 예외처리 해야함.
    figure_description = text_handler(figure_data[1])
    print(figure_description)
    llm_input = figure_description + " the figure's html contents are: " + markdown
    llm_ouput = content_convert(prompt=figure_handler_prompt, data=llm_input)
    return llm_ouput


# === 카테고리 핸들러 매핑 ===
category_to_handler: Dict[str, Callable[[dict], str]] = {
    "equation": equation_handler,
    "chart": chart_handler,
    "figure": figure_handler,
    # 이하의 카테고리는 동일한 text_handler로 처리
}

# === 전체 파서 함수 ===
# data = datas['elements'][i]
# data_category = data['category']
# data_contents = data['contents']
def parse_data_by_category(data):
    data_category = data[0]['category'] if isinstance(data, tuple) else data['category']
    handler = category_to_handler.get(data_category, text_handler)
    return handler(data)


def find_nearest_context_text(datas, current_index, max_search_num = 6):
    # 앞쪽 탐색 (우선순위)
    for offset in range(1, max_search_num):  # 최대 5칸까지 탐색
        idx = current_index - offset
        if idx >= 0:
            text = text_handler(datas[idx])
            if text.strip():
                return text

    # 뒤쪽 탐색 (앞쪽에 없을 경우)
    for offset in range(1, 6):
        idx = current_index + offset
        if idx < len(datas):
            text = text_handler(datas[idx])
            if text.strip():
                return text

    return ""  # 앞뒤 모두 실패 시




def group_by_page_with_handlers(datas):
    page_texts = defaultdict(list)

    for i, element in enumerate(datas):
        page = element.get("page", -1)
        category = element.get("category", "")

        if category in ["chart", "figure", "equation"]:
            context_text = find_nearest_context_text(datas, i)
            pair = (element, {"content": {"html": context_text}} if context_text else None)
            parsed_text = parse_data_by_category(pair)
        else:
            parsed_text = parse_data_by_category(element)

        page_texts[page].append(parsed_text)

    return [
        {"page": page, "content": "\n".join(texts)}
        for page, texts in sorted(page_texts.items())
    ]


def convert_grouped_pages_to_documents(grouped_pages):
    """
    grouped_pages: [{'page': 1, 'content': "..."}, ...]
    → [Document(page_content="...", metadata={"page": 1}), ...]
    """
    documents = []
    for page in grouped_pages:
        doc = Document(
            page_content=page['content'],
            metadata={"page": page['page']}
        )
        documents.append(doc)
    return documents

def chat_with_solar(messages):
    response = client.chat.completions.create(
        model="solar-pro",
        messages=messages
    )
    return response.choices[0].message.content


def upstageParser2Document(file_path):
    response = requests.post(
        "https://api.upstage.ai/v1/document-digitization",
        headers={"Authorization": f"Bearer {api_key}"},
        files={"document": open(file_path, "rb")},
        data={
        "ocr": "force", # OCR을 강제로 수행하도록 설정 ("auto"로 설정 시 이미지 문서에서만 OCR 수행)
        "coordinates": False, # 각 레이아웃 요소의 위치 정보 반환 여부
        "chart_recognition": True, # 차트 인식 여부 
        "output_formats": '["html"]', # 결과를 HTML 형식으로 반환 (text, markdown도 가능)
        "base64_encoding": '["table"]', # 표에 대한 base64 인코딩 요청
        "model": "document-parse" # 사용할 모델 지정 
        }
        )
    # 문서 생성
    datas = response.json()["elements"]
    grouped_pages = group_by_page_with_handlers(datas)
    documents = convert_grouped_pages_to_documents(grouped_pages)
    print(documents)
    return documents

# file_path = "data/10 Vector Calculus.pdf" # ex: ./image.png
# document = upstageParser2Document(file_path=file_path)