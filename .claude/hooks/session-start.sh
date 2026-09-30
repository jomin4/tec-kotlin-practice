#!/bin/bash
# 세션 시작 시 진행 상태를 컨텍스트에 주입한다. (CLAUDE.md 7번 세션 시작 루틴 보조)
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

git fetch -q origin 2>/dev/null

echo "=== [학습 프로젝트] 세션 시작 체크 (8단계 실습 완료, README.md 참고) ==="
echo "CLAUDE.md의 '7. 세션 시작 루틴'을 따르고, 첫 답변에서 현재 단계와 다음 할 일을 알려준다."
echo "대화 중 진행 방식이 바뀌면 '9. 진행 방식 변경 규칙'대로 CLAUDE.md를 즉시 갱신한다."
echo

if [ -f docs/PROGRESS.md ]; then
  sed -n '/^## 현재 상태/,/^## 프로젝트 개요/p' docs/PROGRESS.md | sed '$d'
fi

echo "현재 브랜치: $(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
upstream=$(git rev-parse --abbrev-ref --symbolic-full-name @{u} 2>/dev/null)
if [ -n "$upstream" ]; then
  new_commits=$(git log --oneline HEAD.."$upstream" 2>/dev/null)
  if [ -n "$new_commits" ]; then
    echo "$upstream 에 로컬에 없는 커밋이 있다 (pull 필요):"
    echo "$new_commits"
  else
    echo "$upstream 의 새 커밋 없음"
  fi
fi
exit 0
