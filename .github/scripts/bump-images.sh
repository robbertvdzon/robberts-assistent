#!/usr/bin/env bash
set -euo pipefail
# usage: bump-images.sh <kustomization-dir> <commit-message> <image-arg> [<image-arg> ...]
#
# Bewerkt de image-tag(s) in kustomization.yaml en pusht rechtstreeks naar main
# (met retry bij een race met een gelijktijdige push). Simpeler dan softwarefactory's
# eigen bump-images.sh (die inmiddels een PR + required-checks-flow gebruikt) — dat
# is hier niet nodig zolang er geen branch-protection/required-checks op main staat.
KUST_DIR="$1"
COMMIT_MSG="$2"
shift 2
IMAGE_ARGS=("$@")

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

command -v kustomize >/dev/null 2>&1 || {
  echo "[bump] installing kustomize..."
  curl -fsSL https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh | bash
  sudo mv kustomize /usr/local/bin/
}

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

for attempt in 1 2 3 4 5; do
  git fetch origin main --quiet
  git reset --hard origin/main

  (cd "$KUST_DIR" && kustomize edit set image "${IMAGE_ARGS[@]}")

  if git diff --quiet; then
    echo "[bump] kustomization.yaml already up to date."
    exit 0
  fi

  git add "$KUST_DIR/kustomization.yaml"
  git commit -m "$COMMIT_MSG"

  if git push origin HEAD:main; then
    echo "[bump] pushed on attempt $attempt."
    exit 0
  fi
  echo "[bump] push race on attempt $attempt, retrying..."
done

echo "[bump] failed after retries." >&2
exit 1
