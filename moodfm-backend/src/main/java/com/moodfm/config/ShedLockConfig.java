package com.moodfm.config;

import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 分布式定时任务锁。多实例部署时（同一份 Redis + N 个 backend），
 * 同一 cron 在 lock 窗口内只会被其中一个实例执行，避免重复发周报 / 重复扣额度。
 *
 * defaultLockAtMostFor=PT10M：任何拿到锁的实例若崩了，10 分钟后锁自动释放，
 * 下个 cron 触发时其它实例能接手；单方法可用 @SchedulerLock 覆盖此值。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public RedisLockProvider redisLockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "moodfm");
    }
}
