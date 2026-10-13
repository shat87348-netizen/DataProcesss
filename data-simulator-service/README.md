# 独立模拟源服务

新模块独立管理PDXP/UDP和FEP/TCP模拟源，旧模拟源、管理服务、处理服务保持原样。只依赖公共模块的响应对象、分页对象和FEP编解码工具。

## 部署

1. 在金仓PostgreSQL兼容模式数据库执行仓库根目录的 [sql/data-simulator-service.sql](../sql/data-simulator-service.sql)。同一文件用于新库建表及已有模拟源表补字段，不修改旧模块数据表，不再单独维护升级脚本。
2. 将仓库根目录的 [nacos-config/data-simulator-service.yaml](../nacos-config/data-simulator-service.yaml) 内容导入Nacos：配置标识为 `data-simulator-service.yaml`，分组默认为 `DEFAULT_GROUP`，格式为YAML。直接按环境填写数据库连接、账号和密码，不使用环境变量占位符。默认数据库地址为 `jdbc:kingbase8://127.0.0.1:54321/data_processing`，用户名为 `system`，密码按配置文件当前值读取，部署时核对实际凭据。
3. 在仓库根目录执行 `mvn -pl data-simulator-service -am package -DskipTests`。
4. 执行 `java -jar data-simulator-service/target/data-simulator-service-1.0.0-SNAPSHOT.jar`，默认端口8085，需调整时修改Nacos配置中的 `server.port`。
5. 将 `docs/apifox-openapi.json` 导入Apifox，调用 `/simulator/findData` 获取枚举与默认参数。

模块内仅保留 `src/main/resources/bootstrap.yml` 作为Nacos引导配置，连接信息直接填写明确值：地址默认 `127.0.0.1:8848`，账号和密码默认均为本地开发值 `nacos`，命名空间默认空字符串（公共命名空间），分组默认 `DEFAULT_GROUP`。指定其他命名空间时填写其ID；修改地址、命名空间或分组时，同步调整配置读取与服务注册对应字段。

Nacos配置应在启动前发布，模块不再内置业务配置兜底。为保证线程池容量、进程锁及执行快照一致，关闭动态刷新，修改配置后重启服务生效。本仓库中的YAML是待导入的配置文件，修改文件本身不会自动发布到Nacos。

同一个数据库只允许一个本模块服务进程管理发送任务，该进程内可并发运行多个模拟源。启动时持有数据库会话锁，第二个服务进程会拒绝启动，不会清理第一个进程的记录；另有本机文件锁。持锁连接失效时停止发送并拒绝新启动，恢复数据库后需重启本服务。此版本不提供多节点调度或自动故障接管。

发送端不会自动启动、停止接收端。接收端应提前开启，并支持相应协议与并发连接。该服务没有新增登录鉴权，作为内部服务部署；需要对外提供入口时接入现有网关访问控制。

## 数据结构与流程

| 表 | 内容 |
|---|---|
| simulator_source_config | 名称、协议、文件路径、目标地址、间隔、循环、备注、逻辑删除和时间 |
| simulator_pdxp_config | 仅PDXP实例的传输头、自定义数据及包头字段，与基础配置一对一 |
| simulator_run_record | 每次执行的配置快照、状态、发送统计、开始结束时间及异常 |

新建只保存配置。PDXP基础配置和参数使用同一事务保存，FEP只保存基础配置。有效名称唯一；目标地址和文件路径可以重复。协议创建后不能切换。

启动时锁定配置行、校验文件、记录配置快照并提交事务，随后创建独立发送任务。每个实例独立持有文件、套接字、包序号和统计。同一个源只允许一条活动运行记录，数据库条件唯一索引与内存管理器共同防重。

运行中禁止修改、删除。停止请求使用模拟源编号和运行编号，旧运行的停止请求不会影响新运行。非循环发送完成后状态为“已完成”，主动停止为“已停止”，失败为“异常”。重启后旧活动记录标为异常，不自动重新发送。

分页列表可按名称和状态筛选。状态筛选值：0未启动、1启动中、2运行中、3停止中、4已停止、5已完成、6异常；查询响应中的 `runStatus` 为中文。协议和传输编码保持与旧模块一致，新模块只定义支持的组合，不依赖旧管理服务的业务枚举类。

## PDXP配置示例

向 `POST /simulator/sources/create` 提交：

```json
{
  "sourceName": "遥测模拟源一",
  "transferProtocol": 2,
  "transferType": 1,
  "filePath": "D:/模拟数据/telemetry.dat",
  "targetHost": "127.0.0.1",
  "targetPort": 9001,
  "sendIntervalMillis": 500,
  "loopEnabled": 1,
  "pdxp": {
    "inputFrameLength": 512,
    "transportHeaderLength": 2,
    "telemetryHeaderLength": 4,
    "customDataLength": 5,
    "transportHeader": "1234",
    "customData": "0102030405",
    "ver": "80",
    "mid": "0001",
    "sid": "00000001",
    "did": "00000001",
    "bid": "00000001",
    "initialNo": "00000000",
    "flag": "00"
  }
}
```

