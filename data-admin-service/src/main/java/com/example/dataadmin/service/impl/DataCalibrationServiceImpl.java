package com.example.dataadmin.service.impl;

import com.example.common.response.PageResult;
import com.example.dataadmin.entity.CalibChannelConfig;
import com.example.dataadmin.entity.CalibRecord;
import com.example.dataadmin.entity.CalibRecordDetail;
import com.example.dataadmin.enums.BusinessEnums;
import com.example.dataadmin.enums.EnumData;
import com.example.dataadmin.mapper.CalibChannelConfigMapper;
import com.example.dataadmin.mapper.CalibRecordDetailMapper;
import com.example.dataadmin.mapper.CalibRecordMapper;
import com.example.dataadmin.service.CollectInterfaceRuntimeSyncService;
import com.example.dataadmin.service.DataCalibrationService;
import com.example.dataadmin.vo.calibration.CalibRecordDetailVO;
import com.example.dataadmin.vo.calibration.CalibrationOverviewVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据校准页面业务实现。
 *
 * 查询默认使用只读事务，新增、修改和删除操作单独开启写事务。
 */
@Service
@Transactional(readOnly = true)
public class DataCalibrationServiceImpl implements DataCalibrationService {

    /** 校验正常范围中的两个数值边界。 */
    private static final Pattern NORMAL_RANGE = Pattern.compile(
            "^\\s*([-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))\\s*"
                    + "(?:~|～|至|—|–|-)\\s*"
                    + "([-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)).*$");

    /** 校准通道配置数据访问组件。 */
    private final CalibChannelConfigMapper channelMapper;

    /** 校准记录数据访问组件。 */
    private final CalibRecordMapper recordMapper;

    /** 校准记录野值详情数据访问组件。 */
    private final CalibRecordDetailMapper recordDetailMapper;

    /** 采集接口运行配置同步组件。 */
    private final CollectInterfaceRuntimeSyncService runtimeSyncService;

    public DataCalibrationServiceImpl(
            CalibChannelConfigMapper channelMapper,
            CalibRecordMapper recordMapper,
            CalibRecordDetailMapper recordDetailMapper,
            CollectInterfaceRuntimeSyncService runtimeSyncService) {
        this.channelMapper = channelMapper;
        this.recordMapper = recordMapper;
        this.recordDetailMapper = recordDetailMapper;
        this.runtimeSyncService = runtimeSyncService;
    }

    /** 查询当前试验任务累计检出的野值数量。 */
    @Override
    public CalibrationOverviewVO getOverview(String taskId) {
        requireTaskId(taskId);
        // 汇总全部未删除校准记录，没有记录时Mapper统一返回零。
        CalibrationOverviewVO result = new CalibrationOverviewVO();
        result.setOutlierCount(recordMapper.sumOutlierCount(taskId));
        return result;
    }

    /** 查询指定试验任务的校准通道列表。 */
    @Override
    public List<CalibChannelConfig> listChannelConfigs(String taskId) {
        // 校准通道属于具体试验任务，列表查询必须进行任务隔离。
        requireTaskId(taskId);
        return channelMapper.findAllByTaskId(taskId);
    }

    /** 查询校准通道详情。 */
    @Override
    public CalibChannelConfig getChannelConfig(Long id) {
        return requireChannelConfig(id);
    }

    /** 新增校准通道配置。 */
    @Override
    @Transactional
    public Long createChannelConfig(CalibChannelConfig config) {
        // 新增时补充开关默认值，再执行完整配置校验。
        fillChannelDefaults(config);
        normalizeChannelText(config);
        validateChannelConfig(config);
        validateUniqueCalibrationFormula(config);
        if (channelMapper.insert(config) <= 0) {
            throw new IllegalArgumentException("校准通道新增失败");
        }
        // 通道创建完成后立即创建初始校准记录，事务失败时整体回滚。
        createInitialCalibRecord(config);
        // 事务提交后刷新处理模块，使新通道立即参与后续遥测帧校准。
        runtimeSyncService.syncAfterCommit();
        return config.getId();
    }

