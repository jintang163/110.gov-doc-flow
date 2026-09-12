package com.gov.gw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 政务协同办公系统 - 公文流转引擎。
 * 主链路：模板套红 -> 流程配置 -> 拟稿 -> 审核/会签 -> 签发(电子签章) -> 归档查询。
 */
@EnableScheduling
@SpringBootApplication
public class GongwenApplication {

    public static void main(String[] args) {
        SpringApplication.run(GongwenApplication.class, args);
    }
}
