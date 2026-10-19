# 数据处理软件接口文档

本文档依据当前 `data-admin-service` 和 `data-process-service` 源码整理。后台管理服务默认地址为 `http://localhost:8082`，数据处理服务默认地址为 `http://localhost:8083`。

每个接口可直接使用的入参与出参示例见同目录的 `API-EXAMPLES.md`。HTTP采集配置、普通大文本框JSON输入、数据源字段删除及协议选择的前端改动见[最新前端对接文档](前端对接-HTTP采集与协议配置变更.md)。协议分页与RPC业务结构见[协议配置说明](前端对接-协议配置与采集处理方式.md)。

## 1. 通用约定

`GET /visualization/widgets/{widgetId}/files` 的 `fileUrl` 返回临时签名读取地址，默认一小时有效；前端直接使用完整地址，过期后重新查询列表。部署地址、历史文件兼容和前端改动见[图片与文件访问地址](前端对接-图片与文件访问地址.md)。此变更不改变处理服务发布的MQ文件消息结构。

- 请求与响应编码：`UTF-8`
- POST 请求类型：`application/json`
- 除登录接口和子系统菜单配置接口外，所有接口都需要请求头：`Authorization: Bearer <token>`
- 时间格式：`yyyy-MM-dd'T'HH:mm:ss`，例如 `2026-07-30T15:20:00`
- 日期格式：`yyyy-MM-dd`
- 分页参数 `pageNum` 从 1 开始。

统一响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

分页数据位于 `data` 中：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "total": 100,
  "records": []
}
```

子系统菜单配置接口按照嵌套集成约定直接返回配置对象，不使用统一响应结构。

## 2. 子系统集成

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/integration/menu-config` | 按基线角色查询子系统菜单配置（无需 Token） | 查询：`roleIds` 必填，多个编号使用英文逗号分隔 |

接口使用 `roleIds` 匹配 `dp_role.baseline_role_id`，再通过 `dp_role_menu` 查询角色关联的未删除菜单，自动补齐父菜单并按照 `parent_id` 和 `sort_order` 生成菜单树。`systemKey`、`label`、`icon`、`baseUrl`、`defaultPath` 读取 `subsystem.integration` 配置，`origin` 根据 `baseUrl` 自动生成。

## 3. 登录认证

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| POST | `/auth/login` | 用户登录（无需 Token） | JSON：`username`、`password` |
| POST | `/auth/sso/login` | 使用基线平台 Token 登录（无需本系统 Token） | JSON：`tokenId` 必填，`clientId`、`remoteIp` 可选 |
| POST | `/auth/logout` | 退出当前登录 | 无 |
| GET | `/auth/user-info` | 当前用户和角色 | 无 |
| GET | `/auth/routes` | 根据角色菜单关系查询左侧菜单树 | 无 |

登录示例：

```http
POST http://localhost:8082/auth/login
Content-Type: application/json

{"username":"admin","password":"admin123"}
```

基线 SSO 登录时，前端把 iframe 消息或统一登录回调中的平台 Token 提交给 `/auth/sso/login`。后台依次校验平台 Token、查询平台用户和角色、同步本地账号关系，随后返回本系统 JWT。角色只按用户返回的 `roleIds` 与角色目录匹配，不再使用 `systemPermissions` 筛选。普通平台用户首次登录时自动创建同名本地用户并生成八位随机密码；用户名为 `admin` 时固定复用本地管理员和超级管理员角色。

平台用户和角色同步后仍由本地角色配置菜单范围。平台角色没有配置本地菜单时可以完成登录，但 `/auth/routes` 返回空数组；超级管理员角色通过角色菜单关系绑定全部菜单。

### 3.1 总览

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/dashboard/collection` | 查询采集实时汇总、折线图和表格 | 查询：`taskId?`、`range=3h/24h/7d` |
| GET | `/dashboard/processing` | 查询处理汇总、折线图和表格 | 查询：`taskId?`、`range=3h/24h/7d` |

前端可按固定周期并行轮询两个查询接口。接口速率从 Redis 秒级指标读取，历史采集量和处理量从金仓五秒统计表读取；查询时再按时间范围分桶，并按 `collect_interface_config.send_from` 聚合为发送方系统数据。

两个总览接口的曲线统一采用“完整固定历史区间＋最新滚动区间”。`3h` 每点覆盖5分钟，共36点；`24h` 每点覆盖30分钟，共48点；`7d` 每点覆盖4小时，共42点。最后一个点始终统计最近一个完整跨度，不从当前固定区间起点重新累计。曲线截止时间为查询时刻减10秒再向下对齐5秒，为正常采样和落库留出缓冲；数据库故障或积压超过缓冲时，仍可能存在迟到数据。

`lineChart.timePoints` 表示每个区间的结束时间，与各条 `series.values` 按下标对应。历史标签为 `HH:mm`，近7天为 `MM-dd HH:mm`；最新点标签分别为 `HH:mm:ss`、`MM-dd HH:mm:ss`。`lineChart.latestWindowStartTime` 和 `lineChart.latestWindowEndTime` 返回最新点的完整本地起止时间，区间左闭右开。例如10:07:03查询近3小时，最新点统计 `[10:01:50, 10:06:50)`。没有数据的区间保留0；暂无任何统计记录时，时间轴仍返回，`series` 为空数组。

最新滚动区间可能与历史区间重叠，不能相加曲线值作为总量。卡片和表格仍按查询时刻向前3小时、24小时或7天的原始记录独立汇总，不扣除曲线的落库缓冲。历史首点使用完整固定区间，必要时补查查询边界前的数据，该部分不计入卡片和表格。

## 4. 用户管理

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/system/users` | 用户列表 | 无 |
| GET | `/system/users/{id}` | 用户详情 | 路径：`id` |
| POST | `/system/users/create` | 新增用户 | `UserSaveRequest` |
| POST | `/system/users/update` | 修改用户 | `UserSaveRequest`，`id` 必填 |
| POST | `/system/users/delete` | 删除用户 | JSON：`id` |
| POST | `/system/users/status` | 启用或禁用用户 | JSON：`id`、`status`（0 禁用/1 启用） |
| POST | `/system/users/reset-password` | 重置密码 | JSON：`id`、`password` |