四个长度均为每个PDXP模拟源的独立配置。文件必须存在、可读、非空且大小为 `inputFrameLength` 的整数倍。组装顺序为：传输头、32字节PDXP包头、自定义数据、原帧剩余部分、原帧开头指定长度的帧头。总长度为 `inputFrameLength + transportHeaderLength + 32 + customDataLength`，PDXP数据域长度为 `inputFrameLength + customDataLength`。默认配置仍每帧读取512字节并发送551字节。

`inputFrameLength` 范围1到65475，`telemetryHeaderLength` 范围0到输入帧长度，传输头及自定义数据长度范围0到65475，最终UDP报文总长度不能超过65507。传输头及自定义内容必须恰好包含对应字节长度两倍的十六进制字符；长度为零时填写空字符串。原帧头移位不改变字节总数。报文较大时可能发生IP分片，应按接收端能力选择长度。

十六进制字段逐项小端写入。日期时标自动生成，包序号从配置初值开始，循环读文件时继续递增，重新启动时重置。输入文件的帧头与数据只调整位置，不反转字节顺序。已运行初版建表脚本的环境也统一执行仓库根目录的 `sql/data-simulator-service.sql` 补充字段，默认值保持原行为。

## FEP配置示例

```json
{
  "sourceName": "文件模拟源一",
  "transferProtocol": 3,
  "transferType": 2,
  "filePath": "D:/模拟数据/example.dat",
  "targetHost": "127.0.0.1",
  "targetPort": 19003,
  "sendIntervalMillis": 10,
  "loopEnabled": 0
}
```

FEP不允许提交PDXP参数，不创建PDXP参数记录。公共参数位于Nacos配置 `data-simulator-service.yaml` 的 `simulator` 下，对应仓库文件 `nacos-config/data-simulator-service.yaml`：`data-unit-length=4096`、`connect-timeout-millis=5000`、`response-timeout-millis=10000`。接收端必须使用相同分块长度；当前旧接收端默认为4096，因此通常不修改该值。

文件支持空文件，长度不能超过2147483647字节，文件名需符合公共FEP协议的64字节字段约束。接收方可指定数据单元号续传，文件长度为分块整数倍时补发空结束单元。每轮独立建立TCP连接并验证结束确认。开启循环后成功完成一轮才开始下一轮，轮间至少等待1毫秒；接收端返回文件已存在也视为成功一轮，不代表产生了新的接收业务记录。

## 操作接口

| 方法与路径 | 说明 |
|---|---|
| GET /simulator/sources | 分页，参数name、status、pageNum、pageSize |
| GET /simulator/sources/{id} | 完整详情 |
| POST /simulator/sources/create | 新建，返回模拟源编号 |
| POST /simulator/sources/update | 完整更新，除新建字段外必须包含id |
| POST /simulator/sources/delete | 请求为 `{"id":1}`，逻辑删除 |
| POST /simulator/sources/start | 请求为 `{"id":1}`，返回运行编号 |
| POST /simulator/sources/stop | 请求为 `{"id":1,"runId":1}` |
| GET /simulator/sources/{id}/runs | 分页历史，删除后仍可查询 |
| GET /simulator/findData | 中文枚举与协议默认值 |

统一返回 `code`、`message` 和可选 `data`。参数错误返回HTTP 400，名称或状态冲突返回409，未预期错误返回500。修改为完整更新，省略协议可选参数会恢复默认值，不是局部补丁。

## 统计与运行限制

- `sentUnitCount`：PDXP帧数，或FEP文件数据单元数（包括结束空单元）。
- `sentByteCount`：成功写入套接字的应用层协议字节数，FEP包括发送请求，排除TCP/IP或UDP/IP网络头。
- `completedLoopCount`：完成文件读取或文件交换轮数。
- 统计每2秒保存一次，停止和结束时再次保存；数据库短暂不可用时保留待保存实例重试。强制退出可能损失最后一次保存后的统计。
- UDP发送成功不代表接收确认，FEP结束确认也不代表接收侧后续入库处理完成。
- 默认最多16个并发实例，可在Nacos配置中修改 `simulator.max-concurrent`（1到256），发布并重启服务后生效；达到上限拒绝新启动，不隐式排队。
- 运行期间不要替换或修改源文件；快照保存配置，不保存源文件内容。
- 发送间隔允许0到86400000毫秒；目标端口允许1到65535。

## 已执行验证

未新增单元测试或测试依赖。已执行公共模块与旧模拟源已有测试；对新模块使用独立金仓PostgreSQL兼容数据库和本机UDP/TCP接收端进行接口验证，验证记录见 `docs/verification.md`。