    /** 更新校准通道配置。 */
    @Override
    @Transactional
    public boolean updateChannelConfig(CalibChannelConfig config) {
        // 使用数据库现有值补齐未传字段，兼容局部修改并校验修改后的完整配置。
        CalibChannelConfig current = requireChannelConfig(config.getId());
        normalizeChannelText(config);
        mergeChannelConfig(current, config);
        validateChannelConfig(current);
        validateUniqueCalibrationFormula(current);
        if (channelMapper.update(config) <= 0) {
            return false;
        }
        // 汇总记录与校准通道是一对一关系，通道改名时同步展示名称。
        if (recordMapper.updateSummaryByChannelId(
                current.getId(), current.getTaskId(), current.getChannelName()) <= 0) {
            throw new IllegalArgumentException("校准通道对应的校准记录不存在");
        }
        // 事务提交后刷新处理模块中的校准配置快照。
        runtimeSyncService.syncAfterCommit();
        return true;
    }

    /** 删除校准通道配置。 */
    @Override
    @Transactional
    public boolean deleteChannelConfig(Long id) {
        // 删除前确认记录有效，使重复删除和无效主键返回明确错误。
        requireChannelConfig(id);
        if (channelMapper.delete(id) <= 0) {
            return false;
        }
        // 通道删除后同步删除一对一汇总记录及其一对多野值详情。
        recordMapper.deleteByChannelId(id);
        recordDetailMapper.deleteByChannelId(id);
        // 事务提交后刷新处理模块，后续数据不再应用已删除通道。
        runtimeSyncService.syncAfterCommit();
        return true;
    }

    /** 查询指定试验任务的校准记录列表。 */
    @Override
    public PageResult<CalibRecord> pageCalibRecords(
            String taskId,
            Integer pageNum,
            Integer pageSize) {
        requireTaskId(taskId);
        int safePageNum = pageNum == null ? 1 : Math.max(1, pageNum);
        int safePageSize = safePageSize(pageSize);

        // PageHelper 只会分页紧随其后的第一条 MyBatis 查询，二者之间不要插入其他查询。
        PageHelper.startPage(safePageNum, safePageSize);
        List<CalibRecord> records = recordMapper.findAllByTaskId(taskId);
        PageInfo<CalibRecord> pageInfo = new PageInfo<>(records);

        return new PageResult<CalibRecord>(
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                pageInfo.getTotal(),
                records);
    }

    /** 查询校准记录汇总，并分页查询该通道产生的野值详情。 */
    @Override
    public CalibRecordDetailVO getCalibRecordDetail(
            Long id,
            String taskId,
            Integer pageNum,
            Integer pageSize) {
        requireTaskId(taskId);
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("校准记录ID必须大于0");
        }
        CalibRecord record = recordMapper.findByIdAndTaskId(id, taskId);
        if (record == null) {
            throw new IllegalArgumentException("校准记录不存在");
        }

        int safePageNum = pageNum == null ? 1 : Math.max(1, pageNum);
        int safePageSize = safePageSize(pageSize);
        // PageHelper 必须紧邻详情查询，确保只分页野值详情。
        PageHelper.startPage(safePageNum, safePageSize);
        List<CalibRecordDetail> details =
                recordDetailMapper.findByTaskIdAndChannelId(
                        taskId, record.getChannelId());
        PageInfo<CalibRecordDetail> pageInfo = new PageInfo<>(details);

