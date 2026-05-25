#!/usr/bin/env bash
#
# restore-mysql.sh — 从一个 .sql.gz 备份恢复 MoodFM 数据库
#
# 用法:
#   ./scripts/restore-mysql.sh <backup-file.sql.gz>
#
# 行为:
#   1. 校验入参 (必填, 文件存在, .sql.gz 后缀)
#   2. 二次确认 (默认 N)
#   3. gunzip | docker exec mysql 写入 moodfm 库
#
set -euo pipefail

usage() {
  echo "Usage: $0 <backup-file.sql.gz>" >&2
  echo "Example: $0 ./backups/moodfm-20260525-030001.sql.gz" >&2
}

# ---- 入参校验 ----
if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

BACKUP_FILE="$1"

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "[ERR] file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

if [[ "${BACKUP_FILE}" != *.sql.gz ]]; then
  echo "[ERR] expected a .sql.gz file, got: ${BACKUP_FILE}" >&2
  exit 1
fi

# ---- 路径 + .env ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERR] .env not found at ${ENV_FILE}" >&2
  exit 1
fi
# shellcheck disable=SC1090
set -a
source "${ENV_FILE}"
set +a

if [[ -z "${MYSQL_ROOT_PASSWORD:-}" ]]; then
  echo "[ERR] MYSQL_ROOT_PASSWORD is not set in ${ENV_FILE}" >&2
  exit 1
fi

CONTAINER="moodfm-mysql"
DB_NAME="moodfm"

# ---- 容器检查 ----
if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "[ERR] container '${CONTAINER}' is not running" >&2
  exit 1
fi

# ---- 二次确认 ----
echo "⚠️  这会覆盖当前 ${DB_NAME} 数据库的所有数据!"
echo "    备份文件: ${BACKUP_FILE}"
echo "    目标容器: ${CONTAINER}"
read -r -p "确认继续? [y/N]: " CONFIRM
CONFIRM="${CONFIRM:-N}"
if [[ ! "${CONFIRM}" =~ ^[yY]$ ]]; then
  echo "[ABORT] cancelled by user"
  exit 1
fi

# ---- 执行恢复 ----
set -o pipefail
if ! gunzip -c "${BACKUP_FILE}" \
     | docker exec -i -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" "${CONTAINER}" \
         mysql -uroot --default-character-set=utf8mb4 "${DB_NAME}"; then
  echo "[ERR] restore failed" >&2
  exit 1
fi

echo "[OK] restore complete"
