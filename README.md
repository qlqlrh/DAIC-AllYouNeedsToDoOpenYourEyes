# 🎓 SpeakNote: 실시간 강의 AI 주석 시스템

## 📌 개요
> SpeakNote는 대학 강의나 세미나 중 실시간으로 음성을 텍스트로 변환하고, 이를 AI가 정제·요약하여 슬라이드에 자동 주석으로 달아주는 시스템입니다. 연구자, 대학원생, 고등교육 수강자를 주요 대상으로 하며, 수업 이해도 향상 및 필기 부담 해소를 목표로 합니다.

## 🎯 문제 정의 및 기대 효과
전통적인 STT 시스템은 단순한 텍스트 변환이나 요약 기능에 그쳐, 사용자가 여전히 수동으로 노트 필기를 해야 하는 한계가 있습니다.  

실시간 강의 내용을 빠르게 정리하고 즉시 주석화할 수 있는 시스템이 도입된다면, 강의 이해도는 물론 복습 효율도 크게 향상될 수 있습니다.  
SpeakNote는 AI 기반의 자동 주석화 기능을 통해 사용자의 필기 부담을 줄여주며, 강의 슬라이드 내용과 실시간 음성을 연동해 핵심 내용을 즉시 요약하고 메모할 수 있도록 돕습니다.  

이를 통해 연구와 복습 과정이 더욱 효율적으로 이루어질 수 있으며,  생성된 노트를 다른 사용자와 쉽게 공유하는 협업 환경도 구축할 수 있습니다.

## ✅ Upstage API 활용
> 적용한 기술이나 Upstage API를 어떻게 적용했는지를 작성해주시면 됩니다.

## 🚀 주요 기능
> 프로젝트의 주요 기능을 구체적으로 설명해주세요. Application 내 구현된 부분을 이미지로 함께 첨부하셔도 좋습니다.
> 창의적인 접근 방식이나 기존 방법과의 차별점을 서술해주시면 좋습니다.

- ✨ 기능 1: 핵심 기능 설명
    - 이로 인한 장점
- ✨ 기능 2: 또 다른 주요 기능 설명

## 🖼️ 데모
> 스크린샷이나 데모 영상(GIF 또는 구글 드라이브 링크 등)을 포함해주세요.
- 예시:  
  ![데모 스크린샷](./assets/demo.png)

## 🔬 기술 구현 요약
- Google STT API (Streaming mode)
- Upstage DocumentParse API + Solar LLM 
- STT → GPT 정제 → 주석 렌더링 파이프라인 구현 
- WebSocket 기반 실시간 통신 구조 설계

## 🧰 기술 스택 및 시스템 아키텍처
> 사용한 언어 및 프레임워크를 작성하고 시스템 아키텍처 이미지를 첨부해주세요.

## 🔧 설치 및 사용 방법
> 리포지토리 클론 이후 application을 실행할 수 있는 명령어를 작성해주세요.
> 실행할 수 있는 환경이 제한되어 있는 경우, 이곳에 배포 환경을 알려주세요.
> 실제로 배포하고 있다면, 배포 중인 사이트를 알려주셔도 됩니다.
> 아래는 예시입니다.

```
git clone https://github.com/your-username/project-name.git
cd project-name
pip install -r requirements.txt
```

## 📁 프로젝트 구조
> 프로젝트 루트 디렉토리에서 주요 파일 및 폴더의 역할 및 목적을 작성해주세요.
> 필요없다고 생각되는 부분은 생략하셔도 됩니다.
> 아래는 예시입니다.

```
project-name/
├── README.md               # 프로젝트 설명서
├── app.py                  # 애플리케이션 메인 파일
├── src/                    # 핵심 로직, 파이프라인, 유틸리티 등
│   ├── model.py
│   └── utils.py
├── models/                 # 모델 체크포인트 및 학습된 가중치
├── assets/                 # 이미지, 동영상, 샘플 출력 등
├── data/                   # 샘플 입력/출력 데이터
└── tests/                  # 테스트 코드
```

## 🧑‍🤝‍🧑 팀원 소개
| 이름  | 역할                              | GitHub                                     |
|-----|---------------------------------|--------------------------------------------|
| 김예슬 | 팀장 / PDF 기능, 주석 드래그앤드롭 기능       | [@yeseul](https://github.com/yeseul-kim01) |
| 김동인 | 프론트-백엔드 웹소켓 연동, 실시간 음성인식 기능     | [@qlqlrh](https://github.com/qlqlrh)       |
| 정유성 | RAG 적용, Upstage DP 적용, Solar 적용 | [@yoosung](https://github.com/yoosung5480)       |


## 💡 참고 자료 및 아이디어 출처 (Optional)
> 프로젝트를 개발하면서 참고했던 논문 및 기타 문헌이 있으시다면 첨부해주세요.
> 아래는 예시입니다.

* [Upstage Document Parse](https://www.upstage.ai/products/document-parse)
* [Upstage Building end-to-end RAG system using Solar LLM and MongoDB Atlas](https://www.upstage.ai/blog/en/building-rag-system-using-solar-llm-and-mongodb-atlas)
