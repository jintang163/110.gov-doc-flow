package com.gov.gw.analysis;

import java.time.LocalDateTime;

/** 节点任务明细（doc_analysis.nodeStatsJson 元素）。hours 为 null 表示尚未办结 */
public record NodeStat(String key, String name, String type,
                       Long assigneeId, String assigneeName, String orgName, String post,
                       LocalDateTime start, LocalDateTime end, Double hours,
                       String action) {}
