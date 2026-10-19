package com.example.dataadmin.vo.processing;

/** 数据处理页面顶部统计卡片。 */
public class DataProcessingOverviewVO {

    /** 今日成功完成处理的数据数量。 */
    private Long todayProcessedCount = 0L;

    /** 当前试验任务仍保留的统计记录累计去重数量。 */
    private Long deduplicatedCount = 0L;

    public Long getTodayProcessedCount() {
        return todayProcessedCount;
    }

    public void setTodayProcessedCount(Long todayProcessedCount) {
        this.todayProcessedCount = todayProcessedCount;
    }

    public Long getDeduplicatedCount() {
        return deduplicatedCount;
    }

    public void setDeduplicatedCount(Long deduplicatedCount) {
        this.deduplicatedCount = deduplicatedCount;
    }
}
