package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class RedisLock implements ILock{


    StringRedisTemplate stringRedisTemplate;
    String name;
    static  final  String KEY_LOCK="lock_";
    static  final  String UUID_LOCK= UUID.randomUUID().toString(true)+"-";

    private static  final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<Long>();

        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unLock.lua"));

        UNLOCK_SCRIPT.setResultType(Long.class);


    }
    public RedisLock(String name, StringRedisTemplate stringRedisTemplate) {

        this.stringRedisTemplate=stringRedisTemplate;

        this.name=name;
    }

    @Override
    public Boolean tryLock(Long outTime) {
        long id = Thread.currentThread().getId();

        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_LOCK + name, UUID_LOCK+id, outTime, TimeUnit.SECONDS);

        return success;

    }

    @Override
    public void delLock() {

        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_LOCK+name),
                UUID_LOCK+Thread.currentThread().getId());
    }

//    @Override
//    public void delLock() {
//
//        String value_lock=UUID_LOCK+Thread.currentThread().getId();
//        String value = stringRedisTemplate.opsForValue().get(KEY_LOCK + name);
//       //原子性问题：如果一个线程在获取锁进行业务操作完成后，
//        // 进入finally进行锁释放，在判断锁标识返回true
//        // 后突然发生了业务堵塞，这个时候另外一个线程通过另外一个jvm进入了
//        if(value.equals(value_lock)){
//
//            stringRedisTemplate.delete(KEY_LOCK+name);
//        }
//
//    }
}
