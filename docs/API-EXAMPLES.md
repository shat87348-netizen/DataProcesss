# 接口入参与出参示例

本文档与 `API.md` 配套使用，示例依据当前源码中的 Controller、DTO、Entity 和 VO 编写。

## 阅读说明

可视化配置导入导出新增三个接口：`GET /visualization/config/export`、`POST /visualization/config/import/preview`和`POST /visualization/config/import`。导出成功返回JSON附件本身，检查和导入按`multipart/form-data`上传`file`并通过查询参数提供`taskId`，成功结果使用统一响应。完整配置文件、检查结果及前端调用流程见[可视化配置导入导出对接](前端对接-可视化配置导入导出.md)。

除登录接口外，请求都需携带：

```http
Authorization: Bearer <token>
```

所有出参示例均展示后端实际的统一响应结构：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": {} // 接口返回的业务数据
}
```

没有业务数据时，`data` 为 `null`，由于 Jackson 配置会省略空字段，
实际响应只包含 `code` 和 `message`。

示例代码使用 JSONC 格式，每个字段后通过 `//` 标注字段含义；枚举字段同时列出全部枚举值。实际调用接口时，请去掉 `//` 后面的注释并发送标准 JSON。
字段备注和枚举值也已在 `apifox-openapi.yaml` 的请求、响应 Schema 中逐项定义。

## 总览

### `GET /dashboard/collection?taskId=TASK-20260908-001&range=3h`

```jsonc
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "taskId": "TASK-20260908-001", // 外部试验任务编号
    "range": "3h", // 查询时间范围
    "sampleTime": "2026-08-27T10:30:01", // Redis实时指标最后更新时间
    "summary": { "onlineAgentCount": 6, "senderCount": 7, "collectionCount": 12500, "interfaceRateKbps": 7.81 },
    "lineChart": { "timePoints": ["10:00"], "series": [{ "systemName": "实验综合控制系统", "values": [12500] }] },
    "table": [{ "systemName": "实验综合控制系统", "interfaceCount": 3, "protocols": ["UDP", "TCP"], "collectionCount": 12500, "percentage": 100.0, "interfaceRateKbps": 7.81, "statusName": "在线" }]
  }
}
```

### `GET /dashboard/collection?taskId=TASK-20260908-001&range=24h`

返回结构与近3小时查询一致，`range` 返回 `24h`，折线图按30分钟聚合近24小时数据。

### `GET /dashboard/processing?taskId=TASK-20260908-001&range=3h`

返回 `taskId`、`range`、`sampleTime`、`summary`、`lineChart` 和 `table`；处理曲线和处理表格均按发送方系统名称分类。

```jsonc
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "taskId": "TASK-20260908-001", // 外部试验任务编号
    "range": "3h", // 查询时间范围
    "sampleTime": "2026-08-27T10:30:01", // Redis实时指标最后更新时间
    "summary": { "unhandledAlarmCount": 30, "processedCount": 12480, "abnormalCount": 20 },
    "lineChart": { "timePoints": ["10:00"], "series": [{ "systemName": "实验综合控制系统", "values": [12480] }] },
    "table": [{ "systemName": "实验综合控制系统", "processedCount": 12480, "deduplicatedCount": 8, "abnormalCount": 20 }]
  }
}
```

### `GET /dashboard/processing?taskId=TASK-20260908-001&range=24h`

返回结构与近3小时查询一致，`range` 返回 `24h`，折线图按30分钟聚合近24小时数据。

## 1. 登录认证

### 浏览器跨域预检

两个服务的 Nacos 配置均通过以下配置允许任意前端来源：

```yaml
auth:
  # 允许任意前端来源跨域访问。
  allowed-origin-patterns:
    - "*"
```

将仓库中的配置项合并到线上 Nacos 对应的 `data-admin-service.yaml`、`data-process-service.yaml`，保留其他配置，并重启对应服务，使跨域过滤器加载新配置。

以登录接口为例，浏览器发送的预检请求如下，`Origin` 请替换为实际前端页面的协议、主机和端口：

```http
OPTIONS /auth/login HTTP/1.1
Host: 192.168.112.55:8082
Origin: http://192.168.112.55:5173
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type
```

预期响应状态为 `200`，响应头 `Access-Control-Allow-Origin` 为 `http://192.168.112.55:5173`，`Access-Control-Allow-Methods` 包含 `POST`，`Access-Control-Allow-Headers` 包含 `content-type`，`Access-Control-Allow-Credentials` 为 `true`。预检通过后，浏览器才会发送正式登录请求。Apifox 可手动发送上述预检请求检查响应头。

### `POST /auth/login`

入参：

```jsonc
{
  "username": "admin", // 登录用户名
  "password": "admin123" // 登录密码
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "token": "eyJhbGciOiJIUzI1NiJ9...", // 登录成功后签发的 JWT 访问令牌
    "tokenType": "Bearer", // 访问令牌类型。枚举：Bearer Bearer 令牌
    "expiresIn": 0 // 0表示访问令牌永不过期
  }
}
```

### `POST /auth/sso/login`

入参：

```jsonc
{
  "clientId": "SJJH_CLIENT_ID", // 可选；传入时必须与服务端配置一致
  "tokenId": "BASELINE_TOKEN_ID", // 基线平台签发的登录Token
  "remoteIp": "127.0.0.1" // 可选；原样传给基线平台校验接口
}
```

后台校验平台 Token 后同步本地用户和角色，再返回与本地密码登录相同的本系统 JWT。角色只按平台用户的 `roleIds` 匹配，不检查 `systemPermissions`。普通用户首次登录时自动创建同名账号和八位随机密码；平台 `admin` 固定复用本地 `admin` 和超级管理员角色。

出参：

```jsonc
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 0
  }
}
```

### `POST /auth/logout`

入参：

无。

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `GET /auth/user-info`

入参：

无。

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "user": { // 当前登录用户信息
      "id": 1, // 业务数据主键 ID
      "username": "admin", // 登录用户名
      "nickname": "管理员", // 用户昵称
      "phone": "13800000000", // 联系电话
      "email": "admin@example.com", // 联系邮箱
      "status": 1 // 启用状态。枚举：0 停用，1 启用
    },
    "roles": [ // 当前用户拥有的角色标识集合
      "admin"
    ]
  }
}
```

### `GET /auth/routes`

入参：

无。

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": [ // 接口返回的业务数据
    {
      "label": "系统配置", // 菜单显示名称
      "icon": "setting", // 菜单图标
      "hidden": false, // 是否隐藏菜单
      "children": [ // 子菜单列表
        {
          "label": "用户管理", // 菜单显示名称
          "icon": "user", // 菜单图标
          "path": "/system/user", // 页面访问路径
          "hidden": false // 是否隐藏菜单
        }
      ]
    }
  ]
}
```

## 子系统集成

### `GET /integration/menu-config`

入参：

查询参数：

```text
roleIds=20,21
```

`roleIds` 必填，填写基线平台角色编号；多个编号使用英文逗号分隔。接口无需登录令牌。

出参：

```jsonc
{
  "systemKey": "data-processing", // 子系统唯一标识
  "label": "数据处理软件", // 子系统显示名称
  "icon": "system", // 子系统图标
  "baseUrl": "http://192.168.1.20:8080", // 子系统前端访问根地址
  "defaultPath": "/dashboard", // 默认页面路径
  "origin": "http://192.168.1.20:8080", // iframe消息来源校验地址
  "menus": [ // 从本地dp_menu表生成的完整菜单树
    {
      "label": "总览",
      "icon": "dashboard",
      "path": "/dashboard"
    },
    {
      "label": "系统配置",
      "icon": "setting",
      "children": [
        {
          "label": "用户管理",
          "icon": "user",
          "path": "/system/user"
        }
      ]
    }
  ]
}
```

## 2. 用户管理

### `GET /system/users`

入参：

无。

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": [ // 接口返回的业务数据
    {
      "id": 1, // 业务数据主键 ID
      "username": "admin", // 登录用户名
      "nickname": "管理员", // 用户昵称
      "phone": "13800000000", // 联系电话
      "email": "admin@example.com", // 联系邮箱
      "status": 1, // 启用状态。枚举：0 停用，1 启用
      "deleted": 0, // 逻辑删除标记。枚举：0 未删除，1 已删除
      "createTime": "2026-07-30T09:00:00", // 记录创建时间
      "updateTime": "2026-07-30T09:00:00" // 记录最后更新时间
    }
  ]
}
```

### `GET /system/users/1`

入参：

路径：id=1

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": { // 接口返回的业务数据
    "id": 1, // 业务数据主键 ID
    "username": "admin", // 登录用户名
    "nickname": "管理员", // 用户昵称
    "phone": "13800000000", // 联系电话
    "email": "admin@example.com", // 联系邮箱
    "status": 1, // 服务健康状态。枚举：UP 正常，DOWN 不可用
    "deleted": 0, // 逻辑删除标记。枚举：0 未删除，1 已删除
    "createTime": "2026-07-30T09:00:00", // 记录创建时间
    "updateTime": "2026-07-30T09:00:00" // 记录最后更新时间
  }
}
```

