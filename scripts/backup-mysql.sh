#!/usr/bin/env bash
#
# backup-mysql.sh — MoodFM MySQL 全库备份脚本
#
# 用法:
#   ./scripts/backup-mysql.sh              # 备份到 ./backups/
#   BACKUP_DIR=/data/backups ./scripts/backup-mysql.sh
#
# 行为:
#   1. 从项目根目录的 .env 读 MYSQL_ROOT_PASSWORD
#   2. 在 moodfm-mysql 容器内跑 mysqldump (含 schema + 数据 + routines + triggers)
#   3. gzip 压缩输出到 BACKUP_DIR/moodfm-YYYYMMDD-HHMMSS.sql.gz
#   4. 删除 BACKUP_DIR 下 7 天以上的 moodfm-*.sql.gz
#
# 退出码: 失败任意一步均 exit 非零
#
set -euo pipefail

# ---- 路径解析 (脚本所在目录) ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# ---- 加载 .env ----
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

# ---- 配置 ----
CONTAINER="moodfm-mysql"
DB_NAME="moodfm"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS=7

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="${BACKUP_DIR}/moodfm-${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

# ---- 容器存在性检查 ----
if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "[ERR] container '${CONTAINER}' is not running" >&2
  exit 1
fi

# ---- 执行 mysqldump + gzip ----
# 注意: 通过 -e MYSQL_PWD 传密码, 避免 -p<pwd> 出现在容器内 ps 里
# pipefail 保证 mysqldump / gzip 任一失败都会让整条管道失败
set -o pipefail
if ! docker exec -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" "${CONTAINER}" \
      mysqldump -uroot \
        --single-transaction \
        --routines \
        --triggers \
        --default-character-set=utf8mb4 \
        "${DB_NAME}" \
      | gzip -c > "${OUT_FILE}"; then
  echo "[ERR] mysqldump or gzip failed" >&2
  rm -f "${OUT_FILE}"
  exit 1
fi

# ---- 校验文件存在且非空 ----
if [[ ! -s "${OUT_FILE}" ]]; then
  echo "[ERR] backup file missing or empty: ${OUT_FILE}" >&2
  rm -f "${OUT_FILE}"
  exit 1
fi

SIZE="$(du -h "${OUT_FILE}" | awk '{print $1}')"
echo "[OK] backup saved: ${OUT_FILE} (size: ${SIZE})"

# ---- 清理 7 天以上旧备份 ----
# -mtime +7 = 修改时间在 7*24h 之前
find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'moodfm-*.sql.gz' -mtime +${RETENTION_DAYS} -print -delete \
  | sed 's/^/[CLEAN] removed: /' || true