`UserSaveRequest`：`id`、`username`、`password`、`nickname`、`phone`、`email`、`status`（0 停用/1 启用）、`roleId`。

修改用户支持保存登录用户名 `username`：未传时保留原值；传入时去除首尾空格，不能为空或超过64个字符，不能与其他用户重名。管理员用户名不能修改，其他账号不能改名为 `admin`（不区分大小写）。修改成功后接口返回新用户名，后续使用新用户名和原密码登录；用户编号和基线平台绑定保持不变。

用户列表和详情返回 `phone`、`email`、`baselineUserId`、`roleId`、`roleName`、`roleKey`，不会返回密码。新增或修改用户传入 `roleId` 时，服务会校验角色存在且启用；删除用户会同步清理用户角色关系。用户名为 `admin` 的本地管理员账号不能删除、停用或解除超级管理员关系。

### 4.1 角色与菜单权限管理

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/system/roles` | 角色列表 | 无 |
| GET | `/system/roles/{id}` | 角色详情 | 路径：`id` |
| POST | `/system/roles/create` | 新增角色 | `roleName`、`roleKey`、`status` |
| POST | `/system/roles/update` | 修改角色 | `id`、`roleName`、`roleKey`、`status` |
| POST | `/system/roles/delete` | 删除角色 | JSON：`id` |
| POST | `/system/roles/status` | 启用或停用角色 | JSON：`id`、`status`（0/1） |
| GET | `/system/menus` | 查询可分配的菜单 | 无 |
| GET | `/system/roles/{id}/menus` | 查询角色已分配的菜单 ID | 路径：`id` |
| POST | `/system/roles/menus` | 重新分配角色可访问菜单 | JSON：`roleId`、`menuIds` |

菜单权限采用 `dp_user → dp_user_role → dp_role → dp_role_menu → dp_menu`。后端业务接口只验证登录状态，不校验按钮或接口权限。超级管理员角色不能删除、停用或修改 `admin` 标识；已经分配给用户的角色不能删除。

### 4.2 菜单来源与角色绑定

当前左侧菜单统一预置在 `sql/system.sql` 中，执行脚本后写入 `dp_menu` 表；系统暂未提供菜单新增、修改、删除接口。`GET /system/menus` 仅用于读取可分配的菜单节点。分组菜单通过 `parent_id` 组织子菜单，叶子菜单通过 `path` 指向页面，`hidden` 控制是否在左侧显示。

页面在初始化脚本中的层级为：

```text
总览（ID 10，C）
数据可视化（ID 30，C）
数据采集（ID 20，C）
卫星管理（ID 35，C）
数据处理（ID 40，C）
数据校准（ID 50，C）
告警管理（ID 60，C）
系统配置（ID 1，M）
├── 用户管理（ID 2，C）
├── 角色管理（ID 7，C）
└── 日志管理（ID 11，C）
```

给角色授权时，前端先调用 `GET /system/menus` 获取全部节点，再调用 `GET /system/roles/{id}/menus` 获取已勾选 ID，最后提交：

```http
POST /system/roles/menus
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "roleId": 2,
  "menuIds": [10, 20, 40, 50]
}
```

保存时会先删除该角色在 `dp_role_menu` 中的原关联，再写入本次提交的页面 ID；选择子页面时会自动补齐父目录。超级管理员 `admin` 始终拥有全部有效页面，因此不允许通过此接口重新分配页面。

## 5. 数据采集

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/collection/overview` | 采集总览 | 查询：`taskId`（必填） |
| GET | `/collection/interfaces` | 分页查询采集接口 | 查询：`taskId?`、`interfaceType?`、`transferType?`、`status?`、`enabled?`、`pageNum=1`、`pageSize=10` |
| GET | `/collection/interfaces/{id}` | 采集接口详情 | 路径：`id`；返回 `protocolConfigName` 用于展示，未关联或协议已删除时为 `null`，禁用协议仍返回名称 |
| POST | `/collection/interfaces/create` | 新增采集接口 | `InterfaceSaveRequest` |
| POST | `/collection/interfaces/update` | 修改采集接口 | `InterfaceSaveRequest`，`id` 必填 |
| POST | `/collection/interfaces/delete` | 删除采集接口 | JSON：`id` |
| POST | `/collection/interfaces/enabled` | 启用/禁用采集接口 | JSON：`id`、`enabled`（0/1） |
| GET | `/collection/protocols` | 分页查询协议配置 | 查询：`taskId?`、`protocolType?`、`configName?`（名称模糊匹配）、`enabled?`（0禁用1启用）、`pageNum=1`、`pageSize=10`（最大100） |
| GET | `/collection/protocols/{id}` | 协议配置详情 | 路径：`id` |
| POST | `/collection/protocols/create` | 新增协议配置 | `ProtocolSaveRequest` |
| POST | `/collection/protocols/update` | 修改协议配置 | `ProtocolSaveRequest`，`id` 必填 |
| POST | `/collection/protocols/delete` | 删除协议配置；存在未删除采集接口绑定时禁止删除，提示绑定接口名称，不区分启用状态 | JSON：`id` |
| POST | `/collection/protocols/enabled` | 启用/禁用协议配置 | JSON：`id`、`enabled`（0/1） |