### `POST /system/users/create`

入参：

```jsonc
{
  "username": "operator", // 登录用户名
  "password": "Test@123", // 登录密码
  "nickname": "操作员", // 用户昵称
  "phone": "13800000001", // 联系电话
  "email": "operator@example.com", // 联系邮箱
  "status": 1, // 启用状态。枚举：0 停用，1 启用
  "roleId": 2 // 分配给用户或权限关系的角色 ID
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "id": 2, // 业务数据主键 ID
    "username": "operator", // 登录用户名
    "nickname": "操作员", // 用户昵称
    "phone": "13800000001", // 联系电话
    "email": "operator@example.com", // 联系邮箱
    "status": 1 // 启用状态。枚举：0 停用，1 启用
  }
}
```

### `POST /system/users/update`

入参：

```jsonc
{
  "id": 2, // 业务数据主键 ID
  "username": "operator", // 登录用户名
  "nickname": "值班操作员", // 用户昵称
  "phone": "13800000002", // 联系电话
  "email": "operator@example.com", // 联系邮箱
  "status": 1, // 启用状态。枚举：0 停用，1 启用
  "roleId": 2 // 分配给用户或权限关系的角色 ID
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "id": 2, // 业务数据主键 ID
    "username": "operator", // 登录用户名
    "nickname": "值班操作员", // 用户昵称
    "phone": "13800000002", // 联系电话
    "email": "operator@example.com", // 联系邮箱
    "status": 1 // 启用状态。枚举：0 停用，1 启用
  }
}
```

### `POST /system/users/delete`

入参：

```jsonc
{
  "id": 2 // 业务数据主键 ID
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `POST /system/users/status`

当前登录用户不能禁用自己，ID 为 1 的保底管理员也不能禁用。

入参：

```jsonc
{
  "id": 2, // 用户主键
  "status": 0 // 用户状态：0 禁用，1 启用
}
```

出参：

```jsonc
{
  "code": 200,
  "message": "操作成功"
}
```

### `POST /system/users/reset-password`

入参：

```jsonc
{
  "id": 2, // 业务数据主键 ID
  "password": "NewTest@123" // 登录密码
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```


### 2.1 角色与页面权限管理

菜单数据来自 `sql/system.sql` 对 `dp_menu` 的预置，本项目目前没有菜单增删改查接口。以下菜单接口只负责读取菜单以及为角色绑定菜单。

### `GET /system/roles`

入参：

无。

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": [ // 接口返回的业务数据
    {
      "id": 1, // 业务数据主键 ID
      "roleName": "超级管理员", // 角色名称
      "roleKey": "admin", // 本地角色标识
      "status": 1, // 启用状态。枚举：0 停用，1 启用
      "deleted": 0, // 逻辑删除标记。枚举：0 未删除，1 已删除
      "createTime": "2026-07-30T09:00:00" // 记录创建时间
    },
    {
      "id": 2, // 业务数据主键 ID
      "roleName": "操作员", // 角色名称
      "roleKey": "operator", // 本地角色标识
      "status": 1, // 启用状态。枚举：0 停用，1 启用
      "deleted": 0, // 逻辑删除标记。枚举：0 未删除，1 已删除
      "createTime": "2026-07-30T10:00:00" // 记录创建时间
    }
  ]
}
```

### `GET /system/roles/2`

入参：

路径：id=2

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": { // 接口返回的业务数据
    "id": 2, // 业务数据主键 ID
    "roleName": "操作员", // 角色名称
    "roleKey": "operator", // 本地角色标识
    "status": 1, // 启用状态。枚举：0 停用，1 启用
    "deleted": 0, // 逻辑删除标记。枚举：0 未删除，1 已删除
    "createTime": "2026-07-30T10:00:00" // 记录创建时间
  }
}
```

### `POST /system/roles/create`

入参：

```jsonc
{
  "roleName": "操作员", // 角色名称
  "roleKey": "operator", // 本地角色标识
  "status": 1 // 启用状态。枚举：0 停用，1 启用
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "id": 2, // 业务数据主键 ID
    "roleName": "操作员", // 角色名称
    "roleKey": "operator", // 本地角色标识
    "status": 1, // 启用状态。枚举：0 停用，1 启用
    "deleted": 0, // 逻辑删除标记。枚举：0 未删除，1 已删除
    "createTime": "2026-07-30T10:00:00" // 记录创建时间
  }
}
```

### `POST /system/roles/update`

入参：

```jsonc
{
  "id": 2, // 业务数据主键 ID
  "roleName": "值班操作员", // 角色名称
  "roleKey": "operator", // 本地角色标识
  "status": 1 // 启用状态。枚举：0 停用，1 启用
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "id": 2, // 业务数据主键 ID
    "roleName": "值班操作员", // 角色名称
    "roleKey": "operator", // 本地角色标识
    "status": 1, // 启用状态。枚举：0 停用，1 启用
    "deleted": 0, // 逻辑删除标记。枚举：0 未删除，1 已删除
    "createTime": "2026-07-30T10:00:00" // 记录创建时间
  }
}
```

### `POST /system/roles/delete`

入参：

```jsonc
{
  "id": 2 // 业务数据主键 ID
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `POST /system/roles/status`

入参：

```jsonc
{
  "id": 2, // 业务数据主键 ID
  "status": 0 // 启用状态。枚举：0 停用，1 启用
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `GET /system/menus`

入参：

无。

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": [ // 接口返回的业务数据
    {
      "id": 1, // 业务数据主键 ID
      "parentId": 0, // 父级菜单 ID，顶级节点为 0
      "label": "系统配置", // 菜单显示名称
      "icon": "setting", // 前端菜单图标标识
      "path": null, // 页面访问路径，分组菜单可以为空
      "hidden": false, // 是否隐藏菜单
      "sortOrder": 8, // 同级节点显示顺序，数值越小越靠前
    },
    {
      "id": 7, // 业务数据主键 ID
      "parentId": 1, // 父级菜单 ID，顶级节点为 0
      "label": "角色管理", // 菜单显示名称
      "icon": "peoples", // 前端菜单图标标识
      "path": "/system/role", // 页面访问路径
      "hidden": false, // 是否隐藏菜单
      "sortOrder": 2, // 同级节点显示顺序，数值越小越靠前
    }
  ]
}
```

### `GET /system/roles/2/menus`

入参：

路径：id=2

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": [ // 接口返回的业务数据
    1,
    10,
    20,
    40
  ]
}
```

### `POST /system/roles/menus`

入参：

```jsonc
{
  "roleId": 2, // 分配给用户或权限关系的角色 ID
  "menuIds": [ // 分配给角色的目录和页面 ID 集合
    10,
    20,
    40
  ]
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```


## 3. 数据采集

### `GET /collection/overview`

入参：

查询参数：`taskId`，必填，表示外部试验任务编号。

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "interfaceTotal": 6, // 采集接口总数
    "onlineTotal": 5, // 当前已启用采集接口数量
    "todayCollectionCount": 128000 // 今日采集数据总量
  }
}
```

### `GET /collection/interfaces`

入参：

查询参数见 URL

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "pageNum": 1, // 当前页码，从 1 开始
    "pageSize": 10, // 每页记录数量，最大 100 条
    "total": 1, // 符合查询条件的记录总数
    "records": [ // 当前页数据列表
      {
        "id": 11, // 业务数据主键 ID
        "taskId": "TASK-20260908-001", // 外部试验任务编号
        "interfaceName": "遥测 UDP 接口", // 采集接口名称
        "interfaceType": 1, // 接口类型：1外部接口 2内部接口。枚举：1 外部接口，2 内部接口
        "sendFrom": "测控中心", // 数据发送来源
        "messageContent": "实时遥测", // 采集消息内容或格式说明
        "transferType": 1, // 传输方式：1UDP 2TCP 3HTTP。枚举：1 UDP，2 TCP，3 HTTP
        "transferProtocol": 2, // 传输协议：1 JSON、2 PDXP、3 FEP
        "transferProtocolName": "PDXP", // 传输协议名称
        "protocolConfigId": 21, // 本地处理和RPC处理都必须关联协议配置
        "host": "192.168.1.10", // 数据处理服务本机监听网卡IP地址
        "port": 9001, // 数据处理服务本机监听端口
        "multicastIp": "239.1.1.1", // UDP组播地址；为空时按单播接收
        "status": 1, // 由enabled决定的在线状态：0 离线，1 在线
        "statusName": "在线", // 中文在线状态
        "enabled": 1, // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
        "rpcEnabled": 1, // 是否启用RPC处理：0否 1是
        "rpcEnabledName": "是", // 是否启用RPC处理中文名称
        "dataPacketCount": 25600, // 累计接收的数据包数量
        "remark": "主链路" // 备注信息
      }
    ]
  }
}
```

### `GET /collection/interfaces/11`

入参：

路径：id=11

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": { // 接口返回的业务数据
    "id": 11, // 业务数据主键 ID
    "interfaceName": "遥测 UDP 接口", // 采集接口名称
    "interfaceType": 1, // 接口类型：1外部接口 2内部接口。枚举：1 外部接口，2 内部接口
    "sendFrom": "测控中心", // 数据发送来源
    "messageContent": "实时遥测", // 采集消息内容或格式说明
    "transferType": 1, // 传输方式：1UDP 2TCP 3HTTP。枚举：1 UDP，2 TCP，3 HTTP
    "transferProtocol": 2, // 传输协议：1 JSON、2 PDXP、3 FEP
    "transferProtocolName": "PDXP", // 传输协议名称
    "protocolConfigId": 21, // 本地处理和RPC处理都关联的协议配置
    "protocolConfigName": "遥测RPC协议配置", // 详情展示名称，未关联或协议已删除时为空
    "host": "192.168.1.10", // 数据处理服务本机监听网卡IP地址
    "port": 9001, // 数据处理服务本机监听端口
    "multicastIp": "239.1.1.1", // UDP组播地址；为空时按单播接收
    "status": 1, // 由enabled决定的在线状态：0 离线，1 在线
    "statusName": "在线", // 中文在线状态
    "enabled": 1, // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
    "rpcEnabled": 1, // 是否启用RPC处理：0否 1是
    "rpcEnabledName": "是", // 是否启用RPC处理中文名称
    "dataPacketCount": 25600, // 累计接收的数据包数量
    "remark": "主链路" // 备注信息
  }
}
```

