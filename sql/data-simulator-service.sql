-- 独立模拟源模块建表脚本，适用于金仓的PostgreSQL兼容模式。
-- 新库初始化及已有模拟源表升级统一执行本文件，不修改旧模块数据表。
-- 字段取值与长度规则由后端校验，本文件仅保留非空、主键、外键和必要索引。
CREATE TABLE IF NOT EXISTS simulator_source_config (
    id BIGSERIAL PRIMARY KEY,
    source_name VARCHAR(100) NOT NULL,
    transfer_protocol SMALLINT NOT NULL,
    transfer_type SMALLINT NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    target_host VARCHAR(100) NOT NULL,
    target_port INT NOT NULL,
    send_interval_millis BIGINT NOT NULL,
    loop_enabled SMALLINT NOT NULL,
    remark VARCHAR(500),
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE simulator_source_config IS '模拟源配置';
COMMENT ON COLUMN simulator_source_config.id IS '主键';
COMMENT ON COLUMN simulator_source_config.source_name IS '模拟源名称';
COMMENT ON COLUMN simulator_source_config.transfer_protocol IS '传输协议：2为PDXP，3为FEP';
COMMENT ON COLUMN simulator_source_config.transfer_type IS '传输方式：1为UDP，2为TCP';
COMMENT ON COLUMN simulator_source_config.file_path IS '服务器文件路径';
COMMENT ON COLUMN simulator_source_config.target_host IS '目标地址';
COMMENT ON COLUMN simulator_source_config.target_port IS '目标端口';
COMMENT ON COLUMN simulator_source_config.send_interval_millis IS '发送间隔毫秒';
COMMENT ON COLUMN simulator_source_config.loop_enabled IS '是否循环：0否1是';
COMMENT ON COLUMN simulator_source_config.remark IS '备注';
COMMENT ON COLUMN simulator_source_config.is_deleted IS '是否删除：0否1是';
COMMENT ON COLUMN simulator_source_config.create_time IS '创建时间';
COMMENT ON COLUMN simulator_source_config.update_time IS '更新时间';

CREATE TABLE IF NOT EXISTS simulator_pdxp_config (
    source_id BIGINT PRIMARY KEY REFERENCES simulator_source_config(id),
    input_frame_length INT NOT NULL DEFAULT 512,
    transport_header_length INT NOT NULL DEFAULT 2,
    telemetry_header_length INT NOT NULL DEFAULT 4,
    custom_data_length INT NOT NULL DEFAULT 5,
    transport_header TEXT NOT NULL,
    custom_data TEXT NOT NULL,
    ver VARCHAR(2) NOT NULL,
    mid VARCHAR(4) NOT NULL,
    sid VARCHAR(8) NOT NULL,
    did VARCHAR(8) NOT NULL,
    bid VARCHAR(8) NOT NULL,
    initial_no VARCHAR(8) NOT NULL,
    flag VARCHAR(2) NOT NULL
);
-- 已有初版表先补充字段，再执行字段注释；新库也可重复执行。
ALTER TABLE simulator_pdxp_config ADD COLUMN IF NOT EXISTS input_frame_length INT NOT NULL DEFAULT 512;
ALTER TABLE simulator_pdxp_config ADD COLUMN IF NOT EXISTS transport_header_length INT NOT NULL DEFAULT 2;
ALTER TABLE simulator_pdxp_config ADD COLUMN IF NOT EXISTS telemetry_header_length INT NOT NULL DEFAULT 4;
ALTER TABLE simulator_pdxp_config ADD COLUMN IF NOT EXISTS custom_data_length INT NOT NULL DEFAULT 5;
ALTER TABLE simulator_pdxp_config ALTER COLUMN transport_header TYPE TEXT;
ALTER TABLE simulator_pdxp_config ALTER COLUMN custom_data TYPE TEXT;
COMMENT ON TABLE simulator_pdxp_config IS 'PDXP专用参数';
COMMENT ON COLUMN simulator_pdxp_config.source_id IS '所属模拟源';
COMMENT ON COLUMN simulator_pdxp_config.input_frame_length IS '每次从文件读取的帧长度，默认512字节';
COMMENT ON COLUMN simulator_pdxp_config.transport_header_length IS '传输头长度，默认2字节，零表示省略';
COMMENT ON COLUMN simulator_pdxp_config.telemetry_header_length IS '原帧开头移动到末尾的长度，默认4字节';
COMMENT ON COLUMN simulator_pdxp_config.custom_data_length IS '自定义数据长度，默认5字节，零表示省略';
COMMENT ON COLUMN simulator_pdxp_config.transport_header IS '十六进制协议字段';
COMMENT ON COLUMN simulator_pdxp_config.custom_data IS '十六进制协议字段';
COMMENT ON COLUMN simulator_pdxp_config.ver IS '十六进制协议字段';
COMMENT ON COLUMN simulator_pdxp_config.mid IS '十六进制协议字段';
COMMENT ON COLUMN simulator_pdxp_config.sid IS '十六进制协议字段';
COMMENT ON COLUMN simulator_pdxp_config.did IS '十六进制协议字段';
COMMENT ON COLUMN simulator_pdxp_config.bid IS '十六进制协议字段';
COMMENT ON COLUMN simulator_pdxp_config.initial_no IS '十六进制包序号初值';
COMMENT ON COLUMN simulator_pdxp_config.flag IS '十六进制协议字段';

CREATE TABLE IF NOT EXISTS simulator_run_record (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES simulator_source_config(id),
    run_status SMALLINT NOT NULL,
    config_snapshot JSONB NOT NULL,
    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    sent_unit_count BIGINT NOT NULL DEFAULT 0,
    sent_byte_count BIGINT NOT NULL DEFAULT 0,
    completed_loop_count BIGINT NOT NULL DEFAULT 0,
    last_send_time TIMESTAMP,
    error_message VARCHAR(2000),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE simulator_run_record IS '模拟源运行记录';
COMMENT ON COLUMN simulator_run_record.id IS '运行主键';
COMMENT ON COLUMN simulator_run_record.source_id IS '所属模拟源';
COMMENT ON COLUMN simulator_run_record.run_status IS '运行状态：1启动中2运行中3停止中4已停止5已完成6异常';
COMMENT ON COLUMN simulator_run_record.config_snapshot IS '实际执行配置快照';
COMMENT ON COLUMN simulator_run_record.start_time IS '启动时间';
COMMENT ON COLUMN simulator_run_record.end_time IS '结束时间';
COMMENT ON COLUMN simulator_run_record.sent_unit_count IS '已发送数据单元数量';
COMMENT ON COLUMN simulator_run_record.sent_byte_count IS '已发送应用层协议字节数';
COMMENT ON COLUMN simulator_run_record.completed_loop_count IS '完整文件交换轮数';
COMMENT ON COLUMN simulator_run_record.last_send_time IS '最近发送时间';
COMMENT ON COLUMN simulator_run_record.error_message IS '异常原因';
COMMENT ON COLUMN simulator_run_record.create_time IS '创建时间';
COMMENT ON COLUMN simulator_run_record.update_time IS '更新时间';

-- 业务取值、协议组合和长度校验统一由后端执行；此段可重复执行。
-- 只清理当前连接默认模式下这三张模拟源表的CHECK，保留主键、外键和唯一索引。
BEGIN;

-- 第一步：通过系统目录识别旧CHECK，兼容历史数据库的不同约束命名。
DO $$
DECLARE
    check_record RECORD;
BEGIN
    FOR check_record IN
        SELECT namespace_config.nspname AS schema_name,
               table_config.relname AS table_name,
               constraint_config.conname AS constraint_name
        FROM pg_constraint constraint_config
        JOIN pg_class table_config
          ON table_config.oid = constraint_config.conrelid
        JOIN pg_namespace namespace_config
          ON namespace_config.oid = table_config.relnamespace
        WHERE constraint_config.contype = 'c'
          AND constraint_config.conrelid IN (
              to_regclass('simulator_source_config'),
              to_regclass('simulator_pdxp_config'),
              to_regclass('simulator_run_record')
          )
    LOOP
        EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I',
                       check_record.schema_name,
                       check_record.table_name,
                       check_record.constraint_name);
    END LOOP;
END;
$$;

-- 第二步：将历史FEP配置编码从4迁移为3，不修改运行历史中的原始快照。
UPDATE simulator_source_config
SET transfer_protocol = 3,
    update_time = CURRENT_TIMESTAMP
WHERE transfer_protocol = 4;

-- 第三步：所有约束清理和数据迁移成功后统一提交，失败时整体回滚。
COMMIT;

-- 有效名称唯一，删除后可重新使用名称。
CREATE UNIQUE INDEX IF NOT EXISTS uk_simulator_source_name ON simulator_source_config(source_name) WHERE is_deleted = 0;
-- 防止重复启动同一模拟源。
CREATE UNIQUE INDEX IF NOT EXISTS uk_simulator_active_run ON simulator_run_record(source_id) WHERE run_status IN (1,2,3);
-- 支持每个模拟源按时间查询历史记录。
CREATE INDEX IF NOT EXISTS idx_simulator_run_history ON simulator_run_record(source_id, id DESC);

