SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 欢乐谷游乐园 · 设施 / 演出 / 餐饮 / 会员年卡
-- 库名 amusement_park；application.yml 里 ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 注意：mysql 容器是 UTC。业务时间列一律存园区（Asia/Shanghai，UTC+08:00）挂钟值：
--   本文件里的 created_at 等用 UTC_TIMESTAMP() + INTERVAL 8 HOUR 表达；
--   park_show.show_at 直接写园区挂钟字面值（见该表种子数据注释），show_at_raw 保留原始字面量。

CREATE DATABASE IF NOT EXISTS amusement_park
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE amusement_park;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS ride_inspection;
DROP TABLE IF EXISTS season_pass;
DROP TABLE IF EXISTS ride;
DROP TABLE IF EXISTS park_show;
DROP TABLE IF EXISTS fnb;
DROP TABLE IF EXISTS member;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 会员 ----------------
CREATE TABLE member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    tier VARCHAR(16) NOT NULL,
    phone VARCHAR(24),
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 年卡 / 季卡（共享会员主键） ----------------
-- member_id 既是本表主键，也是指向 member.id 的外键（@PrimaryKeyJoinColumn）。
-- valid_from / valid_to 不允许 NULL；valid_from <= valid_to（含首尾的闭区间，
-- 乘坐核验当天落在起止之间即有效）。索引同时覆盖有效期范围查询。
CREATE TABLE season_pass (
    member_id BIGINT NOT NULL PRIMARY KEY,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    pass_type VARCHAR(16) NOT NULL,
    created_at DATETIME,
    renewed_at DATETIME,
    CONSTRAINT fk_season_pass_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT chk_pass_date_range CHECK (valid_from <= valid_to),
    INDEX idx_pass_valid_range (valid_from, valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 游乐设施 ----------------
CREATE TABLE ride (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_code VARCHAR(24) NOT NULL UNIQUE,
    ride_name VARCHAR(64) NOT NULL,
    min_height_cm INT,
    max_age INT,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 检修安全复核批次 ----------------
-- 设施进入检修时生成本次复核批次；批次只新增不覆盖：
--   填写中 → 待放行 → 已放行；待放行内容过期 → 已失效（终态，历史保留可追溯）。
-- 四项必检（制动/约束装置/紧急停机/空载试运行）：NULL=未检，1=合格，0=不合格。
-- snapshot_* 为提交时设施限制快照，放行时与当前值比对，不一致即视为复核内容过期。
CREATE TABLE ride_inspection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL,
    batch_no VARCHAR(48) NOT NULL UNIQUE,
    brake_ok TINYINT(1),
    restraint_ok TINYINT(1),
    emergency_stop_ok TINYINT(1),
    trial_run_ok TINYINT(1),
    remark VARCHAR(255),
    status VARCHAR(16) NOT NULL,
    snapshot_min_height_cm INT,
    snapshot_max_age INT,
    submitted_by VARCHAR(32),
    submitted_at DATETIME,
    released_by VARCHAR(32),
    released_at DATETIME,
    invalidated_at DATETIME,
    invalidate_reason VARCHAR(128),
    created_at DATETIME,
    CONSTRAINT fk_inspection_ride FOREIGN KEY (ride_id) REFERENCES ride (id),
    INDEX idx_inspection_ride_status (ride_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 园区演出（show 为保留字，表名用 park_show） ----------------
-- show_at 统一存「园区 Asia/Shanghai 挂钟值」（解释后的真实园区时刻）；
-- show_at_raw 保存建档/导入时的原始字面量（历史可追溯，可能带偏移量，也可能不带），
-- 早期数据没有原文时为 NULL，其 show_at 即按园区挂钟值理解。
CREATE TABLE park_show (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_code VARCHAR(24) NOT NULL UNIQUE,
    show_name VARCHAR(64) NOT NULL,
    venue VARCHAR(48),
    capacity INT NOT NULL,
    booked INT NOT NULL DEFAULT 0,
    show_at DATETIME,
    show_at_raw VARCHAR(40),
    created_at DATETIME,
    INDEX idx_show_at (show_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 餐饮网点 ----------------
CREATE TABLE fnb (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    outlet_code VARCHAR(24) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- =====================================================================

INSERT INTO member (id, name, tier, phone, created_at) VALUES
(1, '林晓彤', '金卡', '13800001111', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 90 DAY),
(2, '陈志远', '银卡', '13800002222', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 86 DAY),
(3, '王梓涵', '普通', '13800003333', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 80 DAY),
(4, '赵明轩', '普通', '13800004444', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 74 DAY),
(5, '孙佳怡', '金卡', '13800005555', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 68 DAY),
(6, '周浩然', '银卡', '13800006666', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
(7, '吴雨桐', '普通', '13800007777', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 54 DAY),
(8, '郑子墨', '金卡', '13800008888', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 48 DAY);

INSERT INTO season_pass (member_id, valid_from, valid_to, pass_type, created_at) VALUES
(1, '2026-01-01', '2026-12-31', '年卡', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 90 DAY),
(2, '2026-04-01', '2026-06-30', '季卡', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 86 DAY),
(3, '2026-03-15', '2027-03-14', '年卡', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 80 DAY),
(5, '2026-05-01', '2026-07-31', '季卡', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 68 DAY),
(7, '2026-02-01', '2027-01-31', '年卡', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 54 DAY);

INSERT INTO ride (id, ride_code, ride_name, min_height_cm, max_age, status, created_at) VALUES
(1, 'R-001', '极速过山车', 130, 55, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 95 DAY),
(2, 'R-002', '梦幻旋转木马', 95, 70, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 95 DAY),
(3, 'R-003', '高空跳楼机', 140, 50, '检修', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 95 DAY),
(4, 'R-004', '环园小火车', 90, 80, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 95 DAY),
(5, 'R-005', '激流勇进', 120, 60, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 95 DAY),
(6, 'R-006', '摩天轮', 100, 75, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 95 DAY),
(7, 'R-007', '海盗船', 110, 65, '关闭', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 95 DAY),
(8, 'R-008', '欢乐碰碰车', 100, 70, '开放', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 95 DAY);

-- 高空跳楼机（R-003，检修中）的复核批次历史：
--   0001 上次检修已放行；0002 提交后最低身高调整导致失效（历史保留可追溯）；0003 本次填写中。
INSERT INTO ride_inspection
(id, ride_id, batch_no, brake_ok, restraint_ok, emergency_stop_ok, trial_run_ok, remark, status,
 snapshot_min_height_cm, snapshot_max_age, submitted_by, submitted_at, released_by, released_at,
 invalidated_at, invalidate_reason, created_at) VALUES
(1, 3, 'INSP-R-003-0001', 1, 1, 1, 1, '季度定检，各项正常', '已放行',
 140, 50, '周立安', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 DAY,
 '王国栋', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 DAY + INTERVAL 2 HOUR,
 NULL, NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 41 DAY),
(2, 3, 'INSP-R-003-0002', 1, 1, 1, 1, NULL, '已失效',
 135, 50, '周立安', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY,
 NULL, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY + INTERVAL 5 HOUR,
 '设施乘坐限制已变更（最低身高/最高年龄），复核内容过期',
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 4 DAY),
(3, 3, 'INSP-R-003-0003', 1, 1, NULL, NULL, NULL, '填写中',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY);

-- 演出时刻口径：show_at 一律写园区（上海，UTC+08:00）挂钟时间。
--   * 早期/不带偏移量的历史值：直接写上海挂钟值，show_at_raw 留 NULL（或同文字面量）；
--   * 带偏移量的历史值：把绝对时刻换算到上海再写 show_at，原文保存在 show_at_raw 可追溯。
-- 覆盖：普通日期、午夜前后跨日（UTC 23:30 → 上海次日 07:30）、
--       同一绝对时刻两种写法（S-009 带偏移 / S-012 不带，园区时刻相同，按编号排序）、
--       闰日（2028-02-29）、夏令时地区客户端偏移（-07:00 → +15 小时）、以及未排期 NULL。
INSERT INTO park_show (id, show_code, show_name, venue, capacity, booked, show_at, show_at_raw, created_at) VALUES
(1, 'S-001', '花车大巡游', '中央广场', 500, 320, '2028-03-03 10:00:00', NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
(2, 'S-002', '奇幻魔术剧场', '梦幻剧场', 200, 200, '2028-03-03 14:30:00', '2028-03-03 14:30:00', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
(3, 'S-003', '水上特技秀', '湖畔舞台', 300, 150, '2028-03-04 11:00:00', NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
(4, 'S-004', '儿童音乐剧', '糖果剧场', 180, 60,  '2028-03-04 15:00:00', '2028-03-04T15:00:00', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
(5, 'S-005', '夜空光影秀', '夜空广场', 800, 500, '2028-03-05 19:30:00', NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
(6, 'S-006', '欢乐杂技表演', '中央广场', 400, 0,   '2028-03-05 20:00:00', NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
(7, 'S-007', '童话人偶巡游', '童话大街', 600, 280, '2028-03-06 09:30:00', NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
-- 闰日场次：2028 为闰年，02-29 必须准确归到当天
(8, 'S-008', '闰年特别巡游', '中央广场', 500, 120, '2028-02-29 10:30:00', '2028-02-29T10:30:00+08:00', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
-- 跨日边界：UTC 3/2 23:30 与上海 3/3 07:30 是同一绝对时刻，园区日期为 3 月 3 日
(9, 'S-009', '午夜星光巡游', '湖畔舞台', 260, 80,  '2028-03-03 07:30:00', '2028-03-02T23:30:00Z', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
-- 夏令时地区客户端：洛杉矶夏令时 -07:00 的 10:00 = 上海次日 01:00
(10, 'S-010', '西海岸联动直播秀', '未来馆', 120, 30, '2028-07-16 01:00:00', '2028-07-15T10:00:00-07:00', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
-- 未排期：无演出时刻，列表统一沉到末尾
(11, 'S-011', '待定特别活动', '中央广场', 300, 0, NULL, NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY),
-- 与 S-009 同一园区时刻的不带偏移量写法，用于验证两类历史值解释一致；相同时刻按编号 S-009 在前
(12, 'S-012', '晨光加演场', '糖果剧场', 180, 40, '2028-03-03 07:30:00', '2028-03-03 07:30:00', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 70 DAY);

INSERT INTO fnb (id, outlet_code, name, type, status, created_at) VALUES
(1, 'F-001', '园区汉堡屋', '快餐', '营业', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 82 DAY),
(2, 'F-002', '湖景正餐厅', '正餐', '营业', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 82 DAY),
(3, 'F-003', '鲜榨果汁时光', '饮品', '营业', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 82 DAY),
(4, 'F-004', '意式披萨工坊', '快餐', '暂停', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 82 DAY),
(5, 'F-005', '园区火锅食府', '正餐', '营业', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 82 DAY),
(6, 'F-006', '咖啡驿站', '饮品', '关闭', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 82 DAY);