### `POST /collection/interfaces/create`

入参：

```jsonc
{
  "taskId": "TASK-20260908-001", // 外部试验任务编号
  "interfaceName": "遥测 UDP 接口", // 采集接口名称
  "interfaceType": 1, // 接口类型：1外部接口 2内部接口。枚举：1 外部接口，2 内部接口
  "sendFrom": "测控中心", // 数据发送来源
  "messageContent": "实时遥测", // 采集消息内容或格式说明
  "transferType": 1, // 传输方式：1UDP 2TCP 3HTTP。枚举：1 UDP，2 TCP，3 HTTP
  "transferProtocol": 2, // 传输协议：1 JSON、2 PDXP、3 FEP
  "protocolConfigId": 21, // 本地处理和RPC处理都必须关联协议配置
  "host": "192.168.1.10", // 数据处理服务本机监听网卡IP地址
  "port": 9001, // 数据处理服务本机监听端口
  "multicastIp": "239.1.1.1", // UDP组播地址；为空时按单播接收
  "enabled": 1, // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
  "rpcEnabled": 1, // 是否启用RPC处理：0否 1是
  "remark": "主链路" // 备注信息
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": 11 // 接口返回的业务数据
}
```

### `POST /collection/interfaces/update`

入参：

```jsonc
{
  "id": 11, // 业务数据主键 ID
  "taskId": "TASK-20260908-001", // 外部试验任务编号
  "interfaceName": "遥测 UDP 主接口", // 采集接口名称
  "interfaceType": 1, // 接口类型：1外部接口 2内部接口。枚举：1 外部接口，2 内部接口
  "sendFrom": "测控中心", // 数据发送来源
  "messageContent": "实时遥测", // 采集消息内容或格式说明
  "transferType": 1, // 传输方式：1UDP 2TCP 3HTTP。枚举：1 UDP，2 TCP，3 HTTP
  "transferProtocol": 2, // 传输协议：1 JSON、2 PDXP、3 FEP
  "protocolConfigId": 21, // 本地处理和RPC处理都必须关联协议配置
  "host": "192.168.1.11", // 数据处理服务本机监听网卡IP地址
  "port": 9001, // 数据处理服务本机监听端口
  "multicastIp": "239.1.1.1", // UDP组播地址；为空时按单播接收
  "enabled": 1, // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
  "rpcEnabled": 1, // 是否启用RPC处理：0否 1是
  "remark": "主链路" // 备注信息
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

本地处理和RPC处理都必须选择协议配置，区别仅由 `rpcEnabled` 决定。
UDP接口填写 `multicastIp` 时通过 `host` 指定的本机网卡加入该组播地址；`multicastIp` 为空时使用 `host` 和 `port` 接收单播数据。TCP接口不允许填写 `multicastIp`。

```json
{
  "taskId": "TASK-20260908-001",
  "interfaceName": "遥测本地采集接口",
  "interfaceType": 1,
  "transferType": 1,
  "transferProtocol": 2,
  "host": "0.0.0.0",
  "port": 9001,
  "multicastIp": null,
  "rpcEnabled": 0,
  "protocolConfigId": 21
}
```

修改时在上述请求中增加 `id`。
### `POST /collection/interfaces/delete`

入参：

```jsonc
{
  "id": 11 // 业务数据主键 ID
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `POST /collection/interfaces/enabled`

入参：

```jsonc
{
  "id": 11, // 业务数据主键 ID
  "enabled": 1 // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `GET /collection/protocols`

入参：

查询：taskId=TASK-20260908-001

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": [ // 接口返回的业务数据
    {
      "id": 21, // 业务数据主键 ID
      "configName": "遥测 JSON 协议", // 协议配置名称
      "protocolType": 1, // 协议类型：1JSON 2PDXP 3Protobuf 4RPC。枚举：1 JSON，2 PDXP，3 Protobuf，4 RPC
      "dataSourceType": 1, // 数据源类型：1 遥测，2 遥感，3 设备，4 环境。枚举：1 遥测，2 遥感，3 设备，4 环境
      "configDesc": "标准遥测格式", // 协议配置说明
      "configParams": { // 协议解析使用的动态配置参数
        "encoding": "UTF-8" // 协议字符编码
      },
      "parserClass": "com.example.JsonParser", // 协议解析器实现类的全限定名
      "enabled": 1, // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
    }
  ]
}
```

### `GET /collection/protocols/21`

入参：

路径：id=21

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": { // 接口返回的业务数据
    "id": 21, // 业务数据主键 ID
    "taskId": "TASK-20260908-001", // 外部试验任务编号
    "configName": "遥测 JSON 协议", // 协议配置名称
    "protocolType": 1, // 协议类型：1JSON 2PDXP 3Protobuf 4RPC。枚举：1 JSON，2 PDXP，3 Protobuf，4 RPC
    "dataSourceType": 1, // 数据源类型：1 遥测，2 遥感，3 设备，4 环境。枚举：1 遥测，2 遥感，3 设备，4 环境
    "configDesc": "标准遥测格式", // 协议配置说明
    "configParams": { // 协议解析使用的动态配置参数
      "encoding": "UTF-8" // 协议字符编码
    },
    "parserClass": "com.example.JsonParser", // 协议解析器实现类的全限定名
    "enabled": 1, // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
  }
}
```

### `POST /collection/protocols/create`

入参：

```jsonc
{
  "taskId": "TASK-20260908-001", // 外部试验任务编号
  "configName": "遥测 JSON 协议", // 协议配置名称
  "protocolType": 1, // 协议类型：1JSON 2PDXP 3Protobuf 4RPC。枚举：1 JSON，2 PDXP，3 Protobuf，4 RPC
  "dataSourceType": 1, // 数据源类型：1 遥测，2 遥感，3 设备，4 环境。枚举：1 遥测，2 遥感，3 设备，4 环境
  "configDesc": "标准遥测格式", // 协议配置说明
  "configParams": { // 协议解析使用的动态配置参数
    "encoding": "UTF-8" // 协议字符编码
  },
  "parserClass": "com.example.JsonParser", // 协议解析器实现类的全限定名
  "enabled": 1 // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": 21 // 接口返回的业务数据
}
```

PDXP协议的`configParams`示例：

```jsonc
{
  "taskId": "TASK-20260908-001",
  "configName": "遥测PDXP解析配置",
  "protocolType": 2,
  "dataSourceType": 1,
  "configDesc": "PDXP数据域字段解析配置",
  "configParams": {
    "pdxphead": {
      "ver": null,
      "mid": null,
      "sid": null,
      "did": null,
      "bid": null,
      "no": null,
      "flag": null,
      "res": null
    },
    "fields": [
      {
        "id": 81,
        "tableIndex": "1",
        "bitWidth": "32",
        "telemetryName": "温度",
        "telemetryCode": "TMK1001",
        "formulaType": "109",
        "formulaDesc": "",
        "processParam": "",
        "decimalPlaces": "5",
        "alarmFlag": "1",
        "normalValue": "正常:[-1,30]",
        "warningValue": "",
        "stateChangeInfo": "",
        "commandCode": "",
        "systemName": "发控台",
        "controlChannel": "",
        "mergeChannelCount": "0",
        "delayChannel": "0",
        "storeFlag": "1",
        "calibrationFormula": "x*0.1"
      }
    ]
  },
  "parserClass": null,
  "enabled": 1
}
```

RPC协议的`configParams`示例：

```jsonc
{
  "taskId": "TASK-20260908-001",
  "configName": "遥测RPC处理配置",
  "protocolType": 4,
  "dataSourceType": 1,
  "configDesc": "卫星遥测RPC请求参数",
  "configParams": {
    "fields": [
      {
        "field": "sat_code",
        "fieldName": "卫星代号",
        "dataType": "string",
        "value": "SAT-001"
      },
      {
        "field": "channel",
        "fieldName": "通道代号",
        "dataType": "string",
        "value": "CH-01"
      },
      {
        "field": "data_source",
        "fieldName": "数据源类型",
        "dataType": "string",
        "value": "telemetry"
      }
    ]
  },
  "parserClass": null,
  "enabled": 1
}
```

FEP文件上传对象存储后发送的文件完成消息：

```json
{
  "requirementId": "",
  "requirementName": "",
  "requirementDesc": "",
  "version": "",
  "fileUrl": "http://192.168.2.4:9002/ekbs/2026/09/02/b14cfe8d4d9d4f668f8f5bcaafb0aa4b.docx",
  "fileType": "docx",
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

### `POST /collection/protocols/update`

入参：

```jsonc
{
  "id": 21, // 业务数据主键 ID
  "taskId": "TASK-20260908-001", // 外部试验任务编号
  "configName": "遥测 JSON V2", // 协议配置名称
  "protocolType": 1, // 协议类型：1JSON 2PDXP 3Protobuf 4RPC。枚举：1 JSON，2 PDXP，3 Protobuf，4 RPC
  "dataSourceType": 1, // 数据源类型：1 遥测，2 遥感，3 设备，4 环境。枚举：1 遥测，2 遥感，3 设备，4 环境
  "configDesc": "升级版格式", // 协议配置说明
  "configParams": { // 协议解析使用的动态配置参数
    "encoding": "UTF-8" // 协议字符编码
  },
  "parserClass": "com.example.JsonParser", // 协议解析器实现类的全限定名
  "enabled": 1 // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `POST /collection/protocols/delete`

删除前检查所有未删除采集接口的绑定关系，不区分启用状态。存在绑定时不删除，提示例如：`协议配置已被采集接口【接口A、接口B】绑定，不能删除`。已删除的采集接口不阻止删除。

存在绑定时返回 HTTP 400：

```json
{
  "code": 400,
  "message": "协议配置已被采集接口【接口A、接口B】绑定，不能删除"
}
```

入参：

```jsonc
{
  "id": 21 // 业务数据主键 ID
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `POST /collection/protocols/enabled`

入参：

```jsonc
{
  "id": 21, // 业务数据主键 ID
  "enabled": 0 // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

## 4. 数据可视化

### `GET /visualization/widgets`

入参：

查询：taskId=TASK-20260908-001

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": [ // 接口返回的业务数据
    {
      "id": 41, // 业务数据主键 ID
      "taskId": "TASK-20260908-001", // 外部试验任务编号
      "userId": 1, // 用户主键 ID
      "widgetKey": "telemetry_curve", // 可视化组件唯一标识
      "widgetType": 1, // 组件类型：1实时曲线 2实时数据 3实时告警 4载荷图像。枚举：1 实时曲线，2 实时数据，3 实时告警，4 载荷图像
      "widgetTitle": "遥测实时曲线", // 可视化组件显示标题
      "gridX": 0, // 组件左上角横向网格坐标，从 0 开始
      "gridY": 0, // 组件左上角纵向网格坐标，从 0 开始
      "gridWidth": 6, // 组件占用的网格列数
      "gridHeight": 4, // 组件占用的网格行数
      "enabled": 1, // 组件是否显示。枚举：0 隐藏，1 显示
    }
  ]
}
```

### `GET /visualization/widgets/41`

入参：

路径：id=41

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": { // 接口返回的业务数据
    "id": 41, // 业务数据主键 ID
    "taskId": "TASK-20260908-001", // 外部试验任务编号
    "userId": 1, // 用户主键 ID
    "widgetKey": "telemetry_curve", // 可视化组件唯一标识
    "widgetType": 1, // 组件类型：1实时曲线 2实时数据 3实时告警 4载荷图像。枚举：1 实时曲线，2 实时数据，3 实时告警，4 载荷图像
    "widgetTitle": "遥测实时曲线", // 可视化组件显示标题
    "gridX": 0, // 组件左上角横向网格坐标，从 0 开始
    "gridY": 0, // 组件左上角纵向网格坐标，从 0 开始
    "gridWidth": 6, // 组件占用的网格列数
    "gridHeight": 4, // 组件占用的网格行数
    "enabled": 1, // 组件是否显示。枚举：0 隐藏，1 显示
  }
}
```

### `POST /visualization/widgets/create`

入参：

```jsonc
{
  "taskId": "TASK-20260908-001", // 外部试验任务编号
  "widgetKey": "telemetry_curve", // 可视化组件唯一标识
  "widgetType": 1, // 组件类型：1实时曲线 2实时数据 3实时告警 4载荷图像。枚举：1 实时曲线，2 实时数据，3 实时告警，4 载荷图像
  "widgetTitle": "遥测实时曲线", // 可视化组件显示标题
  "gridX": 0, // 组件左上角横向网格坐标，从 0 开始
  "gridY": 0, // 组件左上角纵向网格坐标，从 0 开始
  "gridWidth": 6, // 组件占用的网格列数
  "gridHeight": 4, // 组件占用的网格行数
  "enabled": 1 // 组件是否显示。枚举：0 隐藏，1 显示
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": 41 // 接口返回的业务数据
}
```

### `POST /visualization/widgets/update`

入参：

```jsonc
{
  "id": 41, // 业务数据主键 ID
  "taskId": "TASK-20260908-001", // 外部试验任务编号
  "widgetKey": "telemetry_curve", // 可视化组件唯一标识
  "widgetType": 1, // 组件类型：1实时曲线 2实时数据 3实时告警 4载荷图像。枚举：1 实时曲线，2 实时数据，3 实时告警，4 载荷图像
  "widgetTitle": "遥测趋势", // 可视化组件显示标题
  "gridX": 0, // 组件左上角横向网格坐标，从 0 开始
  "gridY": 0, // 组件左上角纵向网格坐标，从 0 开始
  "gridWidth": 8, // 组件占用的网格列数
  "gridHeight": 4, // 组件占用的网格行数
  "enabled": 1 // 组件是否显示。枚举：0 隐藏，1 显示
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `POST /visualization/widgets/delete`

入参：

```jsonc
{
  "id": 41 // 业务数据主键 ID
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `GET /visualization/widgets/41/items`

返回组件的全部参数筛选项，`isSelected` 表示是否勾选。由参数解析配置自动同步的记录不绑定单一采集接口或协议，因此两个关联ID为 `null`。

入参：

路径：widgetId=41

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": [ // 接口返回的业务数据
    {
      "id": 51, // 业务数据主键 ID
      "widgetId": 41, // 可视化组件 ID
      "parseRuleId": 81, // 参数解析配置 ID
      "interfaceId": null, // 自动同步项不绑定单一采集接口
      "protocolConfigId": null, // 自动同步项不绑定单一协议配置
      "itemCode": "TEMP_01", // 组件数据项编码
      "itemName": "温度1", // 组件数据项名称
      "isSelected": 1, // 是否勾选：0未勾选 1已勾选。枚举：0 未选中，1 已选中
    }
  ]
}
```

### `GET /visualization/items/51`

入参：

路径：id=51

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": { // 接口返回的业务数据
    "id": 51, // 业务数据主键 ID
    "widgetId": 41, // 可视化组件 ID
    "parseRuleId": 81, // 参数解析配置 ID
    "interfaceId": null, // 自动同步项不绑定单一采集接口
    "protocolConfigId": null, // 自动同步项不绑定单一协议配置
    "itemCode": "TEMP_01", // 组件数据项编码
    "itemName": "温度1", // 组件数据项名称
    "isSelected": 1, // 是否勾选：0未勾选 1已勾选。枚举：0 未选中，1 已选中
  }
}
```

### `POST /visualization/items/create`

入参：

```jsonc
{
  "widgetId": 41, // 可视化组件 ID
  "parseRuleId": 81, // 参数解析配置 ID
  "interfaceId": 11, // 采集接口 ID
  "protocolConfigId": 21, // 协议配置 ID
  "itemCode": "TEMP_01", // 组件数据项编码
  "itemName": "温度1", // 组件数据项名称
  "isSelected": 1 // 是否勾选：0未勾选 1已勾选。枚举：0 未选中，1 已选中
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": 51 // 接口返回的业务数据
}
```

### `POST /visualization/items/update`

入参：

```jsonc
{
  "id": 51, // 业务数据主键 ID
  "widgetId": 41, // 可视化组件 ID
  "parseRuleId": 81, // 参数解析配置 ID
  "interfaceId": 11, // 采集接口 ID
  "protocolConfigId": 21, // 协议配置 ID
  "itemCode": "TEMP_01", // 组件数据项编码
  "itemName": "舱内温度", // 组件数据项名称
  "isSelected": 1 // 是否勾选：0未勾选 1已勾选。枚举：0 未选中，1 已选中
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `POST /visualization/items/delete`

入参：

```jsonc
{
  "id": 51 // 业务数据主键 ID
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `POST /visualization/items/selected`

入参：

```jsonc
{
  "id": 51, // 业务数据主键 ID
  "status": 0 // 启用状态。枚举：0 停用，1 启用
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```


## 5. 数据处理

### `GET /processing/rule-config?taskId=TASK-20260908-001`

如果任务尚无处理规则配置，查询会自动插入参数值范围检查关闭的默认配置后返回。

出参：

```jsonc
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "parameterRangeCheckEnabled": 1 // 参数值范围检查
  }
}
```

### `POST /processing/rule-config/update`

入参：

```jsonc
{
  "taskId": "TASK-20260908-001",
  "parameterRangeCheckEnabled": 1
}
```

出参为保存后的完整配置，结构同查询接口。

### `GET /processing/overview`

入参：

查询：taskId=TASK-20260908-001

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "todayProcessedCount": 128000, // 今日成功处理的数据数量
    "deduplicatedCount": 320 // 当前任务全部历史累计去重数量
  }
}
```

