package com.amusement.park.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 全系统统一时钟：业务一律按园区时区（Asia/Shanghai）判断“今天”，
 * 年卡有效期的边界判断与乘坐核验共用同一时钟，避免容器 UTC 造成跨天误差。
 *
 * <p>测试中可注入固定 Clock 验证日期首尾边界。</p>
 */
@Configuration
public class ClockConfig {

    public static final ZoneId PARK_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    @Primary
    public Clock parkClock() {
        return Clock.system(PARK_ZONE);
    }
}
