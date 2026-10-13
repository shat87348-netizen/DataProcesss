package com.example.dataadmin.enums;
/** 人工操作范围白名单，查询、刷新和后台周期任务不生成操作日志。 */
public enum OperationAction {
    COLLECTION_INTERFACES_CREATE("/collection/interfaces/create", SystemLogSource.COLLECTION, "创建采集接口"),
    COLLECTION_INTERFACES_UPDATE("/collection/interfaces/update", SystemLogSource.COLLECTION, "修改采集接口"),
    COLLECTION_INTERFACES_DELETE("/collection/interfaces/delete", SystemLogSource.COLLECTION, "删除采集接口"),
    COLLECTION_INTERFACES_ENABLED("/collection/interfaces/enabled", SystemLogSource.COLLECTION, "修改采集接口启用状态"),
    COLLECTION_PROTOCOLS_CREATE("/collection/protocols/create", SystemLogSource.COLLECTION, "创建协议配置"),
    COLLECTION_PROTOCOLS_UPDATE("/collection/protocols/update", SystemLogSource.COLLECTION, "修改协议配置"),
    COLLECTION_PROTOCOLS_DELETE("/collection/protocols/delete", SystemLogSource.COLLECTION, "删除协议配置"),
    COLLECTION_PROTOCOLS_ENABLED("/collection/protocols/enabled", SystemLogSource.COLLECTION, "修改协议配置启用状态"),
    CALIBRATION_CHANNELS_CREATE("/calibration/channels/create", SystemLogSource.CALIBRATION, "创建校准通道"),
    CALIBRATION_CHANNELS_UPDATE("/calibration/channels/update", SystemLogSource.CALIBRATION, "修改校准通道"),
    CALIBRATION_CHANNELS_DELETE("/calibration/channels/delete", SystemLogSource.CALIBRATION, "删除校准通道"),
    VISUALIZATION_WIDGETS_CREATE("/visualization/widgets/create", SystemLogSource.VISUALIZATION, "创建页面组件"),
    VISUALIZATION_WIDGETS_UPDATE("/visualization/widgets/update", SystemLogSource.VISUALIZATION, "修改页面组件"),
    VISUALIZATION_WIDGETS_DELETE("/visualization/widgets/delete", SystemLogSource.VISUALIZATION, "删除页面组件"),
    VISUALIZATION_CONFIG_IMPORT("/visualization/config/import", SystemLogSource.VISUALIZATION, "导入可视化页面配置"),
    VISUALIZATION_ITEMS_CREATE("/visualization/items/create", SystemLogSource.VISUALIZATION, "创建组件数据项"),
    VISUALIZATION_ITEMS_UPDATE("/visualization/items/update", SystemLogSource.VISUALIZATION, "修改组件数据项"),
    VISUALIZATION_ITEMS_DELETE("/visualization/items/delete", SystemLogSource.VISUALIZATION, "删除组件数据项"),
    SYSTEM_USERS_CREATE("/system/users/create", SystemLogSource.USER_MANAGEMENT, "创建用户"),
    SYSTEM_USERS_UPDATE("/system/users/update", SystemLogSource.USER_MANAGEMENT, "修改用户"),
    SYSTEM_USERS_DELETE("/system/users/delete", SystemLogSource.USER_MANAGEMENT, "删除用户"),
    SYSTEM_USERS_STATUS("/system/users/status", SystemLogSource.USER_MANAGEMENT, "修改用户状态"),
    SYSTEM_ROLES_CREATE("/system/roles/create", SystemLogSource.ROLE_MANAGEMENT, "创建角色"),
    SYSTEM_ROLES_UPDATE("/system/roles/update", SystemLogSource.ROLE_MANAGEMENT, "修改角色"),
    SYSTEM_ROLES_DELETE("/system/roles/delete", SystemLogSource.ROLE_MANAGEMENT, "删除角色"),
    SYSTEM_ROLES_STATUS("/system/roles/status", SystemLogSource.ROLE_MANAGEMENT, "修改角色状态"),
    SATELLITE_MANAGEMENT_SATELLITES_CREATE("/satellite-management/satellites/create", SystemLogSource.SATELLITE_CATALOG, "创建卫星"),
    SATELLITE_MANAGEMENT_SATELLITES_UPDATE("/satellite-management/satellites/update", SystemLogSource.SATELLITE_CATALOG, "修改卫星"),
    SATELLITE_MANAGEMENT_SATELLITES_DELETE("/satellite-management/satellites/delete", SystemLogSource.SATELLITE_CATALOG, "删除卫星"),
    SATELLITE_MANAGEMENT_SATELLITES_ENABLED("/satellite-management/satellites/enabled", SystemLogSource.SATELLITE_CATALOG, "修改卫星启用状态"),
    SATELLITE_MANAGEMENT_CHANNELS_ENABLED("/satellite-management/channels/enabled", SystemLogSource.SATELLITE_CATALOG, "修改卫星通道启用状态"),
    SYSTEM_ROLES_MENUS("/system/roles/menus", SystemLogSource.ROLE_MANAGEMENT, "修改角色权限"),
    SYSTEM_USERS_RESET_PASSWORD("/system/users/reset-password", SystemLogSource.USER_MANAGEMENT, "重置用户密码"),
    PROCESSING_RULE_CONFIG_UPDATE("/processing/rule-config/update", SystemLogSource.PROCESSING, "修改任务处理规则"),
    PROCESSING_RULES_BATCH_UPDATE("/processing/rules/batch-update", SystemLogSource.PROCESSING, "批量修改遥测参数配置"),
    PROCESSING_RULES_IMPORT("/processing/rules/import", SystemLogSource.PROCESSING, "导入遥测参数配置"),
    SATELLITE_MANAGEMENT_TABLES_UPLOAD("/satellite-management/tables/upload", SystemLogSource.SATELLITE_CATALOG, "上传卫星参数表"),
    VISUALIZATION_ITEMS_SELECTED("/visualization/items/selected", SystemLogSource.VISUALIZATION, "修改组件数据项勾选"),
    ALARMS_DATA_STATUS("/alarms/data/status", SystemLogSource.ALARM_MANAGEMENT, "修改数据告警处理状态"),
    AUTH_LOGIN("/auth/login", SystemLogSource.AUTHENTICATION, "用户登录"),
    AUTH_SSO_LOGIN("/auth/sso/login", SystemLogSource.AUTHENTICATION, "统一身份登录"),
    PROCESSING_TELEMETRY_FILTER_SELECTION_UPDATE("/processing/telemetry-filter-selection/update", SystemLogSource.TELEMETRY_FILTER, "修改遥测参数筛选"),
    AUTH_LOGOUT("/auth/logout", SystemLogSource.AUTHENTICATION, "用户退出");

    /** 人工写操作的接口路径。 */
    private final String path;
    /** 统一日志来源枚举，避免每个操作重复维护来源名称。 */
    private final SystemLogSource source;
    /** 操作的中文描述。 */
    private final String label;

    OperationAction(String path, SystemLogSource source, String label) {
        this.path = path;
        this.source = source;
        this.label = label;
    }

    public SystemLogSource getSource() {
        return source;
    }

    public String getLabel() {
        return label;
    }

    /** 严格按写操作路径匹配，避免把查询型接口记为人工修改。 */
    public static OperationAction find(String path) {
        for (OperationAction action : values()) {
            // 只识别已配置的人工操作路径，来源直接复用该操作引用的枚举。
            if (action.path.equals(path)) {
                return action;
            }
        }
        return null;
    }
}
