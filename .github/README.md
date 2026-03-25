## 🔀 Pull Request(PR) 사용 가이드

Run-a-B 프로젝트는 모든 작업을 **브랜치 + Pull Request 방식**으로 관리합니다.  
`main` 브랜치에는 직접 push 하지 않습니다.

---

### ✅ 작업 순서

1️⃣ main 최신화

```bash
git checkout main
git pull origin main
2️⃣ 새 브랜치 생성
git checkout -b feat/web-login
3️⃣ 작업 후 커밋
git add .
git commit -m "feat: 로그인 화면 추가"
4️⃣ 원격 브랜치로 push
git push -u origin feat/web-login
5️⃣ GitHub에서 Pull Request 생성
레포 이동
Compare & pull request 클릭
PR 작성 후 생성
6️⃣ 리뷰 후 merge
🌿 브랜치 이름 규칙
형식
타입/작업내용
타입 종류
feat/ : 기능 추가
fix/ : 버그 수정
docs/ : 문서 수정
refactor/ : 코드 개선
style/ : UI / CSS 수정
chore/ : 설정 / 패키지 작업
test/ : 테스트 코드
예시
프론트
feat/web-login
feat/web-dashboard
style/web-navbar
fix/web-routing
백엔드
feat/api-auth
feat/api-policy-list
fix/api-error
문서/설정
docs/readme-update
chore/project-structure
📝 커밋 메시지 규칙
형식
타입: 작업 내용
예시
feat: 로그인 페이지 추가
fix: 정책 목록 조회 오류 수정
docs: 협업 가이드 추가
chore: 프로젝트 초기 구조 생성
📌 Pull Request 작성 방법
제목 예시
feat: 로그인 페이지 구현
내용 예시
## 작업 내용
- 로그인 페이지 UI 구현
- 입력 폼 구성
- 버튼 스타일 적용

## 확인 사항
- 로컬 실행 확인 완료
- 불필요한 파일 없음
⚠️ 협업 규칙
main 브랜치 직접 push 금지
모든 작업은 새 브랜치에서 진행
PR 생성 후 merge 진행
하나의 PR에는 하나의 기능만 포함
작업 전 항상 main 최신화
🚫 금지 사항
의미 없는 브랜치 이름 사용 (ex. test, 123)
의미 없는 커밋 메시지 (ex. 수정, dd)
main 브랜치 직접 작업
