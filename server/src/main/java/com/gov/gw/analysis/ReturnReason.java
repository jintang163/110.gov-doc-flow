package com.gov.gw.analysis;

import java.time.LocalDateTime;

/** 退回意见（doc_analysis.returnReasonsJson 元素） */
public record ReturnReason(String nodeName, String actorName, String comment, LocalDateTime at) {}