### `GET /processing/realtime`

入参：

查询：taskId=TASK-20260908-001

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "processRate": "2400 条/秒", // 当前任务所有接口的PDXP帧处理速率之和，按各接口实际采样间隔计算
    "cpuUsage": "36.50%", // 管理服务所在机器整体CPU使用率，与系统告警同口径
    "memoryUsage": "23.50%" // 管理服务所在机器物理内存使用率，与系统告警同口径
  }
}
```

### `GET /processing/device-satellites`

`taskId` 必填，`type` 可选。示例：`taskId=TASK-20260908-001&type=1` 查询卫星；只传 `taskId=TASK-20260908-001` 时查询该任务下全部设备和卫星，不分页。

```json
{"code":200,"message":"操作成功","data":[{"id":12,"code":"卫星一号","name":"卫星一号","type":"1"}]}
```

### `GET /processing/rules`

必填查询参数：`taskId=TASK-20260908-001&deviceSatelliteId=12`。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 81,
      "taskId": "TASK-20260908-001",
      "deviceSatelliteId": 12,
      "tableIndex": "1",
      "bitWidth": "32",
      "telemetryName": "电压",
      "telemetryCode": "FKT001",
      "formulaType": "109",
      "formulaDesc": null,
      "processParam": null,
      "decimalPlaces": "5",
      "alarmFlag": "1",
      "normalValue": "加电:[41,43]/断电:[-1,1]/过程:[1,41]",
      "warningValue": null,
      "stateChangeInfo": null,
      "commandCode": null,
      "systemName": "发控台",
      "controlChannel": null,
      "mergeChannelCount": "0",
      "delayChannel": "0",
      "storeFlag": "1",
      "calibrationFormula": "x*0.1",
      "alarmFlagName": "是",
      "storeFlagName": "是",
      "isDeleted": 0,
      "createTime": "2026-09-11T10:00:00",
      "updateTime": "2026-09-11T10:00:00"
    }
  ]
}
```

