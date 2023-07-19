package com.lacus.core.config.redis;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import redis.embedded.RedisServer;

@Configuration
@ConditionalOnExpression("'${lacus.embedded.redis}' == 'true'")
public class EmbeddedRedisConfig {

    @Value("${spring.redis.port}")
    private Integer port;

    private RedisServer redisServer;


    @PostConstruct
    public void postConstruct() {
        RedisServer redisServer = RedisServer.builder().port(port)
//          window系统如果报错需要加上 maxheap 参数
//          .setting("maxheap 32M")
            .setting("daemonize no")
            .setting("appendonly no").build();
        this.redisServer = redisServer;
        redisServer.start();
    }

    @PreDestroy
    public void preDestroy() {
        redisServer.stop();
    }

}
