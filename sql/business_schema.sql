-- 数据处理业务表（草案版）
-- 说明：task_id 表示调度系统下发的外部试验任务编号，关联 experiment_task.task_id。
-- 当前不添加外键约束。

BEGIN;

-- 试验任务表
CREATE TABLE IF NOT EXISTS experiment_task (
    task_id VARCHAR(100) PRIMARY KEY,
    requirement_id VARCHAR(100),
    task_name VARCHAR(100) NOT NULL,
    task_priority INT,
    plan_id VARCHAR(100),
    plan_code VARCHAR(100),
    flow_instance_id VARCHAR(100),
    execution_attempt INT NOT NULL DEFAULT 1,
    task_status SMALLINT NOT NULL DEFAULT 1,
    end_reason VARCHAR(20),
    event_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE experiment_task IS '试验任务表';
COMMENT ON COLUMN experiment_task.task_id IS '调度系统下发的外部试验任务编号';
COMMENT ON COLUMN experiment_task.requirement_id IS '关联需求业务编号';
COMMENT ON COLUMN experiment_task.task_name IS '试验任务名称';
COMMENT ON COLUMN experiment_task.task_priority IS '任务优先级，取值1至9';
COMMENT ON COLUMN experiment_task.plan_id IS '试验方案内部编号';
COMMENT ON COLUMN experiment_task.plan_code IS '试验方案业务编码';
COMMENT ON COLUMN experiment_task.flow_instance_id IS '流程实例内部编号';
COMMENT ON COLUMN experiment_task.execution_attempt IS '任务执行轮次';
COMMENT ON COLUMN experiment_task.task_status IS '任务状态：1运行中，2已完成，3已取消';
COMMENT ON COLUMN experiment_task.end_reason IS '结束原因：COMPLETED自然完成，CANCELED人工停止';
COMMENT ON COLUMN experiment_task.event_time IS '最近一次生命周期事件发生时间';
COMMENT ON COLUMN experiment_task.create_time IS '记录创建时间';
COMMENT ON COLUMN experiment_task.update_time IS '记录更新时间';

-- 删除已经由五秒统计表替代的旧总览快照和分钟曲线表。
DROP TABLE IF EXISTS dashboard_collection_snapshot;
DROP TABLE IF EXISTS dashboard_processing_snapshot;
DROP TABLE IF EXISTS dashboard_collection_curve_point;
DROP TABLE IF EXISTS dashboard_processing_curve_point;

-- 采集接口五秒统计表，PDXP按完整帧、FEP按完整文件统计采集量。
CREATE TABLE IF NOT EXISTS collect_interface_statistics (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    interface_id BIGINT NOT NULL,
    statistics_time TIMESTAMP NOT NULL,
    collection_count BIGINT NOT NULL DEFAULT 0,
    interface_rate NUMERIC(20, 4) NOT NULL DEFAULT 0,
    is_deleted INTEGER NOT NULL DEFAULT 0
);

COMMENT ON TABLE collect_interface_statistics IS '采集接口五秒统计表';
COMMENT ON COLUMN collect_interface_statistics.id IS '统计记录主键ID';
COMMENT ON COLUMN collect_interface_statistics.task_id IS '外部试验任务编号';
COMMENT ON COLUMN collect_interface_statistics.interface_id IS '采集接口ID';
COMMENT ON COLUMN collect_interface_statistics.statistics_time IS '五秒统计窗口开始时间';
COMMENT ON COLUMN collect_interface_statistics.collection_count IS '窗口内采集量，PDXP为帧数，FEP为文件数';
COMMENT ON COLUMN collect_interface_statistics.interface_rate IS '窗口内平均接口速率，单位B/s';
COMMENT ON COLUMN collect_interface_statistics.is_deleted IS '是否删除：0否，1是';
CREATE UNIQUE INDEX IF NOT EXISTS uk_collect_interface_statistics_window
    ON collect_interface_statistics (task_id, interface_id, statistics_time);
CREATE INDEX IF NOT EXISTS idx_collect_interface_statistics_task_time
    ON collect_interface_statistics (task_id, statistics_time DESC);

-- 数据处理五秒统计表，去重数和野值数均按遥测参数数量统计。
CREATE TABLE IF NOT EXISTS data_processing_statistics (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    interface_id BIGINT NOT NULL,
    statistics_time TIMESTAMP NOT NULL,
    processing_count BIGINT NOT NULL DEFAULT 0,
    duplicate_count BIGINT NOT NULL DEFAULT 0,
    wild_value_count BIGINT NOT NULL DEFAULT 0,
    is_deleted INTEGER NOT NULL DEFAULT 0
);

COMMENT ON TABLE data_processing_statistics IS '数据处理五秒统计表';
COMMENT ON COLUMN data_processing_statistics.id IS '统计记录主键ID';
COMMENT ON COLUMN data_processing_statistics.task_id IS '外部试验任务编号';
COMMENT ON COLUMN data_processing_statistics.interface_id IS '采集接口ID';
COMMENT ON COLUMN data_processing_statistics.statistics_time IS '五秒统计窗口开始时间';
COMMENT ON COLUMN data_processing_statistics.processing_count IS '窗口内成功处理的帧或文件数量';
COMMENT ON COLUMN data_processing_statistics.duplicate_count IS '窗口内被后值覆盖的重复遥测参数数量';
COMMENT ON COLUMN data_processing_statistics.wild_value_count IS '窗口内判定为野值的遥测参数数量';
COMMENT ON COLUMN data_processing_statistics.is_deleted IS '是否删除：0否，1是';
CREATE UNIQUE INDEX IF NOT EXISTS uk_data_processing_statistics_window
    ON data_processing_statistics (task_id, interface_id, statistics_time);
CREATE INDEX IF NOT EXISTS idx_data_processing_statistics_task_time
    ON data_processing_statistics (task_id, statistics_time DESC);

-- 数据处理任务表
CREATE TABLE IF NOT EXISTS data_process_task (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100),
    task_name VARCHAR(100) NOT NULL,
    source_id BIGINT,
    processed_count INT DEFAULT 0,
    task_status INT DEFAULT 0,
    is_deleted INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE data_process_task IS '数据处理任务表';
COMMENT ON COLUMN data_process_task.id IS '自增主键';
COMMENT ON COLUMN data_process_task.task_id IS '外部试验任务编号';
COMMENT ON COLUMN data_process_task.task_name IS '任务名称';
COMMENT ON COLUMN data_process_task.source_id IS '采集接口ID，对应collect_interface_config.id';
COMMENT ON COLUMN data_process_task.processed_count IS '已清洗数量';
COMMENT ON COLUMN data_process_task.task_status IS '任务状态：0等待中 1运行中 2已完成';
COMMENT ON COLUMN data_process_task.is_deleted IS '是否删除：0否 1是';
COMMENT ON COLUMN data_process_task.create_time IS '创建时间';
COMMENT ON COLUMN data_process_task.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_data_process_task_task_id ON data_process_task (task_id);

-- 数据处理页面右侧“处理规则配置”，每个试验任务保存一组开关。
CREATE TABLE IF NOT EXISTS processing_rule_config (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL UNIQUE,
    dedup_enabled SMALLINT NOT NULL DEFAULT 1,
    abnormal_frame_filter_enabled SMALLINT NOT NULL DEFAULT 1,
    delayed_data_compensation_enabled SMALLINT NOT NULL DEFAULT 0,
    parameter_range_check_enabled SMALLINT NOT NULL DEFAULT 1,
    compression_archive_enabled SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE processing_rule_config IS '任务级数据处理规则开关配置';
COMMENT ON COLUMN processing_rule_config.id IS '规则配置主键ID';
COMMENT ON COLUMN processing_rule_config.task_id IS '外部试验任务编号，每个任务一条配置';
COMMENT ON COLUMN processing_rule_config.dedup_enabled IS '数据去重规则：0关闭 1开启';
COMMENT ON COLUMN processing_rule_config.abnormal_frame_filter_enabled IS '异常帧过滤：0关闭 1开启';
COMMENT ON COLUMN processing_rule_config.delayed_data_compensation_enabled IS '延时数据补偿：0关闭 1开启';
COMMENT ON COLUMN processing_rule_config.parameter_range_check_enabled IS '参数值范围检查：0关闭 1开启';
COMMENT ON COLUMN processing_rule_config.compression_archive_enabled IS '数据压缩归档：0关闭 1开启';
COMMENT ON COLUMN processing_rule_config.create_time IS '创建时间';
COMMENT ON COLUMN processing_rule_config.update_time IS '更新时间';

-- 数据监测告警事件表
CREATE TABLE IF NOT EXISTS dm_alarm_event (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100),
    alarm_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_name VARCHAR(100) NOT NULL,
    alarm_msg VARCHAR(500) NOT NULL,
    alarm_level INT NOT NULL DEFAULT 2,
    alarm_status INT NOT NULL DEFAULT 0,
    task_name VARCHAR(100),
    channel_code VARCHAR(100),
    trigger_rule VARCHAR(200),
    is_deleted INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(100),
    interface_id BIGINT,
    process_task_id BIGINT,
    calib_channel_id BIGINT,
    tm_symbol VARCHAR(200),
    parameter_name VARCHAR(200),
    alarm_detail TEXT,
    trigger_count INT NOT NULL DEFAULT 1,
    last_trigger_time TIMESTAMP,
    recover_time TIMESTAMP,
    dedup_key VARCHAR(100)
);

COMMENT ON TABLE dm_alarm_event IS '数据监测告警事件表';
COMMENT ON COLUMN dm_alarm_event.id IS '主键ID';
COMMENT ON COLUMN dm_alarm_event.task_id IS '外部试验任务编号';
COMMENT ON COLUMN dm_alarm_event.alarm_time IS '告警时间';
COMMENT ON COLUMN dm_alarm_event.source_name IS '告警来源英文编码：COLLECTION数据采集、PROCESSING数据处理、CALIBRATION数据校准';
COMMENT ON COLUMN dm_alarm_event.alarm_msg IS '告警信息';
COMMENT ON COLUMN dm_alarm_event.alarm_level IS '2一般、4严重';
COMMENT ON COLUMN dm_alarm_event.alarm_status IS '处理状态：0未处理 1已处理';
COMMENT ON COLUMN dm_alarm_event.task_name IS '任务名称（历史快照）';
COMMENT ON COLUMN dm_alarm_event.channel_code IS '通道编码';
COMMENT ON COLUMN dm_alarm_event.trigger_rule IS '触发规则';
COMMENT ON COLUMN dm_alarm_event.is_deleted IS '是否删除：0否 1是';
COMMENT ON COLUMN dm_alarm_event.create_time IS '创建时间';
COMMENT ON COLUMN dm_alarm_event.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_dm_alarm_event_task_id ON dm_alarm_event (task_id);

-- 系统告警事件表

COMMENT ON COLUMN dm_alarm_event.event_type IS '自动告警事件类型编码';
COMMENT ON COLUMN dm_alarm_event.interface_id IS '采集接口ID';
COMMENT ON COLUMN dm_alarm_event.process_task_id IS '处理任务ID，无法确定时为空';
COMMENT ON COLUMN dm_alarm_event.calib_channel_id IS '校准通道ID';
COMMENT ON COLUMN dm_alarm_event.tm_symbol IS '遥测参数代号';
COMMENT ON COLUMN dm_alarm_event.parameter_name IS '参数名称快照';
COMMENT ON COLUMN dm_alarm_event.alarm_detail IS '完整中文告警详情';
COMMENT ON COLUMN dm_alarm_event.trigger_count IS '累计触发次数';
COMMENT ON COLUMN dm_alarm_event.last_trigger_time IS '最近触发时间';
COMMENT ON COLUMN dm_alarm_event.recover_time IS '自动恢复时间，独立于人工处理状态';
COMMENT ON COLUMN dm_alarm_event.dedup_key IS '后台同对象同参数的告警合并键';
CREATE TABLE IF NOT EXISTS sys_alarm_event (
    id BIGSERIAL PRIMARY KEY,
    alarm_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    obj_name VARCHAR(100) NOT NULL,
    alarm_type VARCHAR(100) NOT NULL,
    alarm_content VARCHAR(500) NOT NULL,
    alarm_level INT DEFAULT 2 NOT NULL,
    alarm_status INT DEFAULT 0 NOT NULL,
    is_deleted INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metric_code VARCHAR(100),
    current_value NUMERIC(14,2),
    threshold_value NUMERIC(14,2),
    first_alarm_time TIMESTAMP,
    last_alarm_time TIMESTAMP,
    recover_time TIMESTAMP,
    trigger_count INT NOT NULL DEFAULT 1
);

COMMENT ON TABLE sys_alarm_event IS '系统告警事件表';
COMMENT ON COLUMN sys_alarm_event.id IS '主键ID';
COMMENT ON COLUMN sys_alarm_event.alarm_time IS '告警时间';
COMMENT ON COLUMN sys_alarm_event.obj_name IS '系统对象';
COMMENT ON COLUMN sys_alarm_event.alarm_type IS '系统告警类型英文编码，复用系统监测指标枚举';
COMMENT ON COLUMN sys_alarm_event.alarm_content IS '告警内容';
COMMENT ON COLUMN sys_alarm_event.alarm_level IS '2一般、3严重';
COMMENT ON COLUMN sys_alarm_event.alarm_status IS '告警状态：0未恢复 1已恢复';
COMMENT ON COLUMN sys_alarm_event.is_deleted IS '是否删除：0否 1是';
COMMENT ON COLUMN sys_alarm_event.create_time IS '创建时间';
COMMENT ON COLUMN sys_alarm_event.update_time IS '更新时间';

-- 系统健康状态快照表：每 5 秒采集一次，仅保留最近 7 天。
-- 此表与已有数据监测告警表完全独立，不能混用。

COMMENT ON COLUMN sys_alarm_event.metric_code IS '系统监测指标编码';
COMMENT ON COLUMN sys_alarm_event.current_value IS '当前指标值';
COMMENT ON COLUMN sys_alarm_event.threshold_value IS '触发阈值';
COMMENT ON COLUMN sys_alarm_event.first_alarm_time IS '首次触发时间';
COMMENT ON COLUMN sys_alarm_event.last_alarm_time IS '最近异常时间';
COMMENT ON COLUMN sys_alarm_event.recover_time IS '自动恢复时间';
COMMENT ON COLUMN sys_alarm_event.trigger_count IS '连续异常触发次数';
CREATE TABLE IF NOT EXISTS sys_monitor_snapshot (
    id BIGSERIAL PRIMARY KEY,
    sample_time TIMESTAMP NOT NULL,
    service_status VARCHAR(20) NOT NULL,
    uptime_seconds BIGINT NOT NULL,
    online_node_count INT NOT NULL,
    total_node_count INT NOT NULL,
    database_latency_ms BIGINT,
    message_queue_depth BIGINT,
    heartbeat_status VARCHAR(20) NOT NULL,
    last_heartbeat_time TIMESTAMP,
    cpu_usage_percent NUMERIC(5,2),
    memory_usage_percent NUMERIC(5,2),
    disk_usage_percent NUMERIC(5,2),
    health_score INT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_monitor_snapshot_time
    ON sys_monitor_snapshot (sample_time DESC);

COMMENT ON TABLE sys_monitor_snapshot IS '系统资源与服务健康状态快照表';
COMMENT ON COLUMN sys_monitor_snapshot.id IS '快照主键ID';
COMMENT ON COLUMN sys_monitor_snapshot.sample_time IS '状态采样时间';
COMMENT ON COLUMN sys_monitor_snapshot.service_status IS '服务状态：RUNNING运行中、DOWN停止、ERROR异常';
COMMENT ON COLUMN sys_monitor_snapshot.uptime_seconds IS '服务运行时长，单位秒';
COMMENT ON COLUMN sys_monitor_snapshot.online_node_count IS '在线处理节点数量';
COMMENT ON COLUMN sys_monitor_snapshot.total_node_count IS '处理节点总数量';
COMMENT ON COLUMN sys_monitor_snapshot.database_latency_ms IS '数据库连接检测耗时，单位毫秒';
COMMENT ON COLUMN sys_monitor_snapshot.message_queue_depth IS '遥测接收队列待处理消息数量';
COMMENT ON COLUMN sys_monitor_snapshot.heartbeat_status IS '数据处理服务心跳状态';
COMMENT ON COLUMN sys_monitor_snapshot.last_heartbeat_time IS '最近一次数据处理心跳时间';
COMMENT ON COLUMN sys_monitor_snapshot.cpu_usage_percent IS 'CPU使用率，单位百分比';
COMMENT ON COLUMN sys_monitor_snapshot.memory_usage_percent IS '内存使用率，单位百分比';
COMMENT ON COLUMN sys_monitor_snapshot.disk_usage_percent IS '磁盘使用率，单位百分比';
COMMENT ON COLUMN sys_monitor_snapshot.health_score IS '综合健康评分，范围0至100';
COMMENT ON COLUMN sys_monitor_snapshot.create_time IS '记录创建时间';

-- 校准通道配置表
CREATE TABLE IF NOT EXISTS calib_channel_config (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100),
    channel_name VARCHAR(100) NOT NULL,
    calibration_formula VARCHAR(100) NOT NULL,
    normal_range VARCHAR(50),
    detect_method INT NOT NULL,
    sigma_value INT,
    fluctuation_rate DOUBLE PRECISION,
    chauvenet_coef DOUBLE PRECISION,
    min_sample_count INT,
    iterate_count INT,
    sample_window INT,
    dynamic_update INT NOT NULL DEFAULT 0,
    auto_clean INT NOT NULL DEFAULT 0,
    enabled INT NOT NULL DEFAULT 1,
    is_deleted INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE calib_channel_config IS '校准通道配置表';
COMMENT ON COLUMN calib_channel_config.id IS '自增主键ID';
COMMENT ON COLUMN calib_channel_config.task_id IS '外部试验任务编号';
COMMENT ON COLUMN calib_channel_config.channel_name IS '通道名称';
COMMENT ON COLUMN calib_channel_config.calibration_formula IS '校准公式';
COMMENT ON COLUMN calib_channel_config.normal_range IS '正常范围，如0~36V，仅阈值法必填';
COMMENT ON COLUMN calib_channel_config.detect_method IS '野值检测方法：1莱特准则 2阈值法 3肖维涅法';
COMMENT ON COLUMN calib_channel_config.sigma_value IS 'σ倍数，用于莱特准则，如3表示3σ';
COMMENT ON COLUMN calib_channel_config.fluctuation_rate IS '波动阈值百分比，用于阈值法，如10.5表示10.5%';
COMMENT ON COLUMN calib_channel_config.chauvenet_coef IS '肖维涅判别常数，固定为0.5';
COMMENT ON COLUMN calib_channel_config.min_sample_count IS '肖维涅法参与计算的最近样本数';
COMMENT ON COLUMN calib_channel_config.iterate_count IS '肖维涅法迭代次数';
COMMENT ON COLUMN calib_channel_config.sample_window IS '莱特准则使用的最近样本窗口';
COMMENT ON COLUMN calib_channel_config.dynamic_update IS '是否动态更新：0否 1是';
COMMENT ON COLUMN calib_channel_config.auto_clean IS '是否自动排除异常值：0否 1是';
COMMENT ON COLUMN calib_channel_config.enabled IS '是否启用通道：0否 1是';
COMMENT ON COLUMN calib_channel_config.is_deleted IS '是否删除：0否 1是';
COMMENT ON COLUMN calib_channel_config.create_time IS '创建时间';
COMMENT ON COLUMN calib_channel_config.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_calib_channel_config_task_id ON calib_channel_config (task_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_calib_channel_task_formula
    ON calib_channel_config (task_id, calibration_formula) WHERE is_deleted = 0;

-- 设备卫星表：编码和名称来自Excel页签，类型由前端选择。
CREATE TABLE IF NOT EXISTS device_satellite (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(1) NOT NULL,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE device_satellite IS '设备卫星配置';
COMMENT ON COLUMN device_satellite.id IS '主键';
COMMENT ON COLUMN device_satellite.task_id IS '外部试验任务编号，所有操作的数据隔离范围';
COMMENT ON COLUMN device_satellite.code IS '编码，导入时为Excel页签名称';
COMMENT ON COLUMN device_satellite.name IS '名称，导入时为Excel页签名称';
COMMENT ON COLUMN device_satellite.type IS '类型：1卫星，2设备';
COMMENT ON COLUMN device_satellite.is_deleted IS '是否删除：0否，1是';
COMMENT ON COLUMN device_satellite.create_time IS '创建时间';
COMMENT ON COLUMN device_satellite.update_time IS '更新时间';
-- 编码仅在当前任务、当前类型的有效记录中唯一。
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_satellite_task_type_code
    ON device_satellite (task_id, type, code) WHERE is_deleted = 0;

-- 所属系统按设备卫星分层存储，同名系统可位于不同父节点。
CREATE TABLE IF NOT EXISTS telemetry_system_config (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    device_satellite_id BIGINT NOT NULL,
    system_name VARCHAR(100) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE telemetry_system_config IS '遥测参数所属系统层级配置';
COMMENT ON COLUMN telemetry_system_config.id IS '主键';
COMMENT ON COLUMN telemetry_system_config.task_id IS '外部试验任务编号';
COMMENT ON COLUMN telemetry_system_config.device_satellite_id IS '设备卫星主键';
COMMENT ON COLUMN telemetry_system_config.system_name IS '当前层级系统名称';
COMMENT ON COLUMN telemetry_system_config.parent_id IS '父系统主键，0表示设备直属系统';
COMMENT ON COLUMN telemetry_system_config.sort_order IS '导入文件中首次出现的顺序';
COMMENT ON COLUMN telemetry_system_config.is_deleted IS '是否删除：0否，1是';
COMMENT ON COLUMN telemetry_system_config.create_time IS '创建时间';
COMMENT ON COLUMN telemetry_system_config.update_time IS '更新时间';
CREATE UNIQUE INDEX IF NOT EXISTS uk_telemetry_system_active_path
    ON telemetry_system_config (task_id, device_satellite_id, parent_id, system_name)
    WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_telemetry_system_children
    ON telemetry_system_config (task_id, device_satellite_id, parent_id, sort_order)
    WHERE is_deleted = 0;

-- 遥测参数解析配置表：全部业务列按字符串存储。
CREATE TABLE IF NOT EXISTS telemetry_parse_rule_config (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    device_satellite_id BIGINT NOT NULL,
    system_id BIGINT,
    table_index VARCHAR(100) NOT NULL,
    bit_width VARCHAR(100) NOT NULL,
    telemetry_name VARCHAR(100) NOT NULL,
    telemetry_code VARCHAR(100) NOT NULL,
    formula_type VARCHAR(100) NOT NULL,
    formula_desc TEXT,
    process_param TEXT,
    decimal_places VARCHAR(100),
    alarm_flag VARCHAR(1) DEFAULT '0',
    normal_value TEXT,
    warning_value TEXT,
    state_change_info TEXT,
    command_code TEXT,
    system_name TEXT,
    control_channel VARCHAR(100),
    merge_channel_count VARCHAR(100),
    delay_channel VARCHAR(100),
    store_flag VARCHAR(1) DEFAULT '0',
    calibration_formula VARCHAR(100),
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE telemetry_parse_rule_config IS '参数解析配置';
COMMENT ON COLUMN telemetry_parse_rule_config.id IS '主键';
COMMENT ON COLUMN telemetry_parse_rule_config.task_id IS '外部试验任务编号';
COMMENT ON COLUMN telemetry_parse_rule_config.device_satellite_id IS '设备卫星表ID，由代码校验关联';
COMMENT ON COLUMN telemetry_parse_rule_config.system_id IS '所属系统主键，为空表示直属设备卫星';
COMMENT ON COLUMN telemetry_parse_rule_config.table_index IS '序号';
COMMENT ON COLUMN telemetry_parse_rule_config.bit_width IS '位宽';
COMMENT ON COLUMN telemetry_parse_rule_config.telemetry_name IS '遥测名称';
COMMENT ON COLUMN telemetry_parse_rule_config.telemetry_code IS '遥测代号';
COMMENT ON COLUMN telemetry_parse_rule_config.formula_type IS '公式类型';
COMMENT ON COLUMN telemetry_parse_rule_config.formula_desc IS '处理公式';
COMMENT ON COLUMN telemetry_parse_rule_config.process_param IS '处理参数';
COMMENT ON COLUMN telemetry_parse_rule_config.decimal_places IS '小数位数';
COMMENT ON COLUMN telemetry_parse_rule_config.alarm_flag IS '是否报警：0否，1是';
COMMENT ON COLUMN telemetry_parse_rule_config.normal_value IS '正常值范围';
COMMENT ON COLUMN telemetry_parse_rule_config.warning_value IS '预警值范围';
COMMENT ON COLUMN telemetry_parse_rule_config.state_change_info IS '状态跳变信息';
COMMENT ON COLUMN telemetry_parse_rule_config.command_code IS '相关命令';
COMMENT ON COLUMN telemetry_parse_rule_config.system_name IS '导入文件中的所属系统原始层级路径';
COMMENT ON COLUMN telemetry_parse_rule_config.control_channel IS '控制波道';
COMMENT ON COLUMN telemetry_parse_rule_config.merge_channel_count IS '合并波道';
COMMENT ON COLUMN telemetry_parse_rule_config.delay_channel IS '延时波道';
COMMENT ON COLUMN telemetry_parse_rule_config.store_flag IS '存储遥测：0否，1是';
COMMENT ON COLUMN telemetry_parse_rule_config.calibration_formula IS '校准公式';
COMMENT ON COLUMN telemetry_parse_rule_config.is_deleted IS '是否删除：0否，1是';
COMMENT ON COLUMN telemetry_parse_rule_config.create_time IS '创建时间';
COMMENT ON COLUMN telemetry_parse_rule_config.update_time IS '更新时间';
-- 任务与设备卫星共同限定有效参数的唯一范围。
CREATE UNIQUE INDEX IF NOT EXISTS uk_parse_rule_task_device_index
    ON telemetry_parse_rule_config (task_id, device_satellite_id, table_index) WHERE is_deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_parse_rule_task_device_code
    ON telemetry_parse_rule_config (task_id, device_satellite_id, telemetry_code) WHERE is_deleted = 0;
-- 任务范围内按遥测代号搜索筛选框参数。
CREATE INDEX IF NOT EXISTS idx_parse_rule_task_code
    ON telemetry_parse_rule_config (task_id, telemetry_code) WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_parse_rule_system
    ON telemetry_parse_rule_config (task_id, device_satellite_id, system_id, id)
    WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_parse_rule_device_page
    ON telemetry_parse_rule_config (task_id, device_satellite_id, id)
    WHERE is_deleted = 0;

-- 固定页面勾选按任务共享；可视化组件勾选按组件主键隔离。
CREATE TABLE IF NOT EXISTS processed_telemetry_filter_selection (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    device_satellite_id BIGINT NOT NULL,
    telemetry_code VARCHAR(100) NOT NULL,
    selection_type SMALLINT NOT NULL DEFAULT 1,
    target_id BIGINT NOT NULL DEFAULT 0,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 已有勾选记录归入处理后数据表格，再复制给处理后数据曲线。
ALTER TABLE processed_telemetry_filter_selection
    ADD COLUMN IF NOT EXISTS selection_type SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE processed_telemetry_filter_selection
    ADD COLUMN IF NOT EXISTS target_id BIGINT NOT NULL DEFAULT 0;
DO $$
DECLARE old_constraint RECORD;
BEGIN
    FOR old_constraint IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'processed_telemetry_filter_selection'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (task_id, device_satellite_id, telemetry_code)'
    LOOP
        -- 使用函数拼接语句，兼容将双竖线解释为逻辑或的数据库模式。
        EXECUTE concat('ALTER TABLE processed_telemetry_filter_selection DROP CONSTRAINT ',
                       quote_ident(old_constraint.conname));
    END LOOP;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uk_telemetry_selection_scope
    ON processed_telemetry_filter_selection
       (task_id, selection_type, target_id, device_satellite_id, telemetry_code);
CREATE INDEX IF NOT EXISTS idx_telemetry_selection_task_device
    ON processed_telemetry_filter_selection (task_id, device_satellite_id, telemetry_code);
INSERT INTO processed_telemetry_filter_selection
    (task_id, device_satellite_id, telemetry_code, selection_type, target_id, is_deleted)
SELECT task_id, device_satellite_id, telemetry_code, 2, 0, is_deleted
FROM processed_telemetry_filter_selection
WHERE selection_type = 1 AND target_id = 0
ON CONFLICT (task_id, selection_type, target_id, device_satellite_id, telemetry_code)
DO NOTHING;
COMMENT ON TABLE processed_telemetry_filter_selection IS '处理后数据及可视化组件遥测参数勾选记录';
COMMENT ON COLUMN processed_telemetry_filter_selection.id IS '主键';
COMMENT ON COLUMN processed_telemetry_filter_selection.task_id IS '外部试验任务编号';
COMMENT ON COLUMN processed_telemetry_filter_selection.device_satellite_id IS '设备卫星主键';
COMMENT ON COLUMN processed_telemetry_filter_selection.telemetry_code IS '遥测代号';
COMMENT ON COLUMN processed_telemetry_filter_selection.selection_type IS '勾选场景：1处理后数据表格 2处理后数据曲线 3数据可视化表格 4数据可视化曲线';
COMMENT ON COLUMN processed_telemetry_filter_selection.target_id IS '固定页面为0，数据可视化页面为组件主键';
COMMENT ON COLUMN processed_telemetry_filter_selection.is_deleted IS '是否取消勾选：0否，1是';
COMMENT ON COLUMN processed_telemetry_filter_selection.create_time IS '创建时间';
COMMENT ON COLUMN processed_telemetry_filter_selection.update_time IS '更新时间';

-- 采集接口管理表
CREATE TABLE IF NOT EXISTS collect_interface_config (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100),
    interface_name VARCHAR(100) NOT NULL,
    interface_type SMALLINT NOT NULL,
    send_from VARCHAR(100),
    message_content VARCHAR(500),
    transfer_type SMALLINT NOT NULL,
    transfer_protocol SMALLINT NOT NULL,
    protocol_config_id BIGINT,
    host VARCHAR(100) NOT NULL,
    port INT NOT NULL,
    multicast_ip VARCHAR(100),
    status SMALLINT NOT NULL DEFAULT 1,
    enabled SMALLINT NOT NULL DEFAULT 1,
    rpc_enabled SMALLINT NOT NULL DEFAULT 0,
    data_packet_count BIGINT DEFAULT 0,
    remark VARCHAR(500),
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

COMMENT ON TABLE collect_interface_config IS '采集接口管理表';
COMMENT ON COLUMN collect_interface_config.id IS '自增主键';
COMMENT ON COLUMN collect_interface_config.task_id IS '外部试验任务编号';
COMMENT ON COLUMN collect_interface_config.interface_name IS '接口名称';
COMMENT ON COLUMN collect_interface_config.interface_type IS '接口类型：1外部接口 2内部接口';
COMMENT ON COLUMN collect_interface_config.send_from IS '发送方标识';
COMMENT ON COLUMN collect_interface_config.message_content IS '消息内容';
COMMENT ON COLUMN collect_interface_config.transfer_type IS '传输方式：1UDP 2TCP 3HTTP';
COMMENT ON COLUMN collect_interface_config.transfer_protocol IS '传输协议：1JSON 2PDXP 3FEP';
COMMENT ON COLUMN collect_interface_config.protocol_config_id IS '协议配置ID';
COMMENT ON COLUMN collect_interface_config.host IS '数据处理服务本机监听网卡IP地址';
COMMENT ON COLUMN collect_interface_config.port IS '数据处理服务本机监听端口';
COMMENT ON COLUMN collect_interface_config.multicast_ip IS 'UDP组播地址，为空时按UDP单播接收';
COMMENT ON COLUMN collect_interface_config.status IS '在线状态：0离线 1在线，由启用状态决定';
COMMENT ON COLUMN collect_interface_config.enabled IS '启用状态：0禁用 1启用';
COMMENT ON COLUMN collect_interface_config.data_packet_count IS '累计数据包数量';
COMMENT ON COLUMN collect_interface_config.remark IS '接口备注';
COMMENT ON COLUMN collect_interface_config.is_deleted IS '是否删除：0否 1是';
COMMENT ON COLUMN collect_interface_config.create_time IS '创建时间';
COMMENT ON COLUMN collect_interface_config.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_collect_interface_config_task_id ON collect_interface_config (task_id);

-- 兼容已经创建的采集接口表，补充RPC处理开关。
ALTER TABLE collect_interface_config
    ADD COLUMN IF NOT EXISTS rpc_enabled SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN collect_interface_config.rpc_enabled IS '是否启用RPC处理：0否 1是';

-- 协议配置表
CREATE TABLE IF NOT EXISTS collect_protocol_config (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100),
    config_name VARCHAR(100) NOT NULL,
    protocol_type SMALLINT NOT NULL,
    data_source_type SMALLINT NOT NULL DEFAULT 1,
    config_desc VARCHAR(500),
    config_params TEXT,
    parser_class VARCHAR(200),
    enabled SMALLINT NOT NULL DEFAULT 1,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

COMMENT ON TABLE collect_protocol_config IS '协议配置表';
COMMENT ON COLUMN collect_protocol_config.id IS '自增主键';
COMMENT ON COLUMN collect_protocol_config.task_id IS '外部试验任务编号';
COMMENT ON COLUMN collect_protocol_config.config_name IS '配置名称';
COMMENT ON COLUMN collect_protocol_config.protocol_type IS '协议类型：1JSON 2PDXP 3Protobuf 4RPC';
COMMENT ON COLUMN collect_protocol_config.data_source_type IS '数据源类型：1遥测 2遥感 3设备 4环境';
COMMENT ON COLUMN collect_protocol_config.config_desc IS '描述';
COMMENT ON COLUMN collect_protocol_config.config_params IS '协议配置参数';
COMMENT ON COLUMN collect_protocol_config.parser_class IS '协议解析处理类';
COMMENT ON COLUMN collect_protocol_config.enabled IS '启用状态：0禁用 1启用';
COMMENT ON COLUMN collect_protocol_config.is_deleted IS '是否删除：0否 1是';
COMMENT ON COLUMN collect_protocol_config.create_time IS '创建时间';
COMMENT ON COLUMN collect_protocol_config.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_collect_protocol_config_task_id ON collect_protocol_config (task_id);

-- FEP文件接收、对象上传和消息发布处理记录表。
CREATE TABLE IF NOT EXISTS fep_file_transfer_record (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    interface_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50),
    file_length BIGINT NOT NULL,
    local_path VARCHAR(1000) NOT NULL,
    object_name VARCHAR(1000) NOT NULL,
    file_url VARCHAR(2000) NOT NULL,
    process_status SMALLINT NOT NULL DEFAULT 1,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(2000),
    next_retry_time TIMESTAMP,
    completed_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 已有环境补充文件后缀字段，新建环境执行时不会重复添加。
ALTER TABLE fep_file_transfer_record
    ADD COLUMN IF NOT EXISTS file_type VARCHAR(50);

COMMENT ON TABLE fep_file_transfer_record IS 'FEP文件接收和后续处理记录表';
COMMENT ON COLUMN fep_file_transfer_record.id IS '记录主键ID';
COMMENT ON COLUMN fep_file_transfer_record.task_id IS '外部试验任务编号';
COMMENT ON COLUMN fep_file_transfer_record.interface_id IS '采集接口ID';
COMMENT ON COLUMN fep_file_transfer_record.file_name IS '发送方提供的文件名';
COMMENT ON COLUMN fep_file_transfer_record.file_type IS '文件后缀，小写且不包含点号';
COMMENT ON COLUMN fep_file_transfer_record.file_length IS '文件字节长度';
COMMENT ON COLUMN fep_file_transfer_record.local_path IS '本地完整文件路径';
COMMENT ON COLUMN fep_file_transfer_record.object_name IS '对象存储中的对象名称';
COMMENT ON COLUMN fep_file_transfer_record.file_url IS '文件对外访问地址';
COMMENT ON COLUMN fep_file_transfer_record.process_status IS '处理状态：1已接收 2已上传 3已发布';
COMMENT ON COLUMN fep_file_transfer_record.retry_count IS '后续处理失败次数';
COMMENT ON COLUMN fep_file_transfer_record.last_error IS '最近一次失败原因';
COMMENT ON COLUMN fep_file_transfer_record.next_retry_time IS '下次允许重试时间';
COMMENT ON COLUMN fep_file_transfer_record.completed_time IS '文件接收完成时间';
COMMENT ON COLUMN fep_file_transfer_record.create_time IS '创建时间';
COMMENT ON COLUMN fep_file_transfer_record.update_time IS '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_fep_file_transfer_identity
    ON fep_file_transfer_record (
        task_id, interface_id, file_name, file_length);

CREATE INDEX IF NOT EXISTS idx_fep_file_transfer_retry
    ON fep_file_transfer_record (
        process_status, next_retry_time, update_time);
-- 按任务、上传状态和接收时间分页展示图片与文件。
CREATE INDEX IF NOT EXISTS idx_fep_file_transfer_task_time
    ON fep_file_transfer_record (task_id, process_status, completed_time DESC, id DESC);

-- 系统日志表
CREATE TABLE IF NOT EXISTS sys_runtime_log (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100),
    log_time TIMESTAMP NOT NULL,
    log_level VARCHAR(20) NOT NULL,
    log_source VARCHAR(100) NOT NULL,
    operator_id VARCHAR(100),
    log_content VARCHAR(500) NOT NULL,
    log_detail TEXT
);

COMMENT ON TABLE sys_runtime_log IS '系统日志表';
COMMENT ON COLUMN sys_runtime_log.id IS '自增主键';
COMMENT ON COLUMN sys_runtime_log.task_id IS '外部试验任务编号';
COMMENT ON COLUMN sys_runtime_log.log_time IS '时间';
COMMENT ON COLUMN sys_runtime_log.log_level IS '操作日志级别：INFO信息、WARN警告、ERROR错误';
COMMENT ON COLUMN sys_runtime_log.log_source IS '操作日志来源英文枚举编码，中文名称由接口枚举返回';
COMMENT ON COLUMN sys_runtime_log.operator_id IS '操作人ID';
COMMENT ON COLUMN sys_runtime_log.log_content IS '内容';
COMMENT ON COLUMN sys_runtime_log.log_detail IS '详情';

CREATE INDEX IF NOT EXISTS idx_sys_runtime_log_task_id ON sys_runtime_log (task_id);
CREATE INDEX IF NOT EXISTS idx_sys_runtime_log_task_time
    ON sys_runtime_log (task_id, log_time DESC);

-- 监测页面组件表
-- 一条记录代表一个可以移动、缩放的页面组件。
CREATE TABLE IF NOT EXISTS monitor_dashboard_widget (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    widget_key VARCHAR(64) NOT NULL,
    widget_type SMALLINT NOT NULL,
    widget_title VARCHAR(100),
    grid_x INT NOT NULL DEFAULT 0,
    grid_y INT NOT NULL DEFAULT 0,
    grid_width INT NOT NULL DEFAULT 6,
    grid_height INT NOT NULL DEFAULT 4,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE monitor_dashboard_widget IS '用户试验任务监测页面组件表';
COMMENT ON COLUMN monitor_dashboard_widget.id IS '组件主键ID';
COMMENT ON COLUMN monitor_dashboard_widget.task_id IS '外部试验任务编号';
COMMENT ON COLUMN monitor_dashboard_widget.user_id IS '用户ID，对应系统用户ID';
COMMENT ON COLUMN monitor_dashboard_widget.widget_key IS '组件唯一标识，同一用户同一试验任务内唯一';
COMMENT ON COLUMN monitor_dashboard_widget.widget_type IS '组件类型：1实时曲线 2实时数据 3实时告警 4载荷图像 5文件表格';
COMMENT ON COLUMN monitor_dashboard_widget.widget_title IS '组件显示标题';
COMMENT ON COLUMN monitor_dashboard_widget.grid_x IS '组件左上角横向网格位置，从0开始';
COMMENT ON COLUMN monitor_dashboard_widget.grid_y IS '组件左上角纵向网格位置，从0开始';
COMMENT ON COLUMN monitor_dashboard_widget.grid_width IS '组件占用的网格列数';
COMMENT ON COLUMN monitor_dashboard_widget.grid_height IS '组件占用的网格行数';
COMMENT ON COLUMN monitor_dashboard_widget.enabled IS '是否显示组件：0否 1是';
COMMENT ON COLUMN monitor_dashboard_widget.create_time IS '创建时间';
COMMENT ON COLUMN monitor_dashboard_widget.update_time IS '更新时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_monitor_widget_task_user_key
    ON monitor_dashboard_widget (task_id, user_id, widget_key);

COMMENT ON INDEX uk_monitor_widget_task_user_key IS '保证同一用户同一试验任务内的组件标识唯一';

CREATE INDEX IF NOT EXISTS idx_monitor_widget_task_user
    ON monitor_dashboard_widget (task_id, user_id);

COMMENT ON INDEX idx_monitor_widget_task_user IS '用于按试验任务和用户加载全部页面组件';

-- 组件数据项配置表
-- 保存截图中组件选择的参数解析配置及可选数据来源。
CREATE TABLE IF NOT EXISTS monitor_widget_config_item (
    id BIGSERIAL PRIMARY KEY,
    widget_id BIGINT NOT NULL,
    parse_rule_id BIGINT,
    interface_id BIGINT,
    protocol_config_id BIGINT,
    item_code VARCHAR(100) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    is_selected SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE monitor_widget_config_item IS '监测页面组件数据项配置表';
COMMENT ON COLUMN monitor_widget_config_item.id IS '配置项主键ID';
COMMENT ON COLUMN monitor_widget_config_item.widget_id IS '所属页面组件ID，对应monitor_dashboard_widget.id';
COMMENT ON COLUMN monitor_widget_config_item.parse_rule_id IS '参数解析配置ID，对应telemetry_parse_rule_config.id';
COMMENT ON COLUMN monitor_widget_config_item.interface_id IS '采集接口ID；解析参数自动同步项为空';
COMMENT ON COLUMN monitor_widget_config_item.protocol_config_id IS '协议配置ID；解析参数自动同步项为空';
COMMENT ON COLUMN monitor_widget_config_item.item_code IS '数据项编码，如T001、A001、V001或C001';
COMMENT ON COLUMN monitor_widget_config_item.item_name IS '数据项名称，如温度、姿态角X、电压或电流';
COMMENT ON COLUMN monitor_widget_config_item.is_selected IS '非遥测数据项勾选状态；遥测参数以场景勾选表为准';
COMMENT ON COLUMN monitor_widget_config_item.create_time IS '创建时间';
COMMENT ON COLUMN monitor_widget_config_item.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_monitor_config_item_widget_id
    ON monitor_widget_config_item (widget_id);

CREATE INDEX IF NOT EXISTS idx_monitor_config_item_parse_rule_id
    ON monitor_widget_config_item (parse_rule_id);

COMMENT ON INDEX idx_monitor_config_item_widget_id IS '用于按组件加载采集接口、协议和数据项配置';
COMMENT ON INDEX idx_monitor_config_item_parse_rule_id IS '用于按参数解析配置同步可视化筛选项';

-- 将已有可视化组件选中的遥测参数归入组件自己的勾选场景。
INSERT INTO processed_telemetry_filter_selection
    (task_id, device_satellite_id, telemetry_code, selection_type, target_id, is_deleted)
SELECT widget.task_id, rule_config.device_satellite_id, rule_config.telemetry_code,
       CASE WHEN widget.widget_type = 1 THEN 4 ELSE 3 END, widget.id, 0
FROM monitor_widget_config_item item
JOIN monitor_dashboard_widget widget ON widget.id = item.widget_id
JOIN telemetry_parse_rule_config rule_config
  ON rule_config.id = item.parse_rule_id AND rule_config.task_id = widget.task_id
WHERE widget.widget_type IN (1, 2) AND item.is_selected = 1
  AND rule_config.is_deleted = 0
ON CONFLICT (task_id, selection_type, target_id, device_satellite_id, telemetry_code)
DO NOTHING;

COMMIT;

-- 自动告警查询与合并使用的索引。
CREATE INDEX IF NOT EXISTS idx_sys_alarm_metric_status ON sys_alarm_event (metric_code, alarm_status, last_alarm_time);
CREATE INDEX IF NOT EXISTS idx_dm_alarm_dedup_time ON dm_alarm_event (dedup_key, last_trigger_time);
