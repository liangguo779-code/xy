#!/bin/bash
# Download the BGE cross-encoder reranker model that the LangChain4j RAG pipeline
# needs at first chat request. The BGE small-zh embedder is bundled inside the
# langchain4j-embeddings-bge-small-zh jar (no download needed for the embedder).
#
# Re-run is safe: the script is idempotent and skips files that already exist
# with the expected size. Models are cached under
#   backend/campus-ai/runtime/models/bge-reranker-base/
# so they survive `mvn clean` and are not committed to git.

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MODEL_DIR="$PROJECT_DIR/backend/campus-ai/runtime/models/bge-reranker-base"
HF_REPO="${HF_RERANKER_REPO:-Xenova/bge-reranker-base}"
BASE_URL="https://huggingface.co/${HF_REPO}/resolve/main"

mkdir -p "$MODEL_DIR"

# (filename, expected-min-bytes)
FILES=(
  "model.onnx|278000000"
  "tokenizer.json|700000"
)

# Use curl on Windows Git Bash, wget elsewhere.
if command -v curl > /dev/null 2>&1; then
  download() {
    local url="$1"
    local target="$2"
    curl -L --fail --silent --show-error -o "$target" "$url"
  }
elif command -v wget > /dev/null 2>&1; then
  download() {
    local url="$1"
    local target="$2"
    wget -q -O "$target" "$url"
  }
else
  echo "❌  Neither curl nor wget is available; install one of them first." >&2
  exit 1
fi

echo "=========================================="
echo "  Pre-downloading BGE reranker model"
echo "  source: ${HF_REPO}"
echo "  target: ${MODEL_DIR}"
echo "=========================================="

NEED_DOWNLOAD=0
for entry in "${FILES[@]}"; do
  fname="${entry%%|*}"
  minsize="${entry##*|}"
  target="$MODEL_DIR/$fname"
  if [ -f "$target" ] && [ "$(stat -c%s "$target" 2>/dev/null || stat -f%z "$target")" -ge "$minsize" ]; then
    echo "  ✅  ${fname} (cached)"
  else
    NEED_DOWNLOAD=1
  fi
done

if [ "$NEED_DOWNLOAD" -eq 0 ]; then
  echo "All reranker files already present and valid. Nothing to do."
  exit 0
fi

for entry in "${FILES[@]}"; do
  fname="${entry%%|*}"
  target="$MODEL_DIR/$fname"
  if [ -f "$target" ]; then
    echo "  ↻  Re-downloading ${fname} (existing file too small / corrupt)"
    rm -f "$target"
  fi
  url="$BASE_URL/$fname"
  echo "  ↓  ${fname}  ←  ${url}"
  download "$url" "$target"
  size=$(stat -c%s "$target" 2>/dev/null || stat -f%z "$target")
  echo "      (${size} bytes)"
done

# Honour HF_HUB_OFFLINE if the user asked for it.
if [ "${HF_HUB_OFFLINE:-0}" = "1" ]; then
  echo "ℹ️   HF_HUB_OFFLINE=1 set — runtime will skip any further download attempts."
fi

echo
echo "✅  BGE reranker is ready. Subsequent `bash start-dev.sh` runs will not redownload."