采集事件列表中的 `logLevel` 返回稳定枚举编码，`logLevelName` 返回对应中文名称（调试、信息、警告、错误）。

`InterfaceSaveRequest`：`id`、`taskId`、`interfaceName`、`interfaceType`、`sendFrom`、`messageContent`、`transferType`、`transferProtocol`、`protocolConfigId`、`host`、`port`、`receivePath`、`enabled`、`rpcEnabled`、`remark`。其中 `transferProtocol` 为整数枚举：1 JSON、2 PDXP、3 FEP；`port` 范围为 1～65535；`rpcEnabled` 为 0 时使用本地处理，为 1 时进入 RPC 处理流程。FEP（`transferProtocol=3`）无需传 `rpcEnabled` 和 `protocolConfigId`，后端固定保存为 `0` 和 `null`，切换到FEP或提交旧值时也会清理。HTTP（`transferType=3`）固定JSON传输协议，必须传当前任务下已启用JSON配置的 `protocolConfigId` 和接收路径 `receivePath`；处理开关固定为0，组播地址保存为空，路径在所有未删除HTTP配置中唯一。列表和详情计算返回 `receiveUrl`，访问协议读取 `data-processing.http.scheme`，默认http。HTTP统一入口 `POST /collection/receive/**` 已提供，当前只解析JSON并确认收到请求，不关联采集配置、不校验任务或启用状态、不处理业务、不入库、不统计。其他非HTTP、非FEP协议的两种处理方式都必须传 `protocolConfigId`：处理（`rpcEnabled=0`）只能选 PDXP（`protocolType=2`），转发（`rpcEnabled=1`）只能选 RPC（`protocolType=4`），类型不匹配时后端拒绝保存。采集接口不再接收或返回 `sheetName`。

采集接口新增、修改不再接收 `status`，前端只提交 `enabled`；未传 `enabled` 时沿用默认启用。列表和详情返回的 `status`、`statusName` 完全由启用开关决定：`enabled=1` 返回 `status=1`、`statusName=在线`，禁用返回 `status=0`、`statusName=离线`，不提供异常状态。列表的可选 `status` 筛选仅接受 `0`、`1`，实际按 `enabled` 查询，`0` 不会被忽略。`/collection/findData`、`/dashboard/findData` 的状态枚举同步为离线、在线。首页采集总览的在线接口数量按启用接口统计，发送方聚合行有任一启用接口即显示在线、全部禁用显示离线；Redis中的历史状态不影响这些结果。数据库 `status` 列保留兼容，旧记录无需迁移。

`ProtocolSaveRequest`：`id`、`taskId`、`configName`、`protocolType`（1 JSON/2 PDXP/3 Protobuf/4 RPC）、`configDesc`、`configParams`、`parserClass`、`enabled`。数据源分类已删除，不提交或返回 `dataSourceType`、`dataSourceTypeName`；`configParams` 为对象，前端普通多行文本框填写标准JSON，解析后提交对象，编辑时格式化回填。

协议配置列表返回 `data.pageNum`、`data.pageSize`、`data.total`、`data.records`，不再直接返回数组；名称按包含关系模糊匹配，任务、类型、名称、启用状态条件可组合使用，未填或空白的文本条件不参与筛选，按主键倒序返回。页码必须大于等于1，每页条数为1～100；超出末页返回空记录并保留总数。

`protocolType=2` 时，PDXP配置提交当前任务下的 `configParams.deviceSatelliteId`，可保留 `pdxphead`；不提交 `fields`，参数解析配置由后端按设备加载。

```json
{
  "deviceSatelliteId": 12,
  "pdxphead": {
    "ver": null,
    "mid": null,
    "sid": null,
    "did": null,
    "bid": null,
    "no": null,
    "flag": null,
    "res": null
  }
}
```

FEP文件在本地接收完整后先返回结束确认包，再上传对象存储并发送文件完成消息。临时目录、数据单元长度、对象存储连接和消息主题统一通过配置中心管理。

文件完成消息示例：

```json
{
  "requirementId": "",
  "requirementName": "",
  "requirementDesc": "",
  "version": "",
  "fileUrl": "http://192.168.2.4:9002/ekbs/2026/09/02/b14cfe8d4d9d4f668f8f5bcaafb0aa4b.docx",
  "baseInfo": {
    "businessName": "",
    "senderId": "",
    "senderName": "",
    "receiverId": "",
    "receiverName": "",
    "time": "2026-09-02 16:35:28.123"
  }
}
```

