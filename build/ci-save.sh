#!/bin/bash
# Saves this job's screenshots and logs to the ci-<os> branch, so they can be fetched with plain git.
#   TOKEN=... bash build/ci-save.sh <os>
os="$1"
out="$(mktemp -d)"
[ -d shots ] && cp -R shots "$out/"
[ -d ci-logs ] && cp -R ci-logs "$out/"
echo "${GITHUB_SHA} ${GITHUB_RUN_ID} $(date -u +%FT%TZ)" > "$out/RUN.txt"
cd "$out"
git init -q -b "ci-$os"
git add -A
git -c user.name="github-actions[bot]" -c user.email="41898282+github-actions[bot]@users.noreply.github.com" commit -q -m "CI results ($os) for ${GITHUB_SHA:0:7}"
git push -q -f "https://x-access-token:${TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "ci-$os"
