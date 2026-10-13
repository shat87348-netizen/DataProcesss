package com.example.dataadmin.mapper;

import com.example.dataadmin.dto.visualization.VisualizationConfigFile;
import com.example.dataadmin.entity.VisualizationItemRecord;
import com.example.dataadmin.entity.VisualizationParameterRecord;
import com.example.dataadmin.entity.CollectInterfaceConfig;
import com.example.dataadmin.entity.CollectProtocolConfig;
import com.example.dataadmin.entity.MonitorWidgetConfigItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 配置文件专用批量查询，所有数据库语句维护在映射文件中。 */
public interface VisualizationConfigMapper {
    /** 检查目标任务存在，避免把配置导入任意任务编号。 */
    String findTask(@Param("taskId") String taskId);
    /** 直接读取当前用户整页组件的场景勾选，不依赖组件数据项是否存在。 */
    List<VisualizationParameterRecord> findSelectedParameters(@Param("taskId") String taskId,
            @Param("userId") Long userId);
    /** 一次读取整页数据项及有效采集接口、协议名称。 */
    List<VisualizationItemRecord> findItems(@Param("taskId") String taskId, @Param("userId") Long userId);
    /** 按业务标识分批匹配目标任务中的有效遥测参数。 */
    List<VisualizationParameterRecord> findParameterMatches(@Param("taskId") String taskId,
            @Param("parameters") List<VisualizationConfigFile.Parameter> parameters);
    /** 只匹配实际引用的接口名称；写入前锁住有效关联，防止导入期间被删除。 */
    List<CollectInterfaceConfig> findInterfaceMatches(@Param("taskId") String taskId,
            @Param("names") List<String> names, @Param("lock") boolean lock);
    /** 只匹配实际引用的协议名称；写入前锁住有效关联，防止导入期间被删除。 */
    List<CollectProtocolConfig> findProtocolMatches(@Param("taskId") String taskId,
            @Param("names") List<String> names, @Param("lock") boolean lock);
    /** 按批恢复已有数据项，不需要返回源数据项主键。 */
    int insertItems(@Param("items") List<MonitorWidgetConfigItem> items);
    /** 按批恢复新组件自己的参数勾选。 */
    int insertSelections(@Param("taskId") String taskId, @Param("widgetId") Long widgetId,
            @Param("selectionType") int selectionType,
            @Param("parameters") List<VisualizationParameterRecord> parameters);
}