PDXP配置选择设备卫星并保留可选的`pdxphead`，参数字段来自设备的参数解析配置。

`protocolType=4`时，RPC动态配置使用`configParams.fields`，不提交`deviceSatelliteId`，无需导入本地参数文件或选择设备卫星：

```json
{
  "fields": [
    {
      "field": "sat_code",
      "fieldName": "卫星代号",
      "value": "SAT-001"
    },
    {
      "field": "channel",
      "fieldName": "通道代号",
      "value": "CH-01"
    },
    {
      "field": "data_source",
      "fieldName": "数据源",
      "value": "telemetry"
    }
  ]
}
```

RPC字段属性仅包含`field`（标识）、`fieldName`（中文名称）、`value`（配置值）；移除`dataType`。前端使用普通大文本框填写完整JSON对象，其中RPC业务字段为卫星代号`sat_code`、通道代号`channel`、数据源`data_source`，支持额外自定义字段；标识应保持唯一。自定义字段可保存和回显，当前不会发送给远程RPC服务。历史`dataType`在读取时忽略，在查询响应和后续保存时移除。`data`、`sequence`、`send_time`和`length`由每帧PDXP数据动态组装，不放入`configParams`。

## 6. 数据可视化

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/visualization/config/export` | 下载当前用户整页JSON配置文件 | 查询：`taskId`（必填） |
| POST | `/visualization/config/import/preview` | 只读检查配置及目标任务关联 | 查询：`taskId`（必填）；表单：`file` |
| POST | `/visualization/config/import` | 重新检查并事务替换当前用户整页配置 | 查询：`taskId`（必填）；表单：`file` |
| GET | `/visualization/widgets` | 当前用户组件列表 | 查询：`taskId`（必填） |
| GET | `/visualization/widgets/{id}` | 组件详情 | 路径：`id` |
| POST | `/visualization/widgets/create` | 新增组件 | `WidgetSaveRequest` |
| POST | `/visualization/widgets/update` | 修改组件 | `WidgetSaveRequest`，`id` 必填 |
| POST | `/visualization/widgets/delete` | 删除组件及数据项 | JSON：`id` |
| GET | `/visualization/widgets/{widgetId}/items` | 组件参数筛选项列表（含勾选状态） | 路径：`widgetId` |
| GET | `/visualization/items/{id}` | 数据项详情 | 路径：`id` |
| POST | `/visualization/items/create` | 新增数据项 | `WidgetItemSaveRequest` |
| POST | `/visualization/items/update` | 修改数据项 | `WidgetItemSaveRequest`，`id` 必填 |
| POST | `/visualization/items/delete` | 删除数据项 | JSON：`id` |
| POST | `/visualization/items/selected` | 修改勾选状态 | JSON：`id`、`status`（0/1） |

`WidgetSaveRequest`：`id`、`taskId`、`widgetKey`、`widgetType`（1 实时曲线/2 实时数据/3 实时告警/4 载荷图像/5 文件表格）、`widgetTitle`、`gridX`、`gridY`、`gridWidth`、`gridHeight`、`enabled`。

`WidgetItemSaveRequest`：`id`、`widgetId`、`parseRuleId`、`interfaceId`、`protocolConfigId`、`itemCode`、`itemName`、`isSelected`。`parseRuleId` 对应参数解析配置主键；自动同步产生的筛选项不绑定单一采集接口或协议，`interfaceId`、`protocolConfigId` 可以为空。

配置导入导出：成功导出直接返回JSON附件，不包裹统一响应；配置文件包含`schemaVersion=1`、`exportedAt`及`widgets`。每个组件包含类型、标题、显示状态、网格位置大小、非遥测数据项`items`和已勾选遥测参数`selectedParameters`。每条已勾选参数包含设备卫星类型、编码、名称、遥测代号、名称、系统完整路径以及已有数据项`dataItems`；参数由“设备卫星类型＋设备卫星编码＋遥测代号”匹配，名称和系统路径仅用于核对。中文枚举名称随文件返回，导入忽略中文展示名称，复用现有枚举编码。

检查和导入使用`multipart/form-data`上传同一`.json`文件，最大10MB、最多500个组件，参数勾选和数据项合计最多50000条。检查结果包含`valid`、`widgetCount`、`parameterCount`、`itemCount`、`existingWidgetCount`、`errors`；最多返回100条中文问题。检查不写库；实际导入在任务锁内重新检查，任意参数缺失、接口或协议名称缺失／重名、非法配置均阻止导入。全部通过后同一事务替换当前用户目标任务的组件、数据项和组件勾选，失败整体回滚，其他用户及固定处理后数据页面不受影响。导入生成新组件主键，成功后重新查询组件列表并刷新筛选树；显式`widgets: []`表示清空该用户目标任务页面，组件列表缺失或为空值不允许导入。详细流程见`前端对接-可视化配置导入导出.md`。

## 7. 试验任务

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/experiment/tasks` | 查询调度系统下发的全部试验任务生命周期快照，使用字符串 `taskId` 作为主键 | 无 |

## 8. 数据处理

