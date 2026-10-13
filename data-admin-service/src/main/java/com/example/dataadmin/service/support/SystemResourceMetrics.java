package com.example.dataadmin.service.support;

import java.lang.management.ManagementFactory;
import org.springframework.stereotype.Component;

/** 系统告警与处理页面共用的管理服务所在机器资源采集组件。 */
@Component
public class SystemResourceMetrics {

    /** 读取机器整体CPU使用百分比，无法采样时沿用系统告警的零值口径。 */
    public double readCpuUsage() {
        // 第一步：检查当前运行环境是否支持系统资源采集。
        java.lang.management.OperatingSystemMXBean bean =
                ManagementFactory.getOperatingSystemMXBean();
        if (!(bean instanceof com.sun.management.OperatingSystemMXBean)) {
            return 0D;
        }
        // 第二步：将系统CPU负载换算为百分比，保留两位小数。
        double value = ((com.sun.management.OperatingSystemMXBean) bean)
                .getSystemCpuLoad();
        return Math.round(Math.max(0D, value * 100D) * 100D) / 100D;
    }

    /** 读取机器已使用物理内存占总物理内存的百分比。 */
    public double readMemoryUsage() {
        // 第一步：检查当前运行环境是否支持物理内存采集。
        java.lang.management.OperatingSystemMXBean operatingSystem =
                ManagementFactory.getOperatingSystemMXBean();
        if (!(operatingSystem instanceof com.sun.management.OperatingSystemMXBean)) {
            return 0D;
        }
        // 第二步：按总物理内存和空闲物理内存计算使用百分比。
        com.sun.management.OperatingSystemMXBean bean =
                (com.sun.management.OperatingSystemMXBean) operatingSystem;
        long total = bean.getTotalPhysicalMemorySize();
        return total <= 0L ? 0D : Math.round(
                (total - bean.getFreePhysicalMemorySize()) * 100D / total * 100D) / 100D;
    }
}
