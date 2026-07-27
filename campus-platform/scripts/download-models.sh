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
# hf-mirror.com is the HuggingFace-endorsed Chinese mirror; works in mainland China.
# Set HF_ENDPOINT=https://huggingface.co to force the canonical host.
HF_ENDPOINT="${HF_ENDPOINT:-https://hf-mirror.com}"
BASE_URL="${HF_ENDPOINT}/${HF_REPO}/resolve/main"

mkdir -p "$MODEL_DIR"

# Parallel arrays: local filename, remote path (relative to BASE_URL), min-bytes.
#   model.onnx  — we fetch onnx/model_int8.onnx (~278MB int8) from the Xenova repo
#   tokenizer.json — downloaded from repo root (~17MB)
LOCAL_NAMES=(model.onnx  tokenizer.json)
REMOTE_PATHS=(onnx/model_int8.onnx  tokenizer.json)
MIN_SIZES=(278000000   700000)

# Use curl on Windows Git Bash, wget elsewhere.
if command -v curl > /dev/null 2>&1; then
  download() { curl -L --fail --silent --show-error -o "$1" "$2"; }
elif command -v wget > /dev/null 2>&1; then
  download() { wget -q -O "$1" "$2"; }
else
  echo "❌  Neither curl nor wget is available; install one of them first." >&2
  exit 1
fi

echo "=========================================="
echo "  Pre-downloading BGE reranker model"
echo "  source: ${HF_REPO}"
echo "  target: ${MODEL_DIR}"
echo "  mirror: ${HF_ENDPOINT}"
echo "=========================================="

NEED_DOWNLOAD=0
for i in "${!LOCAL_NAMES[@]}"; do
  fname="${LOCAL_NAMES[$i]}"
  minsize="${MIN_SIZES[$i]}"
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

for i in "${!LOCAL_NAMES[@]}"; do
  fname="${LOCAL_NAMES[$i]}"
  remote="${REMOTE_PATHS[$i]}"
  target="$MODEL_DIR/$fname"
  if [ -f "$target" ]; then
    echo "  ↻  Re-downloading ${fname} (existing file too small / corrupt)"
    rm -f "$target"
  fi
  url="$BASE_URL/$remote"
  echo "  ↓  ${fname}  ←  ${url}"
  download "$target" "$url"
  size=$(stat -c%s "$target" 2>/dev/null || stat -f%z "$target")
  echo "      (${size} bytes)"
done

# Honour HF_HUB_OFFLINE if the user asked for it.
if [ "${HF_HUB_OFFLINE:-0}" = "1" ]; then
  echo "ℹ️   HF_HUB_OFFLINE=1 set — runtime will skip any further download attempts."
fi

echo
echo "✅  BGE reranker is ready. Subsequent \`bash start-dev.sh\` runs will not redownload."