原始帧与异常帧接口的查询参数为必填 `taskId`、可选 `interfaceId`、`pageNum`、`pageSize`。接口主键必须大于零；对应关系由任务路径隔离，正数但在当前任务下没有数据的接口返回空页。响应仍保留设备卫星、通道、时间、原码及检查结果，不新增 `interfaceId`、`interfaceName`。旧整帧不自动迁移，缺少接口层级时不推断来源，仅在不筛选接口时返回。

`/processing/processed` 和 `/processing/processed/curve` 仍按taskId、selectionType、targetId读取已保存勾选；实际IoTDB查询按勾选所属类型分支：设备使用当前任务、设备主键与遥测代号，卫星使用当前任务、rpc_前缀身份与遥测代号。类型复用DeviceSatelliteType枚举并从已有设备卫星资料读取；卫星资料主键不用于拼接RPC存储路径。同一代号在各自范围内存在多个路径时按时间取最新一条或最近120个有效点。勾选目录、保存机制和返回结构保持现有约定。完整前端改动见[遥测代号查询与整帧接口筛选](前端对接-遥测代号查询与整帧接口筛选.md)。

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/processing/overview` | 今日处理量和累计去重总览 | 查询：`taskId`（必填） |
| GET | `/processing/realtime` | 当前任务所有接口PDXP帧处理速率之和、与系统告警同口径的机器CPU及物理内存使用率 | 查询：`taskId`（必填） |
| GET | `/processing/rule-config` | 查询处理规则开关 | 查询：`taskId`（必填） |
| POST | `/processing/rule-config/update` | 保存处理规则开关 | `ProcessingRuleConfigRequest` |
| GET | `/processing/device-satellites` | 设备卫星筛选列表 | `taskId` 必填，`type` 可选；类型为空时查询全部；返回 `id`、`code`、`name`、`type` |
| GET | `/processing/rules` | 分页查询遥测解析规则 | 必填：`taskId`、`deviceSatelliteId`；可选：`systemId`、`pageNum`、`pageSize`；传入系统主键时只查询该系统直属参数，不传时查询当前设备全部参数 |
| POST | `/processing/rules/import` | Excel或TXT按类型替换导入 | `multipart/form-data`：`taskId`、`type`、`file` |

处理日志中的 `logLevel` 返回稳定枚举编码（1 信息、2 警告、3 错误），`logLevelName` 返回对应中文名称。

`ProcessingRuleConfigRequest` 只包含 `taskId` 和 `parameterRangeCheckEnabled`（0关闭、1开启）。查询时如果该任务尚无配置，系统会先插入参数值范围检查关闭的默认配置，再返回数据库记录。数据库表保留其他规则字段，但查询和保存接口不再读取或修改这些字段。

上传.xls、.xlsx或.txt文件（最大20MB），前端选择type，整份文件使用相同类型。Excel按Sheet页签分别创建设备卫星；TXT使用制表符分隔，一次表示一个设备卫星，使用去掉扩展名的文件名填写code和name，并自动兼容UTF-8和GBK编码。第一行必须包含全部19个中文表头：序号、位宽、遥测名称、遥测代号、公式类型、处理公式、处理参数、小数位数、是否报警、正常值范围、预警值范围、状态跳变信息、相关命令、所属系统、控制波道、合并波道、延时波道、存储遥测、校准公式；旧TXT最后一列表头和内容为反斜线时按空校准公式处理。每个设备至少有一条有效数据，空行忽略，序号不能重复。完整校验后在同一事务中逻辑删除当前taskId下同类型设备卫星及关联参数，回填设备卫星主键到参数的deviceSatelliteId。其他任务和另一类型不受影响，任意失败整体回滚。业务单元格按文本保存，是否报警和存储遥测空白时为0。返回导入的参数总条数。

参数解析配置统一通过Excel或制表符TXT导入维护；导入完成后同步实时曲线和实时数据组件的参数筛选项。

## 9. 数据校准

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/calibration/overview` | 查询累计校准、清洗及野值数量 | 查询：`taskId`（必填） |
| GET | `/calibration/channels` | 校准通道列表 | 查询：`taskId` |
| GET | `/calibration/channels/{id}` | 校准通道详情 | 路径：`id` |
| POST | `/calibration/channels/create` | 新增校准通道 | `ChannelSaveRequest` |
| POST | `/calibration/channels/update` | 修改校准通道 | `ChannelSaveRequest`，`id` 必填 |
| POST | `/calibration/channels/enabled` | 切换校准通道启用状态 | JSON：`taskId`、`id`、`enabled`（均必填，0禁用、1启用） |
| POST | `/calibration/channels/delete` | 删除校准通道 | JSON：`id` |
| GET | `/calibration/records` | 分页查询校准记录及累计校准数量 | 查询：`taskId`、`pageNum=1`、`pageSize=20` |
| GET | `/calibration/records/{id}` | 查询记录汇总和分页野值详情 | 路径：`id`；查询：`taskId`、`pageNum=1`、`pageSize=20` |