### `POST /processing/rules/import`

```http
POST /processing/rules/import
Content-Type: multipart/form-data

taskId=TASK-20260908-001
type=1
file=@设备卫星参数.xlsx
```

也可以上传制表符TXT：

```http
POST /processing/rules/import
Content-Type: multipart/form-data

taskId=TASK-20260908-001
type=2
file=@TMtable-A.txt
```

支持.xls、.xlsx和.txt文件（最大20MB）。Excel按Sheet页签分别创建设备卫星；TXT一次表示一个设备卫星，使用去掉扩展名的文件名填写code和name，自动兼容UTF-8和GBK编码。第一行必须包含19个中文表头，TXT按制表符分列；旧TXT最后一列使用反斜线时按空校准公式处理。每个设备至少有一条有效数据，空行忽略，序号不能重复。完整校验后在同一事务中替换当前taskId下同类型数据，任意失败整体回滚。

示例：工作簿含“卫星一号”和“卫星二号”两页，分别创建设备卫星，参数关联各自的新ID。再次导入 `taskId=TASK-20260908-001&type=1` 时，只逻辑删除任务TASK-20260908-001的原卫星及参数；任务TASK-20260908-001的设备和其他任务全部保留。

```json
{"code":200,"message":"操作成功","data":5}
```

## 6. 数据校准

### `GET /calibration/channels`

入参：

查询：taskId=TASK-20260908-001

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": [ // 接口返回的业务数据
    {
      "id": 91, // 业务数据主键 ID
      "taskId": "TASK-20260908-001", // 外部试验任务编号
      "channelName": "温度通道1", // 校准通道名称
      "calibrationFormula": "CH01", // 校准公式
      "normalRange": "-20~80", // 正常数值范围，仅阈值法必填；其他算法可不填
      "detectMethod": 3, // 野值检测方法：1莱特准则 2阈值法 3肖维涅法。枚举：1 莱特准则，2 阈值法，3 肖维涅法
      "detectMethodName": "肖维涅法", // 野值检测方法中文名称
      "sigmaValue": 3, // 莱特准则使用的 Sigma 检测阈值
      "fluctuationRate": 10, // 阈值法使用的阈值百分比
      "chauvenetCoef": 0.5, // 肖维涅法判别常数，固定为0.5
      "minSampleCount": 10, // 肖维涅法参与计算的最近样本数
      "iterateCount": 1, // 肖维涅法迭代次数
      "sampleWindow": 100, // 莱特准则使用的最近样本窗口
      "dynamicUpdate": 1, // 是否动态更新：0否 1是。枚举：0 否，1 是
      "dynamicUpdateName": "是", // 动态更新状态中文名称
      "autoClean": 1, // 是否自动排除异常值：0否 1是。枚举：0 否，1 是
      "autoCleanName": "是", // 自动排除状态中文名称
      "enabled": 1, // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
      "enabledName": "启用", // 启用状态中文名称
      "channelStatus": 1, // 通道状态：0停止 1正常 2异常。枚举：0 停止，1 正常，2 异常
      "channelStatusName": "正常" // 通道运行状态中文名称
    }
  ]
}
```

### `GET /calibration/channels/91`

入参：

路径：id=91

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": { // 接口返回的业务数据
    "id": 91, // 业务数据主键 ID
    "taskId": "TASK-20260908-001", // 外部试验任务编号
    "channelName": "温度通道1", // 校准通道名称
    "calibrationFormula": "CH01", // 校准公式
    "normalRange": "-20~80", // 正常数值范围，仅阈值法必填；其他算法可不填
    "detectMethod": 3, // 野值检测方法：1莱特准则 2阈值法 3肖维涅法。枚举：1 莱特准则，2 阈值法，3 肖维涅法
    "detectMethodName": "肖维涅法", // 野值检测方法中文名称
    "sigmaValue": 3, // 莱特准则使用的 Sigma 检测阈值
    "fluctuationRate": 10, // 阈值法使用的阈值百分比
    "chauvenetCoef": 0.5, // 肖维涅法判别常数，固定为0.5
    "minSampleCount": 10, // 肖维涅法参与计算的最近样本数
    "iterateCount": 1, // 肖维涅法迭代次数
    "sampleWindow": 100, // 莱特准则使用的最近样本窗口
    "dynamicUpdate": 1, // 是否动态更新：0否 1是。枚举：0 否，1 是
    "dynamicUpdateName": "是", // 动态更新状态中文名称
    "autoClean": 1, // 是否自动排除异常值：0否 1是。枚举：0 否，1 是
    "autoCleanName": "是", // 自动排除状态中文名称
    "enabled": 1, // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
    "enabledName": "启用", // 启用状态中文名称
    "channelStatus": 1, // 通道状态：0停止 1正常 2异常。枚举：0 停止，1 正常，2 异常
    "channelStatusName": "正常" // 通道运行状态中文名称
  }
}
```

### `GET /calibration/overview`

入参：查询参数 `taskId=TASK-20260908-001`

出参：

```jsonc
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "outlierCount": 320 // 当前任务累计检出的野值数量
  }
}
```

### `POST /calibration/channels/create`

入参：

