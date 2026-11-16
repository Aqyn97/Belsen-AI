#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test "$(node -p 'process.versions.node.split(".")[0]')" = 24 || { echo "Use Node.js 24." >&2; exit 1; }
# Prerequisites: Node 24, Java 21, Docker (for PostgreSQL integration tests).
# npm ci installs exactly the committed lockfile; no globally installed frontend tools are used.
(cd frontend && npm ci && npm run typecheck && npm run lint && npm test && npm run build)
test -s frontend/dist/index.html
./backend/mvnw -B -ntp -f backend/pom.xml -Pspa clean verify
