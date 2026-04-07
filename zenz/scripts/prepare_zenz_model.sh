#!/usr/bin/env bash

set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: ${name}" >&2
    exit 1
  fi
}

require_env "ZENZ_MODEL_REPO"
require_env "ZENZ_MODEL_REVISION"
require_env "ZENZ_MODEL_QUANTIZATION"
require_env "ZENZ_MODEL_ASSET_NAME"
require_env "ZENZ_MODEL_OUTPUT_DIR"
require_env "ZENZ_MODEL_WORK_DIR"
require_env "ZENZ_LLAMA_CPP_DIR"

for cmd in python3 cmake; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Required command not found: ${cmd}" >&2
    exit 1
  fi
done

OUTPUT_DIR="${ZENZ_MODEL_OUTPUT_DIR}"
WORK_DIR="${ZENZ_MODEL_WORK_DIR}"
LLAMA_CPP_DIR="${ZENZ_LLAMA_CPP_DIR}"
MODEL_DIR="${WORK_DIR}/hf-model"
HOST_BUILD_DIR="${WORK_DIR}/llama-host-build"
VENV_DIR="${WORK_DIR}/python-venv"
F16_FILE="${WORK_DIR}/ggml-model-f16.gguf"
OUTPUT_FILE="${OUTPUT_DIR}/${ZENZ_MODEL_ASSET_NAME}"
STAMP_FILE="${WORK_DIR}/build-stamp.txt"
EXPECTED_STAMP="${ZENZ_MODEL_REPO}|${ZENZ_MODEL_REVISION}|${ZENZ_MODEL_QUANTIZATION}|${ZENZ_MODEL_ASSET_NAME}"
export MODEL_DIR

mkdir -p "${OUTPUT_DIR}" "${WORK_DIR}"

if [[ -f "${OUTPUT_FILE}" && -f "${STAMP_FILE}" ]] && grep -qx "${EXPECTED_STAMP}" "${STAMP_FILE}"; then
  echo "Zenz model already prepared at ${OUTPUT_FILE}"
  exit 0
fi

python3 -m venv "${VENV_DIR}"
PYTHON_BIN="${VENV_DIR}/bin/python"
PIP_BIN="${VENV_DIR}/bin/pip"

"${PIP_BIN}" install --upgrade pip >/dev/null
"${PIP_BIN}" install huggingface_hub >/dev/null
"${PIP_BIN}" install -r "${LLAMA_CPP_DIR}/requirements/requirements-convert_hf_to_gguf.txt" >/dev/null

rm -rf "${MODEL_DIR}"
mkdir -p "${MODEL_DIR}"

HF_TOKEN="${ZENZ_MODEL_HF_TOKEN:-}" "${PYTHON_BIN}" - <<'PY'
import os
from huggingface_hub import snapshot_download

repo_id = os.environ["ZENZ_MODEL_REPO"]
revision = os.environ["ZENZ_MODEL_REVISION"]
local_dir = os.path.abspath(os.environ["MODEL_DIR"])
token = os.environ.get("HF_TOKEN") or None

snapshot_download(
    repo_id=repo_id,
    revision=revision,
    local_dir=local_dir,
    local_dir_use_symlinks=False,
    token=token,
)
PY

cmake -S "${LLAMA_CPP_DIR}" -B "${HOST_BUILD_DIR}" \
  -DCMAKE_BUILD_TYPE=Release \
  -DLLAMA_BUILD_COMMON=ON \
  -DLLAMA_BUILD_EXAMPLES=ON \
  -DLLAMA_BUILD_SERVER=OFF \
  -DLLAMA_BUILD_TESTS=OFF >/dev/null

cmake --build "${HOST_BUILD_DIR}" --config Release --target llama-quantize -j >/dev/null

"${PYTHON_BIN}" "${LLAMA_CPP_DIR}/convert_hf_to_gguf.py" \
  "${MODEL_DIR}" \
  --outfile "${F16_FILE}" \
  --outtype f16 >/dev/null

"${HOST_BUILD_DIR}/bin/llama-quantize" \
  "${F16_FILE}" \
  "${OUTPUT_FILE}" \
  "${ZENZ_MODEL_QUANTIZATION}" >/dev/null

printf '%s\n' "${EXPECTED_STAMP}" > "${STAMP_FILE}"
echo "Prepared ${OUTPUT_FILE}"
