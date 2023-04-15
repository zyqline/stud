package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedisUtil redisUtil;
    @Override
    public Result queryById(Long id) {
        Shop shop = redisUtil.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        if (shop == null){

            return Result.fail("店铺查询失败！");
        }

        return Result.ok(shop);
    }

    public Shop queryWithLogicalExpire(Long id){

        String shopjson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);

        if(StrUtil.isNotBlank(shopjson)){

            Shop shop1 = JSONUtil.toBean(shopjson, Shop.class);

            return shop1;

        }

        if(shopjson != null){

            return null;

        }

        String lock_key=LOCK_SHOP_KEY+ id;

        Shop shop = null;
        try {
            boolean lock = tryLock(lock_key);

            if (!lock){

                Thread.sleep(50);

                return queryWithPassThrough(id);
            }


            shop = getById(id);

            if (shop == null){

                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);

                return null;

            }

            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {

            unLock(lock_key);
        }

        return shop;
    }

    public Shop queryWithPassThrough(Long id){

        String shopjson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);

        if(StrUtil.isNotBlank(shopjson)){

            Shop shop1 = JSONUtil.toBean(shopjson, Shop.class);

            return shop1;

        }

        if(shopjson != null){

            return null;

        }

        String lock_key=LOCK_SHOP_KEY+ id;

        Shop shop = null;
        try {
            boolean lock = tryLock(lock_key);

            if (!lock){

                Thread.sleep(50);

                return queryWithPassThrough(id);
            }


            shop = getById(id);

            if (shop == null){

                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);

                return null;

            }

            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {

           unLock(lock_key);
        }

        return shop;
    }

    private boolean tryLock(String key){
        Boolean aBoolean = stringRedisTemplate.opsForValue().
                setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(aBoolean);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


    @Override
    @Transactional
    public Result updateShop(Shop shop) {

        Long id = shop.getId();

        if(id == null){

            return  Result.fail("店铺不存在！");

        }

        updateById(shop);

        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);

        return Result.ok();
    }
}