`ChannelSaveRequest`：`id`、`taskId`、`channelName`、`calibrationFormula`（校准公式，与每个遥测参数的 calibration_formula 对应）、`normalRange`（仅阈值法必填，其他算法可不填；填写时校验格式）、`detectMethod`（1 莱特准则/2 阈值法/3 肖维涅法）、`sigmaValue`（莱特准则σ倍数，1至5）、`fluctuationRate`（阈值百分比，大于0且不超过100）、`chauvenetCoef`（肖维涅判别常数，固定0.5）、`minSampleCount`（肖维涅法参与计算的最近样本数，不小于3）、`iterateCount`（肖维涅法迭代次数，1至3）、`sampleWindow`（莱特准则最近样本窗口，不小于3）、`dynamicUpdate`、`autoClean`（是否自动排除异常值）、`enabled`。只校验当前检测方法对应的专用参数。修改时不传正常范围保留原值；非阈值法传空字符串可清空正常范围。

校准通道不再接收或返回 `channelStatus`、`channelStatusName`，枚举接口也不再返回通道状态选项。新增和修改仍支持 `enabled`；独立启用切换接口仅操作当前任务下未删除的通道，事务提交后通知处理模块刷新校准Map，同步成功后无需重启采集接口。禁用通道后保留历史记录，后续帧跳过该通道；野值检测还需开启任务的 `parameterRangeCheckEnabled`。

`/calibration/overview` 返回 `calibrationCount`（累计参与校准的遥测参数数量，包含正常值和野值）、`cleanedCount`（清洗数量，与 `outlierCount` 完全一致）及 `outlierCount`（累计检出的野值数量）。三项均按 `taskId` 汇总未删除的校准记录，无记录时返回 0。参数只有匹配启用的校准通道并完成校准处理后才计数；未匹配通道、未开启 `parameterRangeCheckEnabled` 或校准处理失败的参数不计数，同一参数每次参与校准分别累计。详情或样本过期清理不减少汇总数量。

新增校准通道时自动创建一条校准数量和野值数量均为 0 的校准记录。通道与记录一对一，野值详情与记录一对多；记录由数据处理流程维护，不提供手工新增、修改和删除接口。校准数量先按每条消息的通道合并后写入 Redis 队列，处理服务在同一事务内批量保存野值详情、累加野值数量及校准数量。默认每轮落库结束后等待 5 秒再处理下一轮，每个队列每轮最多取 500 条；数据库或反序列化失败会恢复队列等待重试。总览读取数据库，存在批量落库延迟，队列积压或重试时延迟会增加。

已有数据库上线前先执行 `sql/upgrade-calibration-count.sql`，再更新管理服务及处理服务；新建数据库使用更新后的 `sql/system.sql`。历史数据未保存完整校准总量，`calibrationCount` 从升级后开始累计，不根据野值数量补算；历史 `outlierCount` 保持原值，`cleanedCount` 复用该值。重复执行升级脚本不会重置已有校准数量。

`/calibration/records` 的 `data.records[]` 每条记录增加 `calibrationCount`，表示该通道累计参与校准的遥测参数数量，复用总览使用的 `calib_record.calibration_count`，无累计数量时返回 0。前端列表“校准数量”列绑定 `row.calibrationCount`，按整数展示；该字段具有相同的落库延迟及升级后的累计口径。本次仅列表增加字段，`/calibration/records/{id}` 的详情响应维持原结构，不增加 `calibrationCount`。