        CalibRecordDetailVO result = new CalibRecordDetailVO();
        result.setRecord(record);
        result.setDetails(new PageResult<CalibRecordDetail>(
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                pageInfo.getTotal(),
                details));
        return result;
    }

    /** 查询有效校准通道，不存在或已删除时抛出统一业务异常。 */
    private CalibChannelConfig requireChannelConfig(Long channelId) {
        if (channelId == null || channelId <= 0) {
            throw new IllegalArgumentException("校准通道ID必须大于0");
        }
        CalibChannelConfig config = channelMapper.findById(channelId);
        if (config == null) {
            throw new IllegalArgumentException("校准通道不存在");
        }
        return config;
    }

    /** 校验管理端查询必须携带有效试验任务ID。 */
    private void requireTaskId(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("试验任务编号不能为空");
        }
    }

    /** 限制分页大小，避免单次加载过多野值详情。 */
    private int safePageSize(Integer pageSize) {
        return pageSize == null ? 20 : Math.min(100, Math.max(1, pageSize));
    }

    /** 新增通道时补充数据库约定的默认状态。 */
    private void fillChannelDefaults(CalibChannelConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("校准通道配置不能为空");
        }
        if (config.getDynamicUpdate() == null) {
            config.setDynamicUpdate(0);
        }
        if (config.getAutoClean() == null) {
            config.setAutoClean(0);
        }
        if (config.getEnabled() == null) {
            config.setEnabled(1);
        }
        if (config.getChannelStatus() == null) {
            config.setChannelStatus(1);
        }
        if (config.getIsDeleted() == null) {
            config.setIsDeleted(0);
        }
    }

    /** 去除通道文本字段首尾空白，保证新增和修改保存结果一致。 */
    private void normalizeChannelText(CalibChannelConfig config) {
        if (config == null) {
            return;
        }
        if (config.getChannelName() != null) {
            config.setChannelName(config.getChannelName().trim());
        }
        if (config.getNormalRange() != null) {
            config.setNormalRange(config.getNormalRange().trim());
        }
        if (config.getCalibrationFormula() != null) {
            config.setCalibrationFormula(config.getCalibrationFormula().trim());
        }
    }

    /** 将本次修改的非空字段合并到现有配置，用于完整业务校验。 */
    private void mergeChannelConfig(
            CalibChannelConfig target,
            CalibChannelConfig source) {
        if (source.getTaskId() != null) {
            target.setTaskId(source.getTaskId());
        }
        if (source.getChannelName() != null) {
            target.setChannelName(source.getChannelName());
        }
        if (source.getCalibrationFormula() != null) {
            target.setCalibrationFormula(source.getCalibrationFormula());
        }
        if (source.getNormalRange() != null) {
            target.setNormalRange(source.getNormalRange());
        }
        if (source.getDetectMethod() != null) {
            target.setDetectMethod(source.getDetectMethod());
        }
        if (source.getSigmaValue() != null) {
            target.setSigmaValue(source.getSigmaValue());
        }
        if (source.getFluctuationRate() != null) {
            target.setFluctuationRate(source.getFluctuationRate());
        }
        if (source.getChauvenetCoef() != null) {
            target.setChauvenetCoef(source.getChauvenetCoef());
        }
        if (source.getMinSampleCount() != null) {
            target.setMinSampleCount(source.getMinSampleCount());
        }
        if (source.getIterateCount() != null) {
            target.setIterateCount(source.getIterateCount());
        }
        if (source.getSampleWindow() != null) {
            target.setSampleWindow(source.getSampleWindow());
        }
        if (source.getDynamicUpdate() != null) {
            target.setDynamicUpdate(source.getDynamicUpdate());
        }
        if (source.getAutoClean() != null) {
            target.setAutoClean(source.getAutoClean());
        }
        if (source.getEnabled() != null) {
            target.setEnabled(source.getEnabled());
        }
        if (source.getChannelStatus() != null) {
            target.setChannelStatus(source.getChannelStatus());
        }
    }

    /** 校验校准通道公共参数和所选检测方法的专用参数。 */
    private void validateChannelConfig(CalibChannelConfig config) {
        if (config.getTaskId() == null || config.getTaskId().trim().isEmpty()) {
            throw new IllegalArgumentException("试验任务编号不能为空");
        }
        if (config.getChannelName() == null || config.getChannelName().isEmpty()) {
            throw new IllegalArgumentException("通道名称不能为空");
        }
        if (config.getChannelName().length() > 100) {
            throw new IllegalArgumentException("通道名称不能超过100个字符");
        }
        if (config.getCalibrationFormula() == null || config.getCalibrationFormula().isEmpty()) {
            throw new IllegalArgumentException("校准公式不能为空");
        }
        if (config.getCalibrationFormula().length() > 100) {
            throw new IllegalArgumentException("校准公式不能超过100个字符");
        }
        if (EnumData.labelOf(BusinessEnums.DetectMethod.values(),
                config.getDetectMethod()) == null) {
            throw new IllegalArgumentException("野值检测方法只能为1、2或3");
        }

        // 正常范围仅阈值法必填，其他算法允许不配置；传入有效文本时统一校验。
        String normalRange = config.getNormalRange();
        boolean hasNormalRange = normalRange != null && !normalRange.isEmpty();
        if (BusinessEnums.DetectMethod.THRESHOLD.getValue().equals(config.getDetectMethod())
                && !hasNormalRange) {
            throw new IllegalArgumentException("阈值法正常范围不能为空");
        }
        if (hasNormalRange) {
            if (normalRange.length() > 50) {
                throw new IllegalArgumentException("正常范围不能超过50个字符");
            }
            validateNormalRange(normalRange);
        }

        // 按所选算法校验当前实际生效的参数。
        if (config.getDetectMethod() == 1) {
            validateWrightConfig(config);
        } else if (config.getDetectMethod() == 2) {
            validateThresholdConfig(config);
        } else {
            validateChauvenetConfig(config);
        }

        // 公共开关和运行状态统一使用已有枚举范围。
        validateBinaryValue(config.getDynamicUpdate(), "动态更新状态");
        validateBinaryValue(config.getAutoClean(), "自动清洗状态");
        validateBinaryValue(config.getEnabled(), "启用状态");
        if (EnumData.labelOf(BusinessEnums.ChannelStatus.values(),
                config.getChannelStatus()) == null) {
            throw new IllegalArgumentException("通道状态只能为0、1或2");
        }
    }

    /** 校验正常范围格式以及上下边界顺序。 */
    private void validateNormalRange(String normalRange) {
        Matcher matcher = NORMAL_RANGE.matcher(normalRange);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("正常范围格式不正确，例如：20~85℃");
        }
        BigDecimal lower = new BigDecimal(matcher.group(1));
        BigDecimal upper = new BigDecimal(matcher.group(2));
        if (lower.compareTo(upper) > 0) {
            throw new IllegalArgumentException("正常范围下限不能大于上限");
        }
    }

    /** 校验莱特准则参数。 */
    private void validateWrightConfig(CalibChannelConfig config) {
        if (config.getSigmaValue() == null
                || config.getSigmaValue() < 1
                || config.getSigmaValue() > 5) {
            throw new IllegalArgumentException("σ倍数必须在1至5之间");
        }
        if (config.getSampleWindow() == null || config.getSampleWindow() < 3) {
            throw new IllegalArgumentException("样本窗口不能小于3");
        }
    }

    /** 校验阈值法参数。 */
    private void validateThresholdConfig(CalibChannelConfig config) {
        if (config.getFluctuationRate() == null
                || !Double.isFinite(config.getFluctuationRate())
                || config.getFluctuationRate() <= 0
                || config.getFluctuationRate() > 100) {
            throw new IllegalArgumentException("阈值百分比必须大于0且不超过100");
        }
    }

    /** 校验肖维涅法参数。 */
    private void validateChauvenetConfig(CalibChannelConfig config) {
        if (config.getChauvenetCoef() == null
                || Double.compare(config.getChauvenetCoef(), 0.5D) != 0) {
            throw new IllegalArgumentException("肖维涅判别常数必须为0.5");
        }
        if (config.getMinSampleCount() == null
                || config.getMinSampleCount() < 3) {
            throw new IllegalArgumentException("最近样本数不能小于3");
        }
        if (config.getIterateCount() == null
                || config.getIterateCount() < 1
                || config.getIterateCount() > 3) {
            throw new IllegalArgumentException("迭代次数必须在1至3之间");
        }
    }

    /** 校验是否类字段只能取0或1。 */
    private void validateBinaryValue(Integer value, String fieldName) {
        if (value == null || (value != 0 && value != 1)) {
            throw new IllegalArgumentException(fieldName + "只能为0或1");
        }
    }

    /** 校验同一试验任务内的校准公式唯一。 */
    private void validateUniqueCalibrationFormula(CalibChannelConfig config) {
        int count = channelMapper.countByTaskIdAndCalibrationFormula(
                config.getTaskId(), config.getCalibrationFormula(), config.getId());
        if (count > 0) {
            throw new IllegalArgumentException("当前任务已存在相同的校准公式");
        }
    }

    /** 根据新建通道生成初始校准记录。 */
    private void createInitialCalibRecord(CalibChannelConfig config) {
        CalibRecord record = new CalibRecord();
        record.setTaskId(config.getTaskId());
        record.setChannelId(config.getId());
        record.setChannelName(config.getChannelName());
        record.setOutlierCount(0L);
        record.setIsDeleted(0);
        if (recordMapper.insert(record) <= 0) {
            throw new IllegalArgumentException("初始校准记录创建失败");
        }
    }

}
