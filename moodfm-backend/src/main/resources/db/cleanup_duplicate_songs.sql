-- ============================================================================
-- cleanup_duplicate_songs.sql
--
-- 一次性清理脚本：把同一 (platform, platform_song_id) 对应的多条 songs 行
-- 合并到"最早的" song_id（按 created_at 升序，id 升序兜底）。
--
-- 背景：
--   PlayerServiceImpl.persistSongs() 历史去重策略是 (title, artist) 文本匹配，
--   导致同一首外部曲目（同 platform + 同 platform_song_id）因为标题/艺人字符
--   差异（空格、版本号、繁简体）被当作不同 song 行写进 songs 表。
--   现在 persistSongs() 已改为按 (platform, platform_song_id) 主键去重，
--   但历史脏数据需要这个脚本来清理。
--
-- 涉及表（即所有引用 songs.id 的外键来源）：
--   1. platform_song_mapping.song_id  -- 重复 mapping 直接 DELETE 掉非 canonical 的
--   2. play_records.song_id           -- UPDATE 迁移到 canonical_song_id
--   3. feedback_events.song_id        -- UPDATE 迁移到 canonical_song_id
--   最后 DELETE 掉孤立的非 canonical songs 行。
--
-- 运行前必须先备份以下表：
--   songs, platform_song_mapping, play_records, feedback_events
-- 例如：
--   mysqldump -u <user> -p <database> \
--       songs platform_song_mapping play_records feedback_events \
--       > backup_before_cleanup_$(date +%Y%m%d_%H%M%S).sql
--
-- 用法：
--   1. 第一遍：mysql -u <user> -p <database> < cleanup_duplicate_songs.sql
--      （只跑 PREVIEW + POST-CHECK，POST-CHECK 此时应仍报有重复 —— 正常）
--   2. 检查 PREVIEW 输出合理后，把下面 "EXECUTE" 段的 /* ... */ 注释去掉，
--      再跑一次：mysql -u <user> -p <database> < cleanup_duplicate_songs.sql
--   3. 第二遍跑完后，POST-CHECK 应该返回 0 行。
-- ============================================================================

-- ─── PREVIEW: 看看会动哪些 songs（默认启用，纯只读） ──────────────────────

SELECT '=== 1. 重复 (platform, platform_song_id) 组 ===' AS info;
SELECT
    psm.platform,
    psm.platform_song_id,
    COUNT(DISTINCT psm.song_id)                  AS song_count,
    GROUP_CONCAT(psm.song_id ORDER BY psm.song_id) AS song_ids,
    MIN(psm.song_id)                             AS min_song_id_simple_tiebreak
FROM platform_song_mapping psm
GROUP BY psm.platform, psm.platform_song_id
HAVING song_count > 1;

SELECT '=== 2. 待合并的 songs（old_song_id -> canonical_song_id 映射） ===' AS info;
-- canonical = 同一 (platform, platform_song_id) 组里 songs.created_at 最早的那条；
-- created_at 相同则用 songs.id 较小者兜底。
SELECT
    psm.song_id          AS old_song_id,
    s_old.title          AS old_title,
    s_old.artist         AS old_artist,
    s_old.created_at     AS old_created_at,
    canonical.song_id    AS new_song_id,
    s_new.title          AS new_title,
    s_new.artist         AS new_artist,
    s_new.created_at     AS new_created_at,
    psm.platform,
    psm.platform_song_id
FROM platform_song_mapping psm
JOIN songs s_old ON s_old.id = psm.song_id
JOIN (
    -- 每个重复组里选 canonical：先按 created_at 升序，再按 id 升序
    SELECT
        psm2.platform,
        psm2.platform_song_id,
        (
            SELECT psm3.song_id
            FROM platform_song_mapping psm3
            JOIN songs s3 ON s3.id = psm3.song_id
            WHERE psm3.platform = psm2.platform
              AND psm3.platform_song_id = psm2.platform_song_id
            ORDER BY s3.created_at ASC, psm3.song_id ASC
            LIMIT 1
        ) AS song_id
    FROM platform_song_mapping psm2
    GROUP BY psm2.platform, psm2.platform_song_id
    HAVING COUNT(*) > 1
) canonical
  ON canonical.platform         = psm.platform
 AND canonical.platform_song_id = psm.platform_song_id
