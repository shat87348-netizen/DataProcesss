-- KingbaseES RBAC schema. Initial account: admin / admin123.
create sequence if not exists dp_user_id_seq start with 1 increment by 1;
create sequence if not exists dp_role_id_seq start with 1 increment by 1;
create sequence if not exists dp_menu_id_seq start with 1 increment by 1;

create table if not exists dp_user (
    id bigint primary key default nextval('dp_user_id_seq'),
    username varchar(64) not null unique,
    password varchar(100) not null,
    baseline_user_id varchar(100),
    nickname varchar(64),
    phone varchar(32),
    email varchar(128),
    status smallint not null default 1,
    deleted smallint not null default 0,
    create_time timestamp not null default current_timestamp,
    update_time timestamp not null default current_timestamp
);

-- 兼容已经初始化过的数据库。
alter table dp_user add column if not exists email varchar(128);
alter table dp_user add column if not exists baseline_user_id varchar(100);

comment on table dp_user is '系统用户表';
comment on column dp_user.id is '用户主键';
comment on column dp_user.username is '登录账号';
comment on column dp_user.password is 'BCrypt 加密后的登录密码';
comment on column dp_user.baseline_user_id is '基线平台用户编号，本地用户可以为空';
comment on column dp_user.nickname is '用户昵称';
comment on column dp_user.phone is '手机号码';
comment on column dp_user.email is '联系邮箱';
comment on column dp_user.status is '用户状态：1-正常，0-停用';
comment on column dp_user.deleted is '逻辑删除标识：0-正常，1-已删除';
comment on column dp_user.create_time is '创建时间';
comment on column dp_user.update_time is '最后修改时间';
create unique index if not exists uk_dp_user_baseline_user
    on dp_user (baseline_user_id) where baseline_user_id is not null and deleted = 0;

create table if not exists dp_role (
    id bigint primary key default nextval('dp_role_id_seq'),
    role_name varchar(64) not null,
    role_key varchar(64) not null unique,
    baseline_role_id varchar(100),
    baseline_role_key varchar(100),
    status smallint not null default 1,
    deleted smallint not null default 0,
    create_time timestamp not null default current_timestamp
);

alter table dp_role add column if not exists baseline_role_id varchar(100);
alter table dp_role add column if not exists baseline_role_key varchar(100);

comment on table dp_role is '系统角色表';
comment on column dp_role.id is '角色主键';
comment on column dp_role.role_name is '角色名称';
comment on column dp_role.role_key is '本地角色标识，例如 admin';
comment on column dp_role.baseline_role_id is '基线平台角色编号，本地角色可以为空';
comment on column dp_role.baseline_role_key is '基线平台角色标识';
comment on column dp_role.status is '角色状态：1-正常，0-停用';
comment on column dp_role.deleted is '逻辑删除标识：0-正常，1-已删除';
comment on column dp_role.create_time is '创建时间';
create unique index if not exists uk_dp_role_baseline_role
    on dp_role (baseline_role_id) where baseline_role_id is not null and deleted = 0;

create table if not exists dp_menu (
    id bigint primary key default nextval('dp_menu_id_seq'),
    parent_id bigint not null default 0,
    label varchar(64) not null,
    icon varchar(100) not null,
    path varchar(200),
    hidden smallint not null default 0,
    sort_order integer not null default 0,
    deleted smallint not null default 0
);

comment on table dp_menu is '左侧菜单表';
comment on column dp_menu.id is '菜单主键';
comment on column dp_menu.parent_id is '父菜单主键，顶级菜单为 0';
comment on column dp_menu.label is '菜单显示名称';
comment on column dp_menu.icon is '菜单图标';
comment on column dp_menu.path is '页面访问路径，分组菜单可以为空';
comment on column dp_menu.hidden is '隐藏标识：0-显示，1-隐藏';
comment on column dp_menu.sort_order is '同级菜单显示顺序，数值越小越靠前';
comment on column dp_menu.deleted is '逻辑删除标识：0-正常，1-已删除';

create table if not exists dp_user_role (
    user_id bigint not null,
    role_id bigint not null,
    primary key(user_id, role_id)
);

comment on table dp_user_role is '用户与角色关联表';
comment on column dp_user_role.user_id is '用户主键';
comment on column dp_user_role.role_id is '角色主键';

create table if not exists dp_role_menu (
    role_id bigint not null,
    menu_id bigint not null,
    primary key(role_id, menu_id)
);

comment on table dp_role_menu is '角色与菜单关联表';
comment on column dp_role_menu.role_id is '角色主键';
comment on column dp_role_menu.menu_id is '菜单主键';

insert into dp_user(username,password,nickname,status,deleted)
select 'admin','$2a$10$KHmdYfRvtth/FzHjTLiaoOtRomAI313wCzoNbfNlLr1ARzPRum0o.','管理员',1,0
where not exists(select 1 from dp_user where username='admin');
insert into dp_role(role_name,role_key,status,deleted)
select '超级管理员','admin',1,0 where not exists(select 1 from dp_role where role_key='admin');
-- 本地管理员和超级管理员是系统保底数据，已有记录也恢复为有效状态。
update dp_user
set status=1,
    deleted=0,
    password=case
        when password='{init}admin123'
            then '$2a$10$KHmdYfRvtth/FzHjTLiaoOtRomAI313wCzoNbfNlLr1ARzPRum0o.'
        else password
    end,
    update_time=current_timestamp
