#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="${ORBISOPS_MIGRATION_MANIFEST:-${ROOT_DIR}/db/migrations/manifest.tsv}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-orbisops}"
MYSQL_USERNAME="${MYSQL_USERNAME:-orbisops}"
MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-}"

[[ "${MYSQL_DATABASE}" =~ ^[a-zA-Z0-9_]+$ ]] || { echo "invalid MYSQL_DATABASE identifier" >&2; exit 2; }

command -v mysql >/dev/null 2>&1 || { echo "mysql client is required" >&2; exit 2; }
[[ -f "${MANIFEST}" ]] || { echo "migration manifest not found: ${MANIFEST}" >&2; exit 2; }

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

mysql_exec() {
  if [[ -n "${MYSQL_DEFAULTS_FILE}" ]]; then
    [[ -f "${MYSQL_DEFAULTS_FILE}" ]] || { echo "MYSQL_DEFAULTS_FILE does not exist" >&2; exit 2; }
    mysql --defaults-extra-file="${MYSQL_DEFAULTS_FILE}" --host="${MYSQL_HOST}" --port="${MYSQL_PORT}" --user="${MYSQL_USERNAME}" --protocol=TCP "$@"
    return
  fi
  mysql --host="${MYSQL_HOST}" --port="${MYSQL_PORT}" --user="${MYSQL_USERNAME}" --protocol=TCP "$@"
}

mysql_exec -e "CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql_exec "${MYSQL_DATABASE}" -e "CREATE TABLE IF NOT EXISTS orbisops_schema_history (version VARCHAR(16) PRIMARY KEY, description VARCHAR(128) NOT NULL, checksum CHAR(64) NOT NULL, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"

while IFS=$'\t' read -r service version description sql_path expected_checksum created_at; do
  [[ -z "${service}" || "${service}" == \#* ]] && continue
  [[ "${service}" == "orbisops" ]] || continue
  sql_file="${ROOT_DIR}/${sql_path}"
  [[ -f "${sql_file}" ]] || { echo "missing migration ${version}: ${sql_path}" >&2; exit 3; }
  actual_checksum="$(sha256_file "${sql_file}")"
  [[ "${actual_checksum}" == "${expected_checksum}" ]] || { echo "checksum mismatch for migration ${version}" >&2; exit 4; }
  applied_checksum="$(mysql_exec "${MYSQL_DATABASE}" --batch --skip-column-names -e "SELECT checksum FROM orbisops_schema_history WHERE version='${version}'" 2>/dev/null || true)"
  if [[ -n "${applied_checksum}" ]]; then
    [[ "${applied_checksum}" == "${expected_checksum}" ]] || { echo "applied migration ${version} has a different checksum" >&2; exit 5; }
    continue
  fi
  if [[ "${version}" == "001" ]]; then
    table_count="$(mysql_exec "${MYSQL_DATABASE}" --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name <> 'orbisops_schema_history'")"
    [[ "${table_count}" == "0" ]] || { echo "refusing baseline migration on a non-empty database without migration 001 history" >&2; exit 6; }
  fi
  echo "Applying ${version} ${description}"
  if [[ "${version}" == "001" ]]; then
    # The checksummed historical dump names its original database. The caller
    # already selected/created the target; omit only these two legacy directives.
    # Do not rewrite the migration file or its recorded checksum.
    sed -e '/^CREATE database if NOT EXISTS `orbisops` default character set utf8mb4 collate utf8mb4_0900_ai_ci;$/d' \
        -e '/^use `orbisops`;$/d' "${sql_file}" | mysql_exec "${MYSQL_DATABASE}"
  else
    mysql_exec "${MYSQL_DATABASE}" < "${sql_file}"
  fi
  mysql_exec "${MYSQL_DATABASE}" -e "INSERT INTO orbisops_schema_history(version,description,checksum) VALUES('${version}','${description}','${expected_checksum}')"
done < "${MANIFEST}"

echo "OrbisOps migrations are up to date."