JOIN songs s_new ON s_new.id = canonical.song_id
WHERE psm.song_id <> canonical.song_id
ORDER BY psm.platform, psm.platform_song_id, psm.song_id;

SELECT '=== 3. 每张表预计被迁移的行数 ===' AS info;
-- 用同样的 canonical 选法构造一个 in-line 映射，统计各表里 old_song_id 出现次数
SELECT 'play_records' AS table_name, COUNT(*) AS rows_to_migrate
FROM play_records pr
WHERE pr.song_id IN (
    SELECT psm.song_id
    FROM platform_song_mapping psm
    JOIN (
        SELECT
            psm2.platform,
            psm2.platform_song_id,
            (
                SELECT psm3.song_id
                FROM platform_song_mapping psm3
                JOIN songs s3 ON s3.id = psm3.song_id
                WHERE psm3.platform = psm2.platform
                  AND psm3.platform_song_id = psm2.platform_song_id
                ORDER BY s3.created_at ASC, psm3.song_id ASC
                LIMIT 1
            ) AS canonical_song_id
        FROM platform_song_mapping psm2
        GROUP BY psm2.platform, psm2.platform_song_id
        HAVING COUNT(*) > 1
    ) c
      ON c.platform         = psm.platform
     AND c.platform_song_id = psm.platform_song_id
    WHERE psm.song_id <> c.canonical_song_id
)
UNION ALL
SELECT 'feedback_events', COUNT(*)
FROM feedback_events fe
WHERE fe.song_id IN (
    SELECT psm.song_id
    FROM platform_song_mapping psm
    JOIN (
        SELECT
            psm2.platform,
            psm2.platform_song_id,
            (
                SELECT psm3.song_id
                FROM platform_song_mapping psm3
                JOIN songs s3 ON s3.id = psm3.song_id
                WHERE psm3.platform = psm2.platform
                  AND psm3.platform_song_id = psm2.platform_song_id
                ORDER BY s3.created_at ASC, psm3.song_id ASC
                LIMIT 1
            ) AS canonical_song_id
        FROM platform_song_mapping psm2
        GROUP BY psm2.platform, psm2.platform_song_id
        HAVING COUNT(*) > 1
    ) c
      ON c.platform         = psm.platform
     AND c.platform_song_id = psm.platform_song_id
    WHERE psm.song_id <> c.canonical_song_id
)
UNION ALL
SELECT 'platform_song_mapping (会被 DELETE)', COUNT(*)
FROM platform_song_mapping psm
JOIN (
    SELECT
        psm2.platform,
        psm2.platform_song_id,
        (
            SELECT psm3.song_id
            FROM platform_song_mapping psm3
            JOIN songs s3 ON s3.id = psm3.song_id
            WHERE psm3.platform = psm2.platform
              AND psm3.platform_song_id = psm2.platform_song_id
            ORDER BY s3.created_at ASC, psm3.song_id ASC
            LIMIT 1
        ) AS canonical_song_id
    FROM platform_song_mapping psm2
    GROUP BY psm2.platform, psm2.platform_song_id
    HAVING COUNT(*) > 1
) c
  ON c.platform         = psm.platform
 AND c.platform_song_id = psm.platform_song_id