where username='admin';
update dp_role
set role_name='超级管理员',status=1,deleted=0
where role_key='admin';
insert into dp_user_role(user_id,role_id)
select u.id,r.id
from dp_user u, dp_role r
where u.username = 'admin'
  and r.role_key = 'admin'
  and not exists(
      select 1 from dp_user_role ur
      where ur.user_id = u.id and ur.role_id = r.id
  );

-- 数据校准：每个校准通道只对应一条校准及野值数量汇总记录。
CREATE TABLE IF NOT EXISTS calib_record (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    channel_id BIGINT NOT NULL,
    channel_name VARCHAR(100) NOT NULL,
    calibration_count BIGINT NOT NULL DEFAULT 0,
    outlier_count BIGINT NOT NULL DEFAULT 0,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE calib_record IS '校准记录汇总表';
COMMENT ON COLUMN calib_record.id IS '自增主键ID';
COMMENT ON COLUMN calib_record.task_id IS '试验任务ID';
COMMENT ON COLUMN calib_record.channel_id IS '校准通道ID';
COMMENT ON COLUMN calib_record.channel_name IS '校准通道名称';
COMMENT ON COLUMN calib_record.calibration_count IS '累计参与校准的遥测参数数量，包含正常值和野值';
COMMENT ON COLUMN calib_record.outlier_count IS '累计检出的野值数量';
COMMENT ON COLUMN calib_record.is_deleted IS '是否删除：0否 1是';
COMMENT ON COLUMN calib_record.create_time IS '创建时间';
COMMENT ON COLUMN calib_record.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_calib_record_task_id
    ON calib_record (task_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_calib_record_channel
    ON calib_record (channel_id) WHERE is_deleted = 0;

-- 一条校准记录通过校准通道ID对应多条野值详情。
CREATE TABLE IF NOT EXISTS calib_record_detail (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    channel_id BIGINT NOT NULL,
    interface_id BIGINT NOT NULL,
    channel_name VARCHAR(100),
    parameter_name VARCHAR(100),
    tm_symbol VARCHAR(100) NOT NULL,
    telemetry_value DOUBLE PRECISION NOT NULL,
    detail VARCHAR(500) NOT NULL,
    is_deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE calib_record_detail IS '校准记录野值详情表';
COMMENT ON COLUMN calib_record_detail.id IS '详情主键ID';
COMMENT ON COLUMN calib_record_detail.task_id IS '试验任务ID';
COMMENT ON COLUMN calib_record_detail.channel_id IS '校准通道ID';
COMMENT ON COLUMN calib_record_detail.interface_id IS '采集接口ID';
COMMENT ON COLUMN calib_record_detail.channel_name IS '遥测帧通道名称';
COMMENT ON COLUMN calib_record_detail.parameter_name IS '遥测参数名称';
COMMENT ON COLUMN calib_record_detail.tm_symbol IS '遥测代号';
COMMENT ON COLUMN calib_record_detail.telemetry_value IS '触发野值时的遥测值';
COMMENT ON COLUMN calib_record_detail.detail IS '判定为野值的详细原因';
COMMENT ON COLUMN calib_record_detail.is_deleted IS '是否删除：0否 1是';
COMMENT ON COLUMN calib_record_detail.create_time IS '野值发生时间';

-- 详情列表按任务、校准通道和发生时间分页查询。
CREATE INDEX IF NOT EXISTS idx_calib_record_detail_task_channel_time
    ON calib_record_detail (task_id, channel_id, create_time DESC);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (10,0,'总览','i-lucide-layout-dashboard','/dashboard',0,1,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (30,0,'数据可视化','i-lucide-line-chart','/data-viz',0,2,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (20,0,'数据采集','i-lucide-radio-tower','/data-collection',0,3,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (35,0,'卫星管理','i-lucide-satellite-dish','/satellite-management',0,4,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (40,0,'数据处理','i-lucide-cog','/data-process',0,5,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (50,0,'数据校准','i-lucide-sliders-horizontal','/calibration',0,6,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (60,0,'告警管理','i-lucide-bell-ring','/alarms',0,7,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (1,0,'系统配置','i-lucide-settings',null,0,8,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (2,1,'采集代理','i-lucide-server','/config/agent',0,1,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (3,1,'用户管理','i-lucide-users','/config/user',0,2,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (4,1,'角色管理','i-lucide-users','/config/role',0,3,0);

insert into dp_menu(id,parent_id,label,icon,path,hidden,sort_order,deleted)
values (5,1,'日志管理','i-lucide-scroll-text','/config/log',0,4,0);

-- 超级管理员始终关联全部菜单。
insert into dp_role_menu(role_id,menu_id)
select administrator.id,page.id
from dp_role administrator
cross join dp_menu page
where administrator.role_key='admin'
  and page.deleted=0
  and not exists(
      select 1 from dp_role_menu relation
      where relation.role_id=administrator.id
        and relation.menu_id=page.id
  );
select setval('dp_user_id_seq',(select greatest(coalesce(max(id),0),1) from dp_user));
select setval('dp_role_id_seq',(select greatest(coalesce(max(id),0),1) from dp_role));
select setval('dp_menu_id_seq',(select greatest(coalesce(max(id),0),1) from dp_menu));

-- 野值详情按发生时间分批清理，校准汇总保留历史累计数量。
CREATE INDEX IF NOT EXISTS idx_calib_detail_retention ON calib_record_detail (create_time, id);
