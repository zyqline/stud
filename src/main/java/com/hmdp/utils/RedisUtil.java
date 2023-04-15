package com.hmdp.utils;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

@Slf4j
@Component
public class RedisUtil {


    private final StringRedisTemplate stringRedisTemplate;

    private  static  final ExecutorService EXECUTOR_SERVICE= Executors.newFixedThreadPool(10);


    public RedisUtil(StringRedisTemplate stringRedisTemplate) {

        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);

    }

    public void set(String key,Object value){

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value));

    }

    public void del(String key){

        stringRedisTemplate.delete(key);
    }

    public void  setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit){

        RedisData redisData=new RedisData();

        redisData.setData(value);

        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

       this.set(key,redisData);
    }

    //解决缓存击穿方法
   public <R,ID> R queryWithPassThrough(String key_te, ID id, Class<R> type, Function<ID,R> dbFallBack,Long time,TimeUnit unit){

        String key=key_te+id;

       String json = stringRedisTemplate.opsForValue().get(key);

       if(StrUtil.isNotBlank(json)){

           return JSONUtil.toBean(json,type);
       }

       if(json != null){

           return null;
       }

       R r = dbFallBack.apply(id);

       if(r == null){

           stringRedisTemplate.opsForValue().set(key,"",time,unit);

           return  null;
       }

     stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),time,unit);

       return r;
   }

   //解决缓存击穿方法(逻辑过期)
   public <R,ID>  R queryWithLogicalExpire(String key_it,ID id,Class<R> type,Function<ID,R> dbFallBack,Long time,TimeUnit unit){

        String key=key_it+id;

       String json = stringRedisTemplate.opsForValue().get(key);

       if (StrUtil.isBlank(json)){

           return null;

       }

       RedisData redisData = JSONUtil.toBean(json, RedisData.class);

       R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

       if (redisData.getExpireTime().isAfter(LocalDateTime.now())){

           return r;
       }

       String lock_key=LOCK_SHOP_KEY+id;

       Boolean aBoolean = tryLock(lock_key);

       if(aBoolean){

           EXECUTOR_SERVICE.submit(()-> {

               try {
                   R r1 = dbFallBack.apply(id);

                   this.setWithLogicalExpire(key,r1,time,unit);

               } catch (Exception e) {
                   throw new RuntimeException(e);
               } finally {

                   unLock(key);
               }
           });
       }


       return r;
   }


   //缓存击穿加互斥锁
   public <R,ID> R queryWithMutex(String key_it,ID id,Class<R> type,Function<ID,R> dbFallBack,Long time,TimeUnit unit){

        String key=key_it+id;

       String json = stringRedisTemplate.opsForValue().get(key);

       if (StrUtil.isNotBlank(json)){

           return JSONUtil.toBean(json,type);

       }

       if (json != null){

           return null;

       }

       String key_lock=LOCK_SHOP_KEY+id;

       Boolean aBoolean = tryLock(key_lock);

       R r = null;
       try {
           if(!aBoolean){

               Thread.sleep(50);

              return queryWithMutex(key_it,id,type,dbFallBack,time,unit);

           }

           r = dbFallBack.apply(id);

           if(r == null){

               return null;
           }

           this.set(key,JSONUtil.toJsonStr(r),time,unit);
       } catch (InterruptedException e) {
           throw new RuntimeException(e);
       } finally {

           unLock(key_lock);
       }

       return r;

   }

   private Boolean tryLock(String key){

       Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);

       return aBoolean;

   }

   private void  unLock(String key){

        stringRedisTemplate.delete(key);

   }








}
