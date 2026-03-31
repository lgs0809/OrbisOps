#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_SOURCE="${SCRIPT_DIR}/code-workspace-mcp-server.mjs"
INSTALL_DIR="${CODE_WORKSPACE_INSTALL_DIR:-${HOME}/.orbisops-code-workspace-mcp}"
REPOSITORY_ROOT="${CODE_WORKSPACE_REPOSITORY_ROOT:-${1:-$PWD}}"
REPOSITORY_ID="${CODE_WORKSPACE_REPOSITORY_ID:-default}"
CONFIG_FILE="${INSTALL_DIR}/code-workspace.env"
LAUNCHER="${INSTALL_DIR}/start-code-workspace-mcp.sh"
SERVER_TARGET="${INSTALL_DIR}/code-workspace-mcp-server.mjs"

JAVA_VERSIONS=("1.43.0" "1.42.0")
PYTHON_VERSIONS=("1.1.410" "1.1.409" "1.1.408")

fail() {
  echo "error: $*" >&2
  exit 2
}

command -v node >/dev/null 2>&1 || fail "Node.js 18+ is required"
NODE_MAJOR="$(node -p 'Number(process.versions.node.split(".")[0])')"
[[ "${NODE_MAJOR}" -ge 18 ]] || fail "Node.js 18+ is required; found $(node --version)"
[[ -f "${SERVER_SOURCE}" ]] || fail "missing Code Workspace MCP server: ${SERVER_SOURCE}"
REPOSITORY_ROOT="$(cd "${REPOSITORY_ROOT}" 2>/dev/null && pwd)" || fail "repository root does not exist"
[[ -e "${REPOSITORY_ROOT}/.git" ]] || fail "repository root must be a Git worktree: ${REPOSITORY_ROOT}"
[[ "${REPOSITORY_ID}" =~ ^[A-Za-z0-9._-]+$ ]] || fail "repository id may contain only A-Z a-z 0-9 . _ -"

prompt_value() {
  local env_value="$1"
  local prompt="$2"
  if [[ -n "${env_value}" ]]; then
    printf '%s' "${env_value}"
    return
  fi
  local value=""
  read -r -p "${prompt}" value </dev/tty
  printf '%s' "${value}"
}

choose_version() {
  local language="$1"
  local configured="$2"
  shift 2
  local versions=("$@")
  local selection="${configured}"
  if [[ -z "${selection}" ]]; then
    echo
    echo "${language} LSP supported versions:"
    local index=1
    for version in "${versions[@]}"; do
      if [[ "${index}" -eq 1 ]]; then
        echo "  ${index}) ${version} (recommended)"
      else
        echo "  ${index}) ${version}"
      fi
      index=$((index + 1))
    done
    selection="$(prompt_value "" "Choose ${language} LSP version [Enter=${versions[0]}]: ")"
  fi
  [[ -n "${selection}" ]] || selection="1"
  local chosen=""
  if [[ "${selection}" =~ ^[0-9]+$ ]] && [[ "${selection}" -ge 1 ]] && [[ "${selection}" -le "${#versions[@]}" ]]; then
    chosen="${versions[$((selection - 1))]}"
  else
    for version in "${versions[@]}"; do
      if [[ "${selection}" == "${version}" ]]; then
        chosen="${version}"
        break
      fi
    done
  fi
  [[ -n "${chosen}" ]] || fail "unsupported ${language} LSP version: ${selection}"
  printf '%s' "${chosen}"
}

selection="${CODE_WORKSPACE_SETUP_SELECTION:-}"
if [[ -z "${selection}" ]]; then
  echo "Select LSP languages for Code Workspace MCP:"
  echo "  Enter) no LSP"
  echo "  1) Java (Eclipse JDT LS)"
  echo "  2) Python (Pyright)"
  echo "  You can select multiple languages, e.g. 1 2"
  selection="$(prompt_value "" "LSP selection: ")"
fi

selection="$(printf '%s' "${selection}" | tr ',;' '  ')"
selection_tokens=()
IFS=' ' read -r -a selection_tokens <<< "${selection}" || true
languages=()
java_selected=false
python_selected=false
for token in "${selection_tokens[@]}"; do
  case "${token}" in
    1)
      if [[ "${java_selected}" != true ]]; then languages+=("java"); java_selected=true; fi
      ;;
    2)
      if [[ "${python_selected}" != true ]]; then languages+=("python"); python_selected=true; fi
      ;;
    "") ;;
    *) fail "unknown LSP selection '${token}'; use 1, 2, or '1 2'" ;;
  esac
done

java_version=""
python_version=""
if [[ "${java_selected}" == true ]]; then
  java_version="$(choose_version "Java / Eclipse JDT LS" "${CODE_WORKSPACE_SETUP_JAVA_VERSION:-}" "${JAVA_VERSIONS[@]}")"
fi
if [[ "${python_selected}" == true ]]; then
  python_version="$(choose_version "Python / Pyright" "${CODE_WORKSPACE_SETUP_PYTHON_VERSION:-}" "${PYTHON_VERSIONS[@]}")"
fi

mkdir -p "${INSTALL_DIR}"
cp "${SERVER_SOURCE}" "${SERVER_TARGET}"
chmod 755 "${SERVER_TARGET}"

languages_csv=""
if [[ "${#languages[@]}" -gt 0 ]]; then
  languages_csv="$(IFS=,; echo "${languages[*]}")"
fi

{
  printf 'export CODE_WORKSPACE_REPOSITORY_ROOT=%q\n' "${REPOSITORY_ROOT}"
  printf 'export CODE_WORKSPACE_REPOSITORY_ID=%q\n' "${REPOSITORY_ID}"
  printf 'export CODE_WORKSPACE_STATE_ROOT=%q\n' "${INSTALL_DIR}/state"
  printf 'export CODE_WORKSPACE_LSP_LANGUAGES=%q\n' "${languages_csv}"
  if [[ -n "${java_version}" ]]; then
    printf 'export CODE_WORKSPACE_JDTLS_VERSION=%q\n' "${java_version}"
    printf 'export CODE_WORKSPACE_JDTLS_AUTO_INSTALL=true\n'
  fi
  if [[ -n "${python_version}" ]]; then
    printf 'export CODE_WORKSPACE_PYRIGHT_VERSION=%q\n' "${python_version}"
    printf 'export CODE_WORKSPACE_PYRIGHT_AUTO_INSTALL=true\n'
  fi
} > "${CONFIG_FILE}"
chmod 600 "${CONFIG_FILE}"

cat > "${LAUNCHER}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "${HERE}/code-workspace.env"
exec node "${HERE}/code-workspace-mcp-server.mjs"
EOF
chmod 755 "${LAUNCHER}"

if [[ "${CODE_WORKSPACE_SETUP_QUIET:-false}" != "true" ]]; then
  echo
  echo "Code Workspace MCP installed:"
  echo "  repository : ${REPOSITORY_ROOT}"
  echo "  repositoryId: ${REPOSITORY_ID}"
  if [[ -z "${languages_csv}" ]]; then
    echo "  LSP        : disabled"
  else
    echo "  LSP        : ${languages_csv}"
    [[ -z "${java_version}" ]] || echo "  Java       : Eclipse JDT LS ${java_version}"
    [[ -z "${python_version}" ]] || echo "  Python     : Pyright ${python_version}"
  fi
  echo "  launcher   : ${LAUNCHER}"
  echo
  echo "The selected language servers are downloaded lazily into ${INSTALL_DIR}/state/runtimes on first code_lsp use."
fi
