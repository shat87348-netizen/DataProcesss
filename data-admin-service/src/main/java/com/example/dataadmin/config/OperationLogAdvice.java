package com.example.dataadmin.config;
import com.example.common.auth.LoginUser;
import com.example.common.response.ApiResponse;
import com.example.dataadmin.enums.OperationAction;
import com.example.dataadmin.enums.SystemLogLevel;
import com.example.dataadmin.mapper.*;
import com.example.dataadmin.service.OperationLogService;
import com.example.security.context.LoginUserContext;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
/** 在实际接口结果返回时记录人工操作，白名单只提取业务字段，不保存密码和令牌。 */
@ControllerAdvice
public class OperationLogAdvice extends RequestBodyAdviceAdapter
        implements ResponseBodyAdvice<Object>, HandlerInterceptor, WebMvcConfigurer {
    /** 请求属性贯穿事务提交后的接口同步和最终响应。 */
    public static final String SYNC_RESULT = "操作日志_接口同步结果";
    public static final String SYNC_FAILED = "操作日志_接口同步失败";
    public static final String SELECTION_RECORDED = "操作日志_参数勾选已处理";
    private static final String BODY = "操作日志_请求对象";
    private static final String BEFORE = "操作日志_修改前快照";
    public static final String ACTOR = "操作日志_操作人快照";
    private final OperationLogService logs;
    private final CollectInterfaceConfigMapper interfaces;
    private final CollectProtocolConfigMapper protocols;
    private final CalibChannelConfigMapper channels;
    private final MonitorDashboardWidgetMapper widgets;
    private final MonitorWidgetConfigItemMapper items;
    private final SysUserMapper users;
    private final SysRoleMapper roles;
    private final DmAlarmEventMapper alarms;
    @org.springframework.beans.factory.annotation.Autowired
    private TelemetryParseRuleConfigMapper rules;
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.dataadmin.service.SatelliteManagementService satelliteService;
    /** 中文字段白名单既控制展示，也防止账号密码等敏感字段进入详情。 */
    private static final Map<String, String> LABELS = new LinkedHashMap<String, String>();
    static {
        String[][] pairs = {{"id","对象ID"},{"code","对象代号"},{"name","对象名称"},{"channelId","通道ID"},
            {"enable","启用状态"},{"alarmStatus","告警人工处理状态"},{"alarmStatusName","告警人工处理状态中文名称"},{"taskId","试验任务编号"},{"interfaceName","接口名称"},
            {"configName","协议配置名称"},{"channelName","校准通道名称"},{"widgetTitle","组件标题"},
            {"widgetKey","组件标识"},{"widgetId","组件ID"},{"itemName","数据项名称"},{"itemCode","数据项代号"},
            {"username","账号"},{"nickname","用户名称"},{"roleName","角色名称"},{"roleId","角色ID"},
            {"roleIds","角色ID列表"},{"menuIds","权限菜单ID列表"},{"deviceSatelliteId","设备卫星ID"},
            {"telemetryName","参数名称"},{"telemetryCode","遥测代号"},{"systemName","所属系统路径"},
            {"host","监听地址"},{"port","监听端口"},{"enabled","启用状态"},{"status","状态"},
            {"isSelected","勾选状态"},{"normalRange","正常范围"},{"detectMethod","检测方法"},
            {"parameterRangeCheckEnabled","参数范围检查开关"},{"rpcEnabled","远程处理开关"},
            {"protocolConfigId","协议配置ID"},{"interfaceId","接口ID"},{"parseRuleId","参数解析配置ID"},
            {"alarmMsg","告警信息"},{"sourceName","告警来源"},{"satelliteCode","卫星编码"},
            {"channelCode","通道编码"},{"formulaType","公式类型"},{"formulaDesc","处理公式"},
            {"processParam","处理参数"},{"normalValue","正常值范围"},{"warningValue","预警值范围"},
            {"alarmFlag","参数报警开关"},{"storeFlag","参数存储开关"},{"calibrationFormula","校准公式"},
            {"selectionType","筛选框类型"},{"targetId","筛选框目标ID"},{"checked","勾选状态"},
            {"systemId","所属系统ID"},{"bitWidth","参数位宽"},{"decimalPlaces","小数位数"},{"satelliteName","卫星名称"},{"tableType","参数表类型"}};
        for (String[] pair : pairs) { LABELS.put(pair[0], pair[1]); }
    }
    public OperationLogAdvice(OperationLogService logs, CollectInterfaceConfigMapper interfaces,
            CollectProtocolConfigMapper protocols, CalibChannelConfigMapper channels,
            MonitorDashboardWidgetMapper widgets, MonitorWidgetConfigItemMapper items,
            SysUserMapper users, SysRoleMapper roles, DmAlarmEventMapper alarms) {
        this.logs = logs; this.interfaces = interfaces; this.protocols = protocols;
        this.channels = channels; this.widgets = widgets; this.items = items;
        this.users = users; this.roles = roles; this.alarms = alarms;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(this); }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 退出操作执行前保存登录身份，业务可能在随后清除会话。
        request.setAttribute(ACTOR, LoginUserContext.get());
        return true;
    }
    @Override public boolean supports(MethodParameter parameter, Type type,
            Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter,
            Type type, Class<? extends HttpMessageConverter<?>> converter) {
        HttpServletRequest request = request();
        if (request != null && action(request) != null) {
            request.setAttribute(BODY, body);
            request.setAttribute(ACTOR, LoginUserContext.get());
            // 修改或删除前读取对象名称，删除完成后仍可定位原对象。
            Long targetId = number(property(body, "id"));
            if (targetId == null) { targetId = number(property(body, "roleId")); }
            Object before = snapshot(request.getServletPath(), targetId);
            try {
                if ("/processing/rules/batch-update".equals(request.getServletPath())) {
                    StringBuilder snapshots = new StringBuilder();
                    Object batch = property(body, "items");
                    if (batch instanceof Iterable) {
                        for (Object item : (Iterable<?>) batch) {
                            snapshots.append(describe(rules.findActiveById(text(property(body, "taskId")), number(property(item, "id"))))).append("\n");
                        }
                    }
                    before = snapshots.toString();
                }
                if (request.getServletPath().startsWith("/satellite-management/")
                        && property(body, "code") != null && !request.getServletPath().endsWith("/create")) {
                    before = satelliteService.getSatellite(text(property(body, "code")));
                }
                if ("/processing/telemetry-filter-selection/update".equals(request.getServletPath())) {
                    before = rules.findActiveById(text(property(body, "taskId")), number(property(body, "parseRuleId")));
                }
            } catch (Exception ignored) {
                // 业务层仍负责验证；日志上下文取得失败不能替代业务错误。
            }
            request.setAttribute(BEFORE, before);
        }
        return body;
    }
    @Override public boolean supports(MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter) {
        return true;
    }
    @Override public Object beforeBodyWrite(Object body, MethodParameter parameter, MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest input, ServerHttpResponse output) {
        HttpServletRequest request = request();
        OperationAction action = request == null ? null : action(request);
        if (action == null || !(body instanceof ApiResponse)) { return body; }
        ApiResponse<?> result = (ApiResponse<?>) body;
        // 参数勾选由业务层记录真实变化，成功响应不能重复写一条笼统日志。
        if (Boolean.TRUE.equals(request.getAttribute(SELECTION_RECORDED))
                && ("/visualization/items/selected".equals(request.getServletPath())
                    || "/processing/telemetry-filter-selection/update".equals(request.getServletPath()))
                && Integer.valueOf(200).equals(result.getCode())) {
            return body;
        }
        Object command = request.getAttribute(BODY);
        Object before = request.getAttribute(BEFORE);
        Object resultData = result.getData();
        Long id = number(property(command, "id"));
        if (id == null) { id = number(property(command, "roleId")); }
        if (id == null && request.getServletPath().endsWith("/create")) {
            id = number(resultData);
            if (id == null) { id = number(property(resultData, "id")); }
        }
        Object after = Integer.valueOf(200).equals(result.getCode()) ? snapshot(request.getServletPath(), id) : null;
        String detail = "操作：" + action.getLabel() + "；请求路径：" + request.getServletPath()
                + "；对象ID：" + id + "；执行结果：" + result.getMessage()
                + "\n修改前：" + describe(before) + "\n提交内容：" + describe(command)
                + "\n修改后：" + describe(after);
        // 参数表导入只保存文件名和业务参数，不保存上传文件内容。
        if (request instanceof MultipartHttpServletRequest) {
            MultipartHttpServletRequest multipart = (MultipartHttpServletRequest) request;
            for (org.springframework.web.multipart.MultipartFile file : multipart.getFileMap().values()) {
                detail += "\n导入文件：" + file.getOriginalFilename() + "；文件大小：" + file.getSize();
            }
        }
        for (Map.Entry<String, String> field : LABELS.entrySet()) {
            String value = request.getParameter(field.getKey());
            if (value != null) { detail += "；" + field.getValue() + "：" + value; }
        }
        boolean failedSync = Boolean.TRUE.equals(request.getAttribute(SYNC_FAILED));
        Object syncResult = request.getAttribute(SYNC_RESULT);
        if (syncResult != null) { detail += "\n实际运行结果：" + syncResult; }
        SystemLogLevel level = Integer.valueOf(200).equals(result.getCode())
                ? (failedSync ? SystemLogLevel.WARN : SystemLogLevel.INFO)
                : (result.getCode() != null && result.getCode() >= 500 ? SystemLogLevel.ERROR : SystemLogLevel.WARN);
        String taskId = text(property(command, "taskId"));
        if (taskId == null) { taskId = text(property(before, "taskId")); }
        if (taskId == null) { taskId = request.getParameter("taskId"); }
        LoginUser actor = (LoginUser) request.getAttribute(ACTOR);
        if (actor == null) { actor = LoginUserContext.get(); }
        // 普通登录通过账号查询操作人，登录结果令牌不会写入日志。
        if (actor == null && "/auth/login".equals(request.getServletPath())) {
            try {
                com.example.dataadmin.entity.SysUser user = users.findByUsername(text(property(command, "username")));
                if (user != null) { actor = new LoginUser(); actor.setUserId(user.getId()); actor.setUsername(user.getUsername()); }
            } catch (Exception ignored) {
                // 身份查询失败不能改变原登录结果，详情仍保留提交的账号。
            }
        }
        String content = action.getLabel() + (level == SystemLogLevel.INFO ? "成功" : level == SystemLogLevel.WARN ? "未完全完成" : "失败");
        logs.record(taskId, action.getSource(), level, content, detail, actor);
        return body;
    }
    /** 已有Mapper查询对象快照，查询异常不能遮蔽业务响应。 */
    private Object snapshot(String path, Long id) {
        if (id == null) { return null; }
        try {
            if (path.startsWith("/collection/interfaces/")) { return interfaces.findById(id); }
            if (path.startsWith("/collection/protocols/")) { return protocols.findById(id); }
            if (path.startsWith("/calibration/channels/")) { return channels.findById(id); }
            if (path.startsWith("/visualization/widgets/")) { return widgets.findById(id); }
            if (path.startsWith("/visualization/items/")) { return items.findById(id); }
            if (path.startsWith("/system/users/")) { return users.findById(id); }
            if (path.startsWith("/system/roles/")) { return roles.findById(id); }
            if (path.startsWith("/alarms/data/")) { return alarms.findById(id); }
        } catch (Exception ignored) {
            // 对象未取得时明确为空，不伪造名称或影响原操作。
        }
        return null;
    }
    private OperationAction action(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) ? OperationAction.find(request.getServletPath()) : null;
    }
    /** 只输出白名单中的中文业务字段，不遍历完整请求对象。 */
    private String describe(Object bean) {
        if (bean == null) { return "未取得对象快照"; }
        if (bean instanceof String) { return (String) bean; }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> field : LABELS.entrySet()) {
            Object value = property(bean, field.getKey());
            if (value != null) {
                // 已有枚举字段优先返回中文，便于直接阅读操作详情。
                String display = String.valueOf(value);
                if ("selectionType".equals(field.getKey())) {
                    try { display = com.example.dataadmin.enums.BusinessEnums.SelectionType.fromCode(Integer.valueOf(display)).getLabel(); }
                    catch (Exception ignored) { /* 无效请求保留原编码用于排查。 */ }
                } else if ("enabled".equals(field.getKey()) || "enable".equals(field.getKey()) || field.getKey().endsWith("Enabled")) {
                    display = "1".equals(display) || "true".equals(display) ? "已启用"
                            : "0".equals(display) || "false".equals(display) ? "已禁用" : display;
                } else if ("checked".equals(field.getKey())) {
                    display = "true".equals(display) ? "已勾选" : "未勾选";
                }
                text.append(field.getValue()).append("：").append(display).append("；");
            }
        }
        Object nested = property(bean, "info");
        if (nested != null) { text.append("\n新配置：").append(describe(nested)); }
        Object channels = property(bean, "tmChannels");
        if (channels instanceof Iterable) {
            for (Object channel : (Iterable<?>) channels) { text.append("\n卫星通道：").append(describe(channel)); }
        }
        Object batch = property(bean, "items");
        if (batch instanceof Iterable) {
            for (Object item : (Iterable<?>) batch) { text.append("\n参数修改项：").append(describe(item)); }
        }
        return text.toString();
    }
    private Object property(Object bean, String name) {
        if (bean == null) { return null; }
        try {
            BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
            return wrapper.isReadableProperty(name) ? wrapper.getPropertyValue(name) : null;
        } catch (Exception ignored) { return null; }
    }
    private String text(Object value) { return value == null ? null : value.toString(); }
    private Long number(Object value) {
        try { return value == null ? null : Long.valueOf(value.toString()); } catch (NumberFormatException ignored) { return null; }
    }
    private HttpServletRequest request() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes
                ? ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest() : null;
    }
}
