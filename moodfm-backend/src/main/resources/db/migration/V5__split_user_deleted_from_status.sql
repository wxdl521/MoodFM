-- 拆分 users.status 的双重语义：
--   status:  1=正常  0=封禁（管理员操作，可恢复）
--   deleted: 1=已注销（逻辑删除，@TableLogic）
ALTER TABLE users
  ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 1=已注销' AFTER status;

-- 迁移前 @TableLogic 挂在 status 上：status=0 的行（封禁或注销）都被全局过滤、对所有查询不可见。
-- 因此把全部 status=0 行统一迁为 deleted=1，保持其"不可见"语义不变
-- （不会让历史封禁用户重新可见——它们迁移前后都不可见）。
UPDATE users SET deleted = 1 WHERE status = 0;
