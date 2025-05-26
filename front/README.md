# SpeakNote Frontend

SpeakNote의 프론트엔드 프로젝트입니다.  
절대 !! `npm audit fix --force` 를 하지 말 것. 경고가 떠도 그냥 놔두기.  
이거 실행하면 바로 의존성 에러 납니다..

## 기술 스택

- Next.js 15.3.2
- React 19
- TypeScript
- TailwindCSS
- PDF 관련 라이브러리 (pdf-lib, react-pdf, jspdf)

## 시작하기

### 필수 요구사항

- Node.js (최신 LTS 버전 권장)
- npm

### 설치 및 실행

1. 레포지토리 클론

```bash
git clone [레포지토리 URL]
cd front
```

2. 의존성 패키지 설치

```bash
npm install --legacy-peer-deps
```

> Note: React 19와 일부 라이브러리 간의 의존성 충돌로 인해 `--legacy-peer-deps` 옵션이 필요합니다.

3. 개발 서버 실행

```bash
npm run dev
```

4. 브라우저에서 확인

- http://localhost:3000 으로 접속

## 문제 해결

### canvas 모듈 관련 에러

PDF.js가 서버 사이드에서 PDF를 처리할 때 필요한 `canvas` 모듈이 이미 프로젝트에 포함되어 있습니다. 별도의 설치가 필요하지 않습니다.

### 의존성 충돌

React 19와 일부 라이브러리 간의 의존성 충돌이 있을 수 있습니다. 이는 `--legacy-peer-deps` 옵션을 사용하여 해결할 수 있습니다.
