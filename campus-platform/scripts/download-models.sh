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
# HF_ENDPOINT lets mainland-China users switch to the official Chinese mirror.
# Default: hf-mirror.com (HuggingFace-endorsed mirror) so the script works
# without any extra config. Set HF_ENDPOINT=https://huggingface.co to force the
# canonical host.
HF_ENDPOINT="${HF_ENDPOINT:-https://hf-mirror.com}"
BASE_URL="${HF_ENDPOINT}/${HF_REPO}/resolve/main"

mkdir -p "$MODEL_DIR"

# Xenova/bge-reranker-base stores ONNX under onnx/ subdirectory.
# We download the int8 quantized version (~278MB) instead of the full model
# (~1.1GB) to keep the download fast and the disk footprint reasonable.
# (filename -> remote path -> expected-min-bytes)
FILES=(
  "model.onnx -> onnx/model_int8.onnx|278000000"
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
  # Parse "localName -> remotePath|expectedSize"
  local_name="${entry%% -> *}"
  rest="${entry##* -> }"
  remote_path="${rest%%|*}"
  minsize="${rest##*|}"
  target="$MODEL_DIR/$local_name"
  if [ -f "$target" ] && [ "$(stat -c%s "$target" 2>/dev/null || stat -f%z "$target")" -ge "$minsize" ]; then
    echo "  ✅  ${local_name} (cached)"
  else
    NEED_DOWNLOAD=1
  fi
done

if [ "$NEED_DOWNLOAD" -eq 0 ]; then
  echo "All reranker files already present and valid. Nothing to do."
  exit 0
fi

for entry in "${FILES[@]}"; do
  local_name="${entry%% -> *}"
  rest="${entry##* -> }"
  remote_path="${rest%%|*}"
  target="$MODEL_DIR/$local_name"
  if [ -f "$target" ]; then
    echo "  ↻  Re-downloading ${local_name} (existing file too small / corrupt)"
    rm -f "$target"
  fi
  url="$BASE_URL/$remote_path"
  echo "  ↓  ${local_name}  ←  ${url}"
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
