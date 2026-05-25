# MoodFM 备份脚本

一句话：每天 `mysqldump` 全库 + gzip 压缩，保留最近 7 天本地副本，配套一个交互式恢复脚本。

## 手动备份

```bash
cd /path/to/MoodFM
chmod +x scripts/backup-mysql.sh scripts/restore-mysql.sh   # 仅首次需要
./scripts/backup-mysql.sh
```

成功后会在 `./backups/` 下生成形如 `moodfm-20260525-030001.sql.gz` 的文件，并自动删除 7 天以上的旧备份。

如果想换备份目录：

```bash
BACKUP_DIR=/data/moodfm-backups ./scripts/backup-mysql.sh
```

## 自动备份 (crontab)

每天凌晨 3 点跑，日志追加到 `/var/log/moodfm-backup.log`。`crontab -e` 后粘贴：

```cron
0 3 * * * cd /path/to/MoodFM && ./scripts/backup-mysql.sh >> /var/log/moodfm-backup.log 2>&1
```

把 `/path/to/MoodFM` 改成你服务器上项目的实际绝对路径。第一次部署后建议先用 `./scripts/backup-mysql.sh` 手动跑一次，确认输出正常再交给 cron。

> 提示：`/var/log/moodfm-backup.log` 需要 cron 运行用户有写权限；若是非 root 用户跑，可改成 `~/moodfm-backup.log`。

## 恢复

```bash
./scripts/restore-mysql.sh ./backups/moodfm-20260525-030001.sql.gz
```

脚本会让你输入 `y` 二次确认。**会完全覆盖当前 `moodfm` 库的所有数据**，慎用。生产环境恢复前建议先停掉 backend 容器，避免恢复过程中有写入：

```bash
docker compose stop backend
./scripts/restore-mysql.sh <file>
docker compose start backend
```

## 验证备份能用 (建议每月一次)

备份只有能恢复才算数。建议每月做一次「真演练」：

1. 跑一份新备份：`./scripts/backup-mysql.sh`
2. 在另一台机器（或本地另开一个 MySQL 容器，库名换成 `moodfm_verify`）上恢复这份备份
3. 校验关键表行数，例如：

   ```bash
   docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" <verify-container> \
     mysql -uroot -e 'SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM songs;' moodfm_verify
   ```

只要行数和线上接近、查询不报错，就说明这份备份是真能用的。

## 不做什么 (边界)

为了保持简单，本方案**只做**本地每日全量备份。以下能力暂不提供，将来如有需要再加：

- **异地灾备**：不会把备份推到 S3 / 阿里云 OSS / 七牛等对象存储。若服务器整机挂了/被删，本地备份也会一起没。后续可加一行 `aliyun oss cp` 之类的命令到 cron 里。
- **PITR (Point-in-time recovery)**：不做 binlog 增量备份。最多只能恢复到最近一次 `mysqldump` 的时间点，期间的数据会丢。需要分钟级 RPO 时考虑开启 binlog + 定期归档，或上 Percona XtraBackup。
- **加密**：备份文件未加密。如果备份目录权限不够严格，等于明文数据库泄露。介意时可在 cron 里追加 `gpg --symmetric` 或挂载加密盘。