```jsonc
{
  "taskId": "TASK-20260908-001", // 外部试验任务编号
  "channelName": "温度通道1", // 校准通道名称
  "calibrationFormula": "CH01", // 校准公式
  "normalRange": "-20~80", // 正常数值范围，仅阈值法必填；其他算法可不填
  "detectMethod": 3, // 野值检测方法：1莱特准则 2阈值法 3肖维涅法。枚举：1 莱特准则，2 阈值法，3 肖维涅法
  "sigmaValue": 3, // 莱特准则使用的 Sigma 检测阈值
  "fluctuationRate": 10, // 阈值法使用的阈值百分比
  "chauvenetCoef": 0.5, // 肖维涅法判别常数，固定为0.5
  "minSampleCount": 10, // 肖维涅法参与计算的最近样本数
  "iterateCount": 1, // 肖维涅法迭代次数
  "sampleWindow": 100, // 莱特准则使用的最近样本窗口
  "dynamicUpdate": 1, // 是否动态更新：0否 1是。枚举：0 否，1 是
  "autoClean": 1, // 是否自动排除异常值：0否 1是。枚举：0 否，1 是
  "enabled": 1, // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
  "channelStatus": 1 // 通道状态：0停止 1正常 2异常。枚举：0 停止，1 正常，2 异常
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": 91 // 接口返回的业务数据
}
```

### `POST /calibration/channels/update`

入参：

```jsonc
{
  "id": 91, // 业务数据主键 ID
  "taskId": "TASK-20260908-001", // 外部试验任务编号
  "channelName": "温度通道1", // 校准通道名称
  "calibrationFormula": "CH01", // 校准公式
  "normalRange": "-30~90", // 正常数值范围，仅阈值法必填；其他算法可不填
  "detectMethod": 3, // 野值检测方法：1莱特准则 2阈值法 3肖维涅法。枚举：1 莱特准则，2 阈值法，3 肖维涅法
  "sigmaValue": 3, // 莱特准则使用的 Sigma 检测阈值
  "fluctuationRate": 10, // 阈值法使用的阈值百分比
  "chauvenetCoef": 0.5, // 肖维涅法判别常数，固定为0.5
  "minSampleCount": 10, // 肖维涅法参与计算的最近样本数
  "iterateCount": 1, // 肖维涅法迭代次数
  "sampleWindow": 100, // 莱特准则使用的最近样本窗口
  "dynamicUpdate": 1, // 是否动态更新：0否 1是。枚举：0 否，1 是
  "autoClean": 1, // 是否自动排除异常值：0否 1是。枚举：0 否，1 是
  "enabled": 1, // 是否启用通道：0否 1是。枚举：0 禁用，1 启用
  "channelStatus": 1 // 通道状态：0停止 1正常 2异常。枚举：0 停止，1 正常，2 异常
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `POST /calibration/channels/delete`

入参：

```jsonc
{
  "id": 91 // 业务数据主键 ID
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `GET /calibration/records`

入参：

查询：taskId=TASK-20260908-001&pageNum=1&pageSize=20

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": { // 接口返回的业务数据
    "pageNum": 1, // 当前页码，从 1 开始
    "pageSize": 20, // 每页记录数量，最大 100 条
    "total": 1, // 符合查询条件的记录总数
    "records": [ // 当前页数据列表
      {
        "id": 101, // 业务数据主键 ID
        "taskId": "TASK-20260908-001", // 外部试验任务编号
        "channelId": 91, // 校准通道 ID
        "channelName": "温度通道1", // 校准通道名称
        "outlierCount": 3 // 累计检出的野值数量
      }
    ]
  }
}
```

### `GET /calibration/records/101`

入参：

路径：id=101；查询：taskId=TASK-20260908-001&pageNum=1&pageSize=20

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": { // 接口返回的业务数据
    "record": {
      "id": 101,
      "taskId": "TASK-20260908-001",
      "channelId": 91,
      "channelName": "温度通道1",
      "outlierCount": 3
    },
    "details": {
      "pageNum": 1,
      "pageSize": 20,
      "total": 1,
      "records": [
        {
          "id": 501,
          "taskId": "TASK-20260908-001",
          "channelId": 91,
          "interfaceId": 1,
          "channelName": "设备A",
          "parameterName": "母线电压",
          "tmSymbol": "VOLT_01",
          "telemetryValue": 45.0,
          "detail": "遥测值45超出正常范围上限43，超出2，判定为野值",
          "createTime": "2026-09-15T14:01:00"
        }
      ]
    }
  }
}
```


## 7. 告警管理

### `GET /alarms/system-monitor/current`

采集节点总数查询全部未删除接口，在线数表示已启用接口数；服务状态实时查询注册中心，消息消费积压本次保持原逻辑。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "sampleTime": "2026-10-09T12:00:00",
    "serviceStatus": "RUNNING",
    "serviceStatusName": "运行中",
    "uptimeSeconds": 86400,
    "onlineNodeCount": 3,
    "totalNodeCount": 5,
    "databaseLatencyMs": 12,
    "messageQueueDepth": null,
    "heartbeatStatus": "WAITING",
    "heartbeatStatusName": "等待心跳",
    "lastHeartbeatTime": null,
    "cpuUsagePercent": 36.5,
    "memoryUsagePercent": 58.2,
    "diskUsagePercent": 42.1,
    "healthScore": 42
  }
}
```

### `GET /alarms/system-monitor/history`

入参：

查询参数见 URL

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": [ // 接口返回的业务数据
    {
      "id": 110, // 业务数据主键 ID
      "taskId": "TASK-20260908-001", // 外部试验任务编号
      "sampleTime": "2026-07-30T14:20:00", // 数据采样时间
      "cpuUsagePercent": 30.1, // CPU 使用率，单位为百分比
      "memoryUsagePercent": 55.4, // 内存使用率，单位为百分比
      "diskUsagePercent": 42.0, // 磁盘使用率，单位为百分比
      "healthScore": 97 // 系统综合健康评分，范围 0～100
    },
    {
      "id": 111, // 业务数据主键 ID
      "taskId": "TASK-20260908-001", // 外部试验任务编号
      "sampleTime": "2026-07-30T15:20:00", // 数据采样时间
      "cpuUsagePercent": 36.5, // CPU 使用率，单位为百分比
      "memoryUsagePercent": 58.2, // 内存使用率，单位为百分比
      "diskUsagePercent": 42.1, // 磁盘使用率，单位为百分比
      "healthScore": 96 // 系统综合健康评分，范围 0～100
    }
  ]
}
```

### `GET /alarms/data`

来源筛选使用 `/alarms/findData` 返回的英文编码，参数名为 `sourceCode`；查询结果中的 `sourceName` 是中文展示名称。数据告警仅使用2一般、4严重。

```http
GET /alarms/data?taskId=TASK-20260908-001&sourceCode=PROCESSING&pageNum=1&pageSize=20
```

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "pageNum": 1,
    "pageSize": 20,
    "total": 1,
    "records": [
      {
        "id": 121,
        "taskId": "TASK-20260908-001",
        "alarmTime": "2026-10-09T12:00:00",
        "sourceCode": "PROCESSING",
        "sourceName": "数据处理",
        "eventType": "PARAMETER_RANGE",
        "eventTypeName": "遥测参数越限",
        "alarmMsg": "遥测参数越限；接口：遥测采集（ID：11）；参数：温度（TM001）；判定原码值：120；未命中允许范围",
        "alarmLevel": 2,
        "alarmLevelName": "一般",
        "alarmStatus": 0,
        "alarmStatusName": "未处理",
        "interfaceId": 11,
        "processTaskId": null,
        "calibChannelId": null,
        "tmSymbol": "TM001",
        "parameterName": "温度",
        "triggerRule": "正常(0~100)",
        "alarmDetail": "事件：遥测参数越限；接口：遥测采集（ID：11）；监听地址：127.0.0.1:9001；参数：温度（TM001）；判定原码值：120；规则：正常(0~100)",
        "triggerCount": 3,
        "lastTriggerTime": "2026-10-09T12:00:10",
        "recoverTime": null
      }
    ]
  }
}
```

### `GET /alarms/data/131`

入参：

路径：id=131

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 响应提示信息
  "data": { // 接口返回的业务数据
    "id": 131, // 业务数据主键 ID
    "taskId": "TASK-20260908-001", // 外部试验任务编号
    "alarmTime": "2026-07-30T15:20:00", // 告警触发时间
    "sourceCode": "PROCESSING", // 告警来源英文编码
    "sourceName": "数据处理", // 告警来源中文名称，仅用于展示
    "alarmMsg": "温度超上限", // 数据监测告警内容
    "alarmLevel": 4, // 数据监测告警级别。枚举：2 一般，4 严重
    "alarmStatus": 0, // 数据告警处理状态。枚举：0 未处理，1 已处理
    "taskName": "任务A", // 试验任务或数据处理任务名称
    "channelCode": "TEMP_01", // 触发告警的通道编码
    "triggerRule": "> 80℃" // 告警触发规则说明
  }
}
```

### `POST /alarms/data/status`

只修改人工处理状态，不改变告警内容及自动恢复时间；操作记录到系统日志。

入参：

