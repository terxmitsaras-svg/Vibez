#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${ROOT_DIR}/artifacts"
ARCHIVE_NAME="vibez-code.zip"
ARCHIVE_PATH="${OUTPUT_DIR}/${ARCHIVE_NAME}"

mkdir -p "${OUTPUT_DIR}"
rm -f "${ARCHIVE_PATH}"

cd "${ROOT_DIR}"
zip -r "${ARCHIVE_PATH}" . \
  -x ".git/*" \
     "artifacts/*" \
     "node_modules/*" \
     "backend/node_modules/*" \
     "frontend/node_modules/*" \
     "android-kotlin/.gradle/*" \
     "android-kotlin/app/build/*"

echo "Archive created at: ${ARCHIVE_PATH}"
