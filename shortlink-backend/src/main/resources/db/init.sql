CREATE DATABASE IF NOT EXISTS shortlink DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE shortlink;

CREATE TABLE IF NOT EXISTS t_short_link (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    short_code    VARCHAR(10)   NOT NULL            COMMENT '短码',
    original_url  VARCHAR(2048) NOT NULL            COMMENT '原始链接',
    url_hash      VARCHAR(64)   NOT NULL            COMMENT 'URL的SHA-256哈希，用于加速查询',
    domain        VARCHAR(255)  DEFAULT NULL        COMMENT '短链接域名',
    description   VARCHAR(500)  DEFAULT NULL        COMMENT '描述',
    expire_time   DATETIME      DEFAULT NULL        COMMENT '过期时间，NULL表示永久有效',
    status        TINYINT       DEFAULT 1           COMMENT '状态: 0-禁用, 1-启用',
    click_count   BIGINT        DEFAULT 0           COMMENT '点击次数（由定时任务从Redis同步）',
    created_time  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       DEFAULT 0           COMMENT '逻辑删除: 0-未删除, 1-已删除',
    UNIQUE  KEY uk_short_code   (short_code),
    INDEX       idx_url_hash    (url_hash),
    INDEX       idx_created_time(created_time),
    INDEX       idx_status      (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短链接表';

CREATE TABLE IF NOT EXISTS t_access_log (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    short_code    VARCHAR(10)   NOT NULL            COMMENT '短码',
    ip            VARCHAR(50)   DEFAULT NULL        COMMENT '访问IP',
    user_agent    VARCHAR(500)  DEFAULT NULL        COMMENT '用户代理',
    referer       VARCHAR(1024) DEFAULT NULL        COMMENT '来源页面',
    device_type   VARCHAR(50)   DEFAULT NULL        COMMENT '设备类型: PC/Mobile/Tablet',
    browser       VARCHAR(100)  DEFAULT NULL        COMMENT '浏览器',
    os            VARCHAR(100)  DEFAULT NULL        COMMENT '操作系统',
    country       VARCHAR(100)  DEFAULT NULL        COMMENT '国家',
    province      VARCHAR(100)  DEFAULT NULL        COMMENT '省份',
    city          VARCHAR(100)  DEFAULT NULL        COMMENT '城市',
    access_time   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
    INDEX       idx_short_code  (short_code),
    INDEX       idx_access_time (access_time),
    INDEX       idx_ip          (ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访问日志表';
