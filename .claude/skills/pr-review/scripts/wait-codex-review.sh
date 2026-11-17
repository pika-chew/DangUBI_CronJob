#!/usr/bin/env bash
# PR의 현재 head 커밋에 대한 Codex 리뷰가 끝날 때까지 기다린 뒤, 그 커밋의 Codex 지적을 출력한다.
#
# 사용: wait-codex-review.sh <PR번호> [--trigger]
#   --trigger  시작하자마자 "@codex review" 댓글로 리뷰를 요청한다 (이미 열린 PR에 커밋을 더 푸시한 경우).
#              없으면 TRIGGER_AFTER초 동안 리뷰가 시작되지 않을 때 한 번만 요청한다.
#
# 완료 판단: Codex 요약 댓글의 상태 표시(codex-review-state:v1)가 head 커밋에 대해 status=completed.
# 종료 코드: 0 리뷰 완료(지적 출력), 2 시간 초과, 1 사용 오류.
set -euo pipefail

PR="${1:-}"
[[ "$PR" =~ ^[0-9]+$ ]] || { echo "사용: $0 <PR번호> [--trigger]" >&2; exit 1; }
TRIGGER_NOW="${2:-}"

BOT="chatgpt-codex-connector[bot]"
INTERVAL="${INTERVAL:-60}"            # 확인 간격(초)
TIMEOUT="${TIMEOUT:-2400}"            # 최대 대기(초), 기본 40분
TRIGGER_AFTER="${TRIGGER_AFTER:-600}" # 이 시간 동안 리뷰가 시작되지 않으면 한 번 요청(초)

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
STATE_JQ='[.[] | select(.user.login == "'"$BOT"'") | select(.body | contains("codex-review-state:v1"))] | last
  | if . == null then "none none none"
    else (.body | capture("codex-review-state:v1 (?<s>\\{.*?\\}) -->").s | fromjson | .code_review
          | "\(.commit_sha) \(.status) \(.has_any_findings)") end'

triggered=""
if [[ "$TRIGGER_NOW" == "--trigger" ]]; then
  gh pr comment "$PR" --body "@codex review" >/dev/null
  triggered="yes"
  echo "[$(date -u +%H:%M:%S)] @codex review 요청"
fi

elapsed=0
while :; do
  HEAD="$(gh pr view "$PR" --json headRefOid -q .headRefOid)"
  read -r sha status findings < <(gh api "repos/$REPO/issues/$PR/comments" --paginate --jq "$STATE_JQ" | tail -n 1)

  if [[ "$sha" == "$HEAD" && "$status" == "completed" ]]; then
    break
  fi
  echo "[$(date -u +%H:%M:%S)] 대기 중: head=${HEAD:0:7} codex=${sha:0:7} status=$status"

  if [[ -z "$triggered" && "$elapsed" -ge "$TRIGGER_AFTER" && "$sha" != "$HEAD" ]]; then
    gh pr comment "$PR" --body "@codex review" >/dev/null
    triggered="yes"
    echo "[$(date -u +%H:%M:%S)] 리뷰가 시작되지 않아 @codex review 요청"
  fi
  if [[ "$elapsed" -ge "$TIMEOUT" ]]; then
    echo "TIMEOUT: ${TIMEOUT}초 안에 head ${HEAD:0:7}의 Codex 리뷰가 끝나지 않았다."
    exit 2
  fi
  sleep "$INTERVAL"
  elapsed=$((elapsed + INTERVAL))
done

echo "CODEX_REVIEW_DONE pr=$PR repo=$REPO commit=$HEAD has_findings=$findings"
echo
echo "## 리뷰 본문 (인라인이 아닌 지적이 있으면 여기에 있다)"
gh api "repos/$REPO/pulls/$PR/reviews" --paginate --jq \
  '.[] | select(.user.login == "'"$BOT"'" and .commit_id == "'"$HEAD"'") | .body'
echo
echo "## 인라인 지적"
gh api "repos/$REPO/pulls/$PR/comments" --paginate --jq \
  '.[] | select(.user.login == "'"$BOT"'" and .commit_id == "'"$HEAD"'" and .in_reply_to_id == null)
   | "### \(.path):\(.line // .original_line) (comment id \(.id))\n\(.body)\n"'