## 10. 告警管理

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/alarms/system-monitor/current` | 当前系统健康状态 | 无参数 |
| GET | `/alarms/system-monitor/history` | 系统健康趋势 | 查询：`minutes=60` |
| GET | `/alarms/data` | 组合筛选并分页查询数据告警 | 查询：`taskId?`、`sourceCode?`、`keyword?`、`alarmLevel?`、`alarmStatus?`、`startTime?`、`endTime?`、`pageNum=1`、`pageSize=20` |
| GET | `/alarms/findData` | 告警来源、类型、级别和状态等枚举选项 | 无 |
| GET | `/alarms/data/{id}` | 数据告警详情 | 路径：`id` |
| POST | `/alarms/data/status` | 标记数据告警人工处理状态 | JSON：`id`、`status`（0/1） |
| GET | `/alarms/system` | 唯一系统告警列表 | 查询：`status?`（0未恢复、1已恢复） |

数据告警来源固定为 `COLLECTION` 数据采集、`PROCESSING` 数据处理、`CALIBRATION` 数据校准。`/alarms/findData` 中 `dataAlarmSource` 返回英文 `value` 和中文 `label`；来源筛选使用 `sourceCode`，不接受中文名称。数据库沿用 `source_name` 列保存英文编码，查询返回 `sourceCode` 编码和 `sourceName` 中文名称。旧数据不迁移。列表和详情新增 `eventType`、`eventTypeName`、`interfaceId`、`processTaskId`、`calibChannelId`、`tmSymbol`、`parameterName`、`alarmDetail`、`triggerCount`、`lastTriggerTime`、`recoverTime`。人工 `alarmStatus` 与自动 `recoverTime` 分开维护。数据告警只由后台自动录入，不提供人工创建、内容修改和删除接口；`POST /alarms/data/status` 保留，`status=0` 表示未处理、`status=1` 表示已处理，操作会写入系统日志。

数据告警列表的 `sourceCode` 为来源编码精确匹配；`keyword` 会模糊匹配来源、告警信息、任务名称、通道编码和触发规则；时间范围使用 ISO 8601 格式并包含起止时间。

系统告警类型 `alarmType` 保存监测指标英文编码，`alarmTypeName` 返回中文名称，类型选项从 `/alarms/findData` 的 `systemAlarmType` 获取。系统告警只由后台自动写入，统一保存到 `sys_alarm_event`，不开放人工创建、修改、删除及恢复接口。列表保留原字段，并返回指标编码/中文名称、当前值、阈值、首次和最近触发时间、恢复时间及触发次数。

## 11. 系统日志

| 方法 | 地址 | 说明 | 参数 |
| --- | --- | --- | --- |
| GET | `/system/logs` | 组合筛选并分页查询系统日志 | 查询：`taskId?`、`logLevel?`、`logSource?`、`operatorId?`、`keyword?`、`startTime?`、`endTime?`、`pageNum=1`、`pageSize=20` |
| GET | `/system/logs/findData` | 日志级别与来源枚举选项 | 无 |
| GET | `/system/logs/{id}` | 系统日志详情 | 路径：`id` |

查询参数 `logLevel` 使用枚举编码：`INFO` 信息、`WARN` 警告、`ERROR` 错误。日志列表和详情同时返回 `logLevel` 编码与 `logLevelName` 中文名称，前端可直接展示中文名称。`logSource` 使用 `/system/logs/findData` 返回的选项英文 `value` 精确匹配来源，不接受中文名称，`operatorId` 模糊匹配操作人，`keyword` 模糊匹配日志内容和详情；时间范围使用 ISO 8601 格式并包含起止时间。

`/system/logs/findData` 同时返回 `logLevel` 和 `logSource`。来源统一由 `SystemLogSource` 枚举维护，人工操作引用同一个来源枚举，`value` 为英文编码、`label` 为中文名称；与任务无关，即使尚无日志也返回完整来源选项。日志表 `log_source` 保存英文编码，列表和详情返回 `logSource` 编码和 `logSourceName` 中文名称；旧数据不迁移。

系统日志页面只开放查询接口。日志新增由后台模块通过 `OperationLogService` 写入，不开放页面新增、修改或删除接口。

## 12. 调试建议

### 遥测整帧异常记录

本地处理新写入的IoTDB整帧路径为 `root.db.task_<任务节点>.interface_<采集接口ID>.device_<设备卫星ID>.tms.<遥测类型>._frame`，只保存 `deviceSatelliteName`、`channelName`、`raw`、`checkResultName` 四个测点；raw保存当前完整采集帧，顺序为传输头、PDXP包头、自定义数据、遥测数据、遥测帧头，时间使用帧时间。参数节点的raw仍保存参数原码，Proto的frame_raw_data仍保留去头数据域定义。成功生成Proto时，RPC返回的 `frame_check_status=false` 对应 `checkResultName=异常`，返回true时为正常；本地成功解析的结果为正常。参数越限和校准野值不改变整帧检查结果。

RPC整帧使用 `root.db.task_<任务节点>.interface_<采集接口ID>.rpc_<卫星通道身份节点>.tms.<遥测类型>._frame`，卫星通道身份由RPC返回编码生成。参数路径继续位于任务下原有设备或RPC身份节点，不增加接口层级。整帧查询改用可选interfaceId筛选，不再接收deviceSatelliteId作为筛选条件；不传interfaceId时包含当前任务新旧路径的本地与RPC整帧，传入时只查该interface_节点；RPC记录的 `deviceSatelliteId` 返回 `null`，`deviceSatelliteName` 为返回的卫星名称或代号。历史RPC设备节点数据仍保留，不自动迁移。

本地参数解析失败或未生成结果时，若PDXP包和数据域已正确拆分，并且采集配置具有有效任务编号、设备卫星ID及“通道名称_业务标识”格式的设备名称，则允许没有Proto而保存异常整帧。设备名称来自运行配置，通道名称取首个下划线前的内容，原码保存包含传输头、PDXP包头、自定义数据和遥测帧头的当前完整采集帧，类型节点为 `device`，时间使用PDXP包头发送时间。该分支复用遥测入库线程池，只写完整异常 `_frame`，不写任何参数，不发布遥测消息，不计入成功处理量。身份不完整、无法拆分数据域、RPC调用失败及校准失败不使用此回退；入库队列满或写入失败会保留原始解析错误并另外记录存储告警。

`GET /processing/realtime/telemetry` 可查询正常和异常整帧，`GET /processing/realtime/invalid` 仍按 `checkResultName != '正常'` 查询异常记录。两个接口返回字段不增加，rawLength与rawFrame分别展示完整帧字节数和完整帧十六进制原码；旧记录不会重新补头。`checkResultName` 返回中文“正常”或“异常”，旧数据中的其他非正常名称仍可被异常接口筛选。RPC异常Proto已有的参数入库和消息发布流程保持原有行为。

### 采集传输协议编号升级

`transferProtocol` 统一使用 `1 JSON、2 PDXP、3 FEP`。管理服务的传输协议枚举不再返回 Protobuf，创建、修改采集接口不再接受值 `4`；数据处理服务的 FEP 接收分支和模拟源管理服务都使用值 `3`。模拟源仅支持 `2 PDXP、3 FEP`。协议配置的 `protocolType` 是独立字段，保持原有定义。

已有环境先停止相关采集与模拟源任务，再执行下面的升级语句，最后重启管理、数据处理及模拟源管理服务。新环境直接使用已更新的建表脚本。以下只把旧 FEP 值 `4` 改为 `3`；旧值 `3` 原来表示 Protobuf，若已有这类采集接口，需根据实际用途重新选择有效协议，不能直接当作 FEP 使用。

采集接口表升级：

```sql
BEGIN;
-- 将已有FEP采集接口的传输协议编号统一为3，包括逻辑删除的配置。
UPDATE collect_interface_config
SET transfer_protocol = 3
WHERE transfer_protocol = 4;
-- 同步字段描述。
COMMENT ON COLUMN collect_interface_config.transfer_protocol IS '传输协议：1JSON 2PDXP 3FEP';
COMMIT;
```

已安装模拟源管理模块的环境还需执行以下语句。约束名称对应原 `sql/data-simulator-service.sql` 创建的表；未安装该模块时无需执行此段。

```sql
BEGIN;
-- 先移除旧编号约束，允许将4更新为3。
ALTER TABLE simulator_source_config
    DROP CONSTRAINT IF EXISTS simulator_source_config_transfer_protocol_check;
