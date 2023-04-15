package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {


    @Bean
    public RedissonClient redissonClient(){

        Config config = new Config();

        config.useSingleServer()
                .setAddress("redis://43.139.100.225:6379")
                .setPassword("zyq123456");

        return Redisson.create(config);
    }
}