```jsonc
{
  "id": 131, // 业务数据主键 ID
  "status": 1 // 人工处理状态。枚举：0 未处理，1 已处理
}
```

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功" // 业务处理结果提示信息
}
```

### `GET /alarms/system`

可选查询参数：`status=0`（未恢复）或 `status=1`（已恢复）。系统告警统一自动入库，不再提供人工写入接口。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 141,
      "alarmTime": "2026-10-09T12:00:00",
      "objName": "计算服务CPU",
      "alarmType": "cpu_usage",
      "alarmTypeName": "CPU使用率",
      "alarmContent": "CPU使用率为92，持续超过阈值85",
      "alarmLevel": 2,
      "alarmLevelName": "一般",
      "alarmStatus": 0,
      "alarmStatusName": "未恢复",
      "isDeleted": 0,
      "metricCode": "cpu_usage",
      "metricName": "CPU使用率",
      "currentValue": 92,
      "thresholdValue": 85,
      "firstAlarmTime": "2026-10-09T12:00:00",
      "lastAlarmTime": "2026-10-09T12:00:10",
      "recoverTime": null,
      "triggerCount": 3
    }
  ]
}
```

## 8. 系统日志

### `GET /system/logs`

入参：

```http
GET /system/logs?taskId=TASK-20260908-001&logLevel=ERROR&logSource=COLLECTION&operatorId=admin&keyword=创建采集接口失败&startTime=2026-08-06T00:00:00&endTime=2026-08-06T23:59:59&pageNum=1&pageSize=20
```

日志级别枚举：`INFO` 信息、`WARN` 警告、`ERROR` 错误。

出参：

```jsonc
{
  "code": 200, // 业务状态码，200 表示操作成功
  "message": "操作成功", // 业务处理结果提示信息
  "data": {
    "pageNum": 1, // 当前页码，从1开始
    "pageSize": 20, // 每页记录数，最大100
    "total": 1, // 符合条件的日志总数
    "records": [
      {
        "id": 201, // 日志主键ID
        "taskId": "TASK-20260908-001", // 外部试验任务编号
        "logTime": "2026-08-06T14:39:14", // 日志发生时间
        "logLevel": "ERROR", // 日志级别：INFO信息 WARN警告 ERROR错误
        "logLevelName": "错误", // 日志级别中文名称，前端可直接展示
        "logSource": "COLLECTION", // 日志来源英文编码
        "logSourceName": "数据采集", // 日志来源中文名称
        "operatorId": "admin", // 操作人标识
        "logContent": "创建采集接口失败", // 日志内容
        "logDetail": "操作人：admin；接口名称：遥测接收接口；接口ID：101；结果：创建失败；原因：后台异常" // 完整详情
      }
    ]
  }
}
```

### `GET /system/logs/201`

出参：

```jsonc
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 201,
    "taskId": "TASK-20260908-001",
    "logTime": "2026-08-06T14:39:14",
    "logLevel": "ERROR", // 日志级别：INFO信息 WARN警告 ERROR错误
    "logLevelName": "错误", // 日志级别中文名称，前端可直接展示
    "logSource": "COLLECTION",
    "logSourceName": "数据采集",
    "operatorId": "admin",
    "logContent": "创建采集接口失败",
    "logDetail": "操作人：admin；接口名称：遥测接收接口；接口ID：101；结果：创建失败；原因：后台异常"
  }
}
```

## 9. 异常响应示例

业务校验失败：

```jsonc
{
  "code": 400, // 业务状态码，200 表示操作成功
  "message": "业务主键不能为空" // 业务处理结果提示信息
}
```

未登录或 Token 无效：

```jsonc
{
  "code": 401, // 业务状态码，200 表示操作成功
  "message": "登录状态已失效" // 业务处理结果提示信息
}
```

### `GET /experiment/tasks`

入参：无

出参：

```jsonc
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "taskId": "TASK-20260908-001", // 外部试验任务编号
      "requirementId": "REQ-20260908-001", // 关联需求业务编号
      "taskName": "遥测试验任务", // 试验任务名称
      "taskPriority": 5, // 任务优先级
      "planId": "b38cb6f7-7cb6-4fae-bbb5-d5b55c808143", // 试验方案内部编号
      "planCode": "PLAN-001", // 试验方案业务编码
      "flowInstanceId": "c88bd453-3c47-4499-8940-642fbe09b6c1", // 流程实例编号
      "executionAttempt": 1, // 执行轮次
      "taskStatus": 1, // 任务状态编码
      "taskStatusName": "运行中", // 任务状态中文名称
      "endReason": null, // 结束原因
      "endReasonName": "", // 结束原因中文名称，运行中任务为空字符串
      "eventTime": "2026-09-16T18:00:00" // 最近生命周期事件时间
    }
  ]
}
```
## PDXP本地参数解析示例

采集接口选择 `rpcEnabled=0` 时，通过 `protocolConfigId` 取得协议配置，将 `configParams.fields` 按 `tableIndex` 数字升序排列，再按 `bitWidth` 连续解码并组装遥测Protobuf消息。

当字段的 `alarmFlag` 为 `"1"` 时，优先使用 `warningValue` 状态范围，为空时使用 `normalValue`。命中范围后写入 `STATE_TYPE_NORMAL`、从零开始的 `stateIndex` 和范围中文名称 `stateName`；未命中时只写入 `STATE_TYPE_ALARM`，不写入 `stateIndex` 和 `stateName`。本地处理按 `formulaType` 设置 `valueType`：300为 `VALUE_TYPE_FLOAT`，301为 `VALUE_TYPE_DOUBLE`，302为 `VALUE_TYPE_TEXT`，303为 `VALUE_TYPE_TIME`；未约定的旧类型保持 `VALUE_TYPE_RAW`。状态范围仍按解码原始整数判断。

| tableIndex | telemetryCode | bitWidth | 读取位置 | 原始值 |
| --- | --- | --- | --- | --- |
| 1 | TM001 | 3 | 第0～2位 | 5 |
| 2 | TM002 | 9 | 第3～11位 | 377 |
| 3 | TM003 | 4 | 第12～15位 | 10 |

上述示例的数据域在去掉前5字节自定义数据和末尾4字节遥测帧头后为十六进制 `CD AB`。每字节低位先读，跨字节连续读取，该取位示例未配置公式类型，按无符号小端整数解释；配置300或301时还会计算处理公式并按小数位数四舍五入。

每行生成一个 `Telemetry`，汇总为一帧 `TelemetryMessage`：`table_index` 对应 `tableIndex`，`tm_symbol` 和字典键对应 `telemetryCode`，`tm_name` 对应 `telemetryName`。该未配置公式类型的示例中，`value_type` 使用原码枚举；`value_text` 保存精确十进制值，`value` 使用协议的双精度数值（大整数可能产生舍入）；`raw_data` 保存低位对齐的小端字段原码，末字节高位补零。TM002的原码为 `79 01`。

每个字段必须携带参数解析配置 `id`，处理时据此关联当前任务下的设备卫星信息；设备卫星 `code` 写入 `sat_code`，`name` 第一个 `_` 前写入 `channel_name`，后面写入 `bussiness_id`。每个字段自己的 `calibrationFormula` 写入对应遥测参数的 `calibration_formula`，不再作为帧级通道编号使用。消息头主题固定为试验数据，子主题固定为遥测物理值，消息来源为“数据处理”，任务主键来自当前采集接口，遥测类型固定为设备遥测。缺少配置、设备名称格式不正确、无效位宽、重复大表序号或数据不足时停止本帧后续处理，并回退保存完整PDXP原始包。同一帧存在相同遥测代号时保留最后一项，并把该参数的 `is_dep_value` 设置为 `true`。`frame_raw_data` 只保存去掉自定义头和遥测头后的中间遥测原码，时间使用PDXP发送时间，配置未覆盖的尾部位不生成参数。

正常PDXP帧处理成功后，整帧记录和全部遥测参数通过一次 `insertRecords` 批量写入IoTDB。整帧记录不再保存 `telemetryType`，参数记录不再保存 `source`；参数的状态索引测点命名为 `stateIndex`，未命中任何状态范围时不写入该测点。状态中文名称写入 `stateName`，`GET /processing/processed` 将其作为 `status` 返回，并同时返回允许为空的 `stateIndex`。`is_dep_value` 为 `true` 时 `deduplication` 写入“去重”，否则写入“原始”。IoTDB中的 `taskId` 按文本保存；`GET /processing/realtime/telemetry`、`GET /processing/processed` 和 `GET /processing/realtime/invalid` 均要求传入字符串任务编号，例如 `TASK-20260908-001`。解析失败时保存的PDXP完整原始包也会写入该任务编号。

### 本地公式与类型示例

数值原始字节继续按小端无符号整数解码为x，不按IEEE 754位模式重解释。配置示例：