ALTER TABLE simulator_source_config
    DROP CONSTRAINT IF EXISTS simulator_source_config_check;
-- 更新已有FEP模拟源配置。
UPDATE simulator_source_config
SET transfer_protocol = 3
WHERE transfer_protocol = 4;
-- 恢复协议编号及协议与传输方式的约束。
ALTER TABLE simulator_source_config
    ADD CONSTRAINT simulator_source_config_transfer_protocol_check
    CHECK (transfer_protocol IN (2, 3));
ALTER TABLE simulator_source_config
    ADD CONSTRAINT simulator_source_config_check
    CHECK ((transfer_protocol = 2 AND transfer_type = 1)
        OR (transfer_protocol = 3 AND transfer_type = 2));
-- 同步字段描述。
COMMENT ON COLUMN simulator_source_config.transfer_protocol IS '传输协议：2为PDXP，3为FEP';
COMMIT;
```

Apifox、Postman 或其他支持 OpenAPI 3.0 的工具可直接导入同目录下的 `apifox-openapi.yaml`。登录后，将返回的 Token 配置为 Bearer Token 即可调用其他接口。

OpenAPI 文件已为各模块请求 DTO 配置独立 Schema，包括字段类型、枚举、说明和示例值；导入 Apifox 后可直接生成请求参数表与 JSON 示例。

设备卫星表和参数解析配置表的建表定义统一维护在 `sql/business_schema.sql` 中。

告警与人工操作日志的触发范围、合并规则、遥测筛选详情及发布步骤见 [告警与操作日志说明](告警与操作日志说明.md)。已有数据库发布前执行 `sql/upgrade_alarm_operation_log.sql`，不迁移旧表数据。

## 数据保留与过期清理

保留天数统一使用Nacos中的 `data-retention` 配置，不填或留空不删除，填写正整数N时删除早于当前时间减N天的数据；0和负数不合法。管理服务按分类分批物理删除金仓记录，处理服务删除IoTDB中同一截止时间之前的参数和整帧测点，每小时第15分钟执行。原固定7天清理不再使用。

采集接口累计量及处理累计去重数按仍保留的统计记录计算，清理后可减少。遥测参数曲线仍查询最近120条已有数据，查询 `range` 与保留天数互相独立。校准只删除野值详情，保留汇总记录、ID和历史累计数量；详情分页total是当前保留的详情条数，允许小于汇总的outlierCount。文件图片只清理数据库记录，不删除MinIO对象；超期未完成的记录也删除，之后不再被数据库重试扫描选中。系统告警和数据告警包含已处理、未处理或仍活动的过期记录。配置项、配置所属服务和时间依据详见 `docs/数据保留与过期清理配置说明.md`。

## PDXP接收附加长度配置

处理服务通过 `data-processing.pdxp.transport-header-length`、`custom-data-length`、`telemetry-header-length` 统一读取传输头、数据域开头自定义数据和数据域末尾遥测帧头的字节数，默认分别为2、5、4，0表示没有。PDXP标准包头仍固定32字节，遥测数据按实际长度读取。本地处理只解析中间遥测数据；RPC请求data为遥测帧头加遥测数据；IoTDB整帧raw保留实际完整接收帧，异常帧校验复用相同长度配置。配置适用于所有PDXP采集接口，修改后重启处理服务生效，模拟源发送格式应匹配。接口入参和出参字段不变，详见 `docs/PDXP附加头长度配置说明.md`。


## HTTP采集统一接收入口

数据处理服务提供 `POST /collection/receive/**`，默认服务地址为 `http://localhost:8083`。前缀下所有路径由同一个Controller接收JSON并返回 `{ "code": 200, "message": "操作成功" }`，没有data字段。请求头使用application/json，鉴权沿用现有开关与白名单。

当前不查询采集配置，不检查任务或启用状态，不解析业务、不入库、不统计。空请求体或无法解析的JSON返回400，非POST返回405，内容类型不支持返回415。完整前端联调说明见[前端对接文档](前端对接-HTTP采集与协议配置变更.md)。
