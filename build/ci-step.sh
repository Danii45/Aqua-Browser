#!/bin/bash
# Runs one build step and keeps its output in ci-logs/<name>.log. If the step fails, its last lines are posted
# as a GitHub error annotation, which can be read through the GitHub API even when the full log can't.
#   bash build/ci-step.sh <name> <command...>
name="$1"; shift
mkdir -p ci-logs
"$@" 2>&1 | tee "ci-logs/$name.log"
rc=${PIPESTATUS[0]}
if [ "$rc" -ne 0 ]; then
  msg="$(tail -n 45 "ci-logs/$name.log" | tr -d '\r' | sed 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}')"
  echo "::error title=${name} failed (exit ${rc})::${msg}"
fi
exit "$rc"