WHERE psm.song_id <> c.canonical_song_id
UNION ALL
SELECT 'songs (会被 DELETE)', COUNT(DISTINCT psm.song_id)
FROM platform_song_mapping psm
JOIN (
    SELECT
        psm2.platform,
        psm2.platform_song_id,
        (
            SELECT psm3.song_id
            FROM platform_song_mapping psm3
            JOIN songs s3 ON s3.id = psm3.song_id
            WHERE psm3.platform = psm2.platform
              AND psm3.platform_song_id = psm2.platform_song_id
            ORDER BY s3.created_at ASC, psm3.song_id ASC
            LIMIT 1
        ) AS canonical_song_id
    FROM platform_song_mapping psm2
    GROUP BY psm2.platform, psm2.platform_song_id
    HAVING COUNT(*) > 1
) c
  ON c.platform         = psm.platform
 AND c.platform_song_id = psm.platform_song_id
WHERE psm.song_id <> c.canonical_song_id;

-- ─── EXECUTE: 真正执行迁移 + 删除（默认整段被注释掉） ─────────────────────
-- 确认 PREVIEW 输出无误后，把下面这一整块的 /* 和 */ 删掉，再跑一遍本文件。
/*
START TRANSACTION;

-- 临时表存 "非 canonical song_id → canonical song_id" 映射
CREATE TEMPORARY TABLE _song_merge_map (
    old_song_id BIGINT NOT NULL PRIMARY KEY,
    new_song_id BIGINT NOT NULL,
    INDEX idx_new (new_song_id)
);

INSERT INTO _song_merge_map (old_song_id, new_song_id)
SELECT psm.song_id, canonical.song_id
FROM platform_song_mapping psm
JOIN (
    -- 同 PREVIEW 第 2 段的 canonical 选法：created_at 升序 + id 升序兜底
    SELECT
        psm2.platform,
        psm2.platform_song_id,
        (
            SELECT psm3.song_id
            FROM platform_song_mapping psm3
            JOIN songs s3 ON s3.id = psm3.song_id
            WHERE psm3.platform = psm2.platform
              AND psm3.platform_song_id = psm2.platform_song_id
            ORDER BY s3.created_at ASC, psm3.song_id ASC
            LIMIT 1
        ) AS song_id
    FROM platform_song_mapping psm2
    GROUP BY psm2.platform, psm2.platform_song_id
    HAVING COUNT(*) > 1
) canonical
  ON canonical.platform         = psm.platform
 AND canonical.platform_song_id = psm.platform_song_id
WHERE psm.song_id <> canonical.song_id;

-- 1) 迁移每张引用 songs.id 的业务表 ----------------------------------------

-- play_records.song_id  → canonical
UPDATE play_records pr
  JOIN _song_merge_map m ON pr.song_id = m.old_song_id
   SET pr.song_id = m.new_song_id;

-- feedback_events.song_id  → canonical
UPDATE feedback_events fe
  JOIN _song_merge_map m ON fe.song_id = m.old_song_id
   SET fe.song_id = m.new_song_id;

-- platform_song_mapping 是冲突源头本身：canonical 那行已存在了，
-- 非 canonical 的 mapping 行直接 DELETE，不能 UPDATE（会违反 uk_platform_song）
DELETE psm FROM platform_song_mapping psm
  JOIN _song_merge_map m ON psm.song_id = m.old_song_id;

-- 2) 删掉孤立的非 canonical songs 行 --------------------------------------
-- 走到这一步，old_song_id 已经在所有业务表里"消失"了，可以安全删
DELETE s FROM songs s
  JOIN _song_merge_map m ON s.id = m.old_song_id;

DROP TEMPORARY TABLE _song_merge_map;

COMMIT;
*/

-- ─── POST-CHECK: 跑完 EXECUTE 段后再跑一遍本文件，下面应该返回 0 行 ──────
SELECT '=== POST-CHECK: 应为空（无任何输出行） ===' AS info;
SELECT platform, platform_song_id, COUNT(*) AS cnt
FROM platform_song_mapping
GROUP BY platform, platform_song_id
HAVING cnt > 1;