```json
{"id":81,"tableIndex":1,"bitWidth":32,"telemetryCode":"TM001","formulaType":"300","formulaDesc":"y=a*x+b","processParam":"0.02/1","decimalPlaces":"2"}
```

当x=100时，a=0.02、b=1，得到y=3；Proto的value为3，valueText为“3.00”，valueType为VALUE_TYPE_FLOAT。类型301使用VALUE_TYPE_DOUBLE。value是二进制浮点字段，不保存末尾零；valueText保存修约后的十进制展示。公式支持四则运算、括号、正负号及科学计数法；参数依次绑定a、b等变量，空公式或横线占位符直接使用x。处理公式formulaDesc与用于野值检测的calibrationFormula独立。

类型302：例如bitWidth=24、字节41 42 43，valueText为“ABC”，valueType为VALUE_TYPE_TEXT，value保持默认值0；按UTF-8解码，位宽必须为8的整数倍，不执行处理公式。

类型303：value保存从2000-01-01 12:00:00开始的0.1毫秒整数计数，valueType为VALUE_TYPE_TIME；例如计数10001对应valueText“2000-01-01 12:00:01.0001”。配置公式时，公式结果必须是整数计数，不按decimalPlaces舍入；直接在约定日历起点上累加，不作时区偏移。格式固定为yyyy-MM-dd HH:mm:ss.SSSS。

非法公式、缺少系数、除零、数值溢出或非整数时间计数会终止当前帧后续处理，并由原有失败流程记录中文上下文及回退保存原始帧；不会用原码伪装成计算成功的物理量。
### 实时遥测整帧新增身份字段

`GET /processing/realtime/telemetry?taskId=TASK-20260908-001&interfaceId=12` 返回的每条记录在satelliteCode后增加satelliteName和channelCode：

```json
{"interfaceId":12,"time":"2026-09-20T12:00:00","satelliteCode":"SAT-003","satelliteName":"试验卫星三号","channelCode":"CH-01","channelName":"发控台","rawLength":3,"rawFrame":"41 42 43","checkResult":1,"checkResultName":"正确"}
```

正常整帧的批量入库与单独原码入库均将Proto的satName写入satelliteName、channelCode写入同名测点，类型均为TEXT。channelName继续表示通道名称，channelCode表示通道编码。历史记录的新测点缺失时返回null；新消息未提供这两个字段时保存空字符串。当前本地处理未配置这两个字段的来源，因此保持为空；RPC消息按实际返回值保存。

### IoTDB帧检查字段与页面分页

正常帧和异常帧在同一个`_frame`设备结构中统一保存`checkResult`和`checkResultName`，编码为0未知、1正确、2错误。上游`frameCheckStatus`为true时转换为正确，为false时转换为错误；实时遥测查询继续兼容历史记录中的`frameCheckStatus`。异常帧返回字段增加`satelliteName`和`channelName`，历史记录缺少对应测点时返回null。

`GET /processing/realtime/telemetry`、`GET /processing/processed`和`GET /processing/realtime/invalid`均增加`pageNum`和`pageSize`，默认分别为1和20，`pageSize`最大为200。响应`data`统一包含`pageNum`、`pageSize`、`total`和`records`。处理后参数查询使用所有参数统一保存的`valueText`作为`parameterValue`，避免不同物理量数据类型在IoTDB跨设备查询时发生冲突。

IoTDB设备路径将任务编号和采集接口编号放在卫星之前，统一格式为`root.db.task_{taskId}.interface_{interfaceId}.{satelliteCode}.tms.{channelCode}.{tmType}.{node}`。整帧节点使用`_frame`，参数节点使用参数代号`tmSymbol`。查询端直接使用任务和接口路径前缀缩小设备范围，不再展开整个`root.db`后通过测点筛选任务和接口。

### `GET /alarms/findData`

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "dataAlarmSource": [
      {
        "value": "COLLECTION",
        "label": "数据采集"
      },
      {
        "value": "PROCESSING",
        "label": "数据处理"
      },
      {
        "value": "CALIBRATION",
        "label": "数据校准"
      }
    ],
    "dataAlarmEventType": [
      {
        "value": "INTERFACE_SYNC",
        "label": "采集运行状态同步失败"
      },
      {
        "value": "INTERFACE_START",
        "label": "接口启动失败"
      },
      {
        "value": "INTERFACE_STOP",
        "label": "采集接口停止接收"
      },
      {
        "value": "COLLECTION_RECEIVE",
        "label": "接口接收异常"
      },
      {
        "value": "PACKET_PARSE",
        "label": "报文解析失败"
      },
      {
        "value": "FILE_RECEIVE",
        "label": "文件接收失败"
      },
      {
        "value": "PROCESS_QUEUE",
        "label": "处理队列已满"
      },
      {
        "value": "PROCESS_CONFIG",
        "label": "参数处理失败"
      },
      {
        "value": "RPC_FAILURE",
        "label": "远程处理失败"
      },
      {
        "value": "PARAMETER_RANGE",
        "label": "遥测参数越限"
      },
      {
        "value": "STORAGE_FAILURE",
        "label": "数据存储失败"
      },
      {
        "value": "MESSAGE_FAILURE",
        "label": "结果消息发送失败"
      },
      {
        "value": "FILE_RESULT",
        "label": "文件结果处理失败"
      },
      {
        "value": "CALIBRATION_OUTLIER",
        "label": "校准野值"
      },
      {
        "value": "CALIBRATION_RULE",
        "label": "校准规则异常"
      },
      {
        "value": "CALIBRATION_CACHE",
        "label": "校准缓存失败"
      },
      {
        "value": "CALIBRATION_PERSIST",
        "label": "校准记录落库失败"
      }
    ],
    "dataAlarmLevel": [
      {
        "value": 2,
        "label": "一般"
      },
      {
        "value": 4,
        "label": "严重"
      }
    ],
    "dataAlarmStatus": [
      {
        "value": 0,
        "label": "未处理"
      },
      {
        "value": 1,
        "label": "已处理"
      }
    ],
    "systemAlarmLevel": [
      {
        "value": 2,
        "label": "一般"
      },
      {
        "value": 3,
        "label": "严重"
      }
    ],
    "systemAlarmStatus": [
      {
        "value": 0,
        "label": "未恢复"
      },
      {
        "value": 1,
        "label": "已恢复"
      }
    ],
    "serviceStatus": [
      {
        "value": "RUNNING",
        "label": "运行中"
      },
      {
        "value": "DOWN",
        "label": "停止"
      },
      {
        "value": "ERROR",
        "label": "查询异常"
      }
    ],
    "metricCode": [
      {
        "value": "cpu_usage",
        "label": "CPU使用率"
      },
      {
        "value": "memory_usage",
        "label": "内存使用率"
      },
      {
        "value": "disk_usage",
        "label": "磁盘使用率"
      },
      {
        "value": "queue_depth",
        "label": "消息积压数量"
      }
    ],
    "heartbeatStatus": [
      {
        "value": "NORMAL",
        "label": "正常"
      },
      {
        "value": "WAITING",
        "label": "等待心跳"
      }
    ],
    "changeType": [
      {
        "value": "CREATED",
        "label": "新增"
      },
      {
        "value": "UPDATED",
        "label": "持续触发"
      },
      {
        "value": "RECOVERED",
        "label": "已恢复"
      }
    ],
    "systemAlarmType": [
      {
        "value": "cpu_usage",
        "label": "CPU使用率"
      },
      {
        "value": "memory_usage",
        "label": "内存使用率"
      },
      {
        "value": "disk_usage",
        "label": "磁盘使用率"
      },
      {
        "value": "queue_depth",
        "label": "消息积压数量"
      }
    ]
  }
}
```

### `GET /system/logs/findData`

无需入参。来源选项固定返回，不依赖任务或已有日志；筛选日志时使用选项中的英文 `value`，`label` 仅用于展示。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "logLevel": [
      {
        "value": "INFO",
        "label": "信息"
      },
      {
        "value": "WARN",
        "label": "警告"
      },
      {
        "value": "ERROR",
        "label": "错误"
      }
    ],
    "logSource": [
      {
        "value": "COLLECTION",
        "label": "数据采集"
      },
      {
        "value": "PROCESSING",
        "label": "数据处理"
      },
      {
        "value": "CALIBRATION",
        "label": "数据校准"
      },
      {
        "value": "VISUALIZATION",
        "label": "数据可视化"
      },
      {
        "value": "TELEMETRY_FILTER",
        "label": "遥测参数筛选"
      },
      {
        "value": "ALARM_MANAGEMENT",
        "label": "告警管理"
      },
      {
        "value": "USER_MANAGEMENT",
        "label": "用户管理"
      },
      {
        "value": "ROLE_MANAGEMENT",
        "label": "角色管理"
      },
      {
        "value": "SATELLITE_CATALOG",
        "label": "卫星目录"
      },
      {
        "value": "AUTHENTICATION",
        "label": "用户认证"
      }
    ]
  }
}
```
