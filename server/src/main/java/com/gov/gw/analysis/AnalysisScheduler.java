package com.gov.gw.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 效能数据定时抽取：默认每 10 分钟刷新分析表并生成督办单（仅读业务库与 ACT_HI_* 历史表） */
@Component
public class AnalysisScheduler {
    private static final Logger log = LoggerFactory.getLogger(AnalysisScheduler.class);

    private final AnalysisService analysisService;

    public AnalysisScheduler(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Scheduled(fixedDelayString = "${app.analysis.extract-interval-ms:600000}", initialDelay = 60_000)
    public void run() {
        try {
            int docs = analysisService.extract();
            int reminders = analysisService.generateReminders();
            if (reminders > 0) {
                log.info("效能数据抽取完成：{} 篇公文，新增督办单 {} 张", docs, reminders);
            } else {
                log.debug("效能数据抽取完成：{} 篇公文", docs);
            }
        } catch (Exception e) {
            log.warn("效能数据抽取失败：{}", e.getMessage());
        }
    }
}
