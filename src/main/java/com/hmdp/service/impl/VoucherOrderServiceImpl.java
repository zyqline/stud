package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    ISeckillVoucherService iseckillvoucherservice;
    @Resource
    RedisIdWorker redisidworker;
    @Resource
    StringRedisTemplate stringredistemplate;
    @Resource
    RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private  BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
    private static final  ExecutorService SECKILL_ORDER_EXECUTOR=Executors.newSingleThreadExecutor();

    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }



    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            try {
                VoucherOrder voucherOrder = orderTasks.take();

                handleVoucherOrder(voucherOrder);

            } catch (InterruptedException e) {
                log.error("订单异常："+e);
            }

        }


    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {

        Long userId = voucherOrder.getUserId();

        RLock lock = redissonClient.getLock("lock_order_" + userId);

        boolean success = lock.tryLock();

        if(!success){
            log.error("handleVoucherOrder:分布式锁获取失败");
        }

        try {

            proxy.createVoucherOrder(voucherOrder);

        } finally {

            lock.unlock();
        }


    }

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {

        Long userId = UserHolder.getUser().getId();

        Long result = stringredistemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());

        if(result.intValue()!=0){

            return Result.fail(result.intValue()==1?"库存不足！":"不可以重复购买！");

        }


        VoucherOrder voucherOrder = new VoucherOrder();
        long voucherOrderId = redisidworker.nextId("order");

        voucherOrder.setId(voucherOrderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);


        orderTasks.add(voucherOrder);

        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(voucherOrderId);
    }


/*  @Override
    public Result seckillVoucher(Long id) {
        SeckillVoucher voucher = iseckillvoucherservice.getById(id);

        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){

            return Result.fail("秒杀活动还没有开始！");

        }

        if(voucher.getEndTime().isBefore(LocalDateTime.now())){

            return Result.fail("秒杀活动已经结束！");
        }

        if(voucher.getStock()<1){
            return Result.fail("库存不足！");
        }

        long id1 =UserHolder.getUser().getId();


        RedisLock redislock = new RedisLock("order_" + id1, stringredistemplate);
        RLock lock = redissonclient.getLock("order_" + id1);
        boolean success = lock.tryLock();

//        获取redis的分布式锁，判断获取成功
//        Boolean success = redislock.tryLock(10L);

        if(!success){

            return Result.fail("不允许重复下单！");
        }


        try {
            IVoucherOrderService currentProxy = (IVoucherOrderService) AopContext.currentProxy();

            Result result = currentProxy.createOrder(voucher);

            return result;
        }  finally {

            lock.unlock();
        }
    } */

    //悲观锁:synchronized 和@transactional的问题  synchronized如果在@transactional前释放，
    // 就可能导致数据还没有同步到数据库，锁就结束，其他线程就进入到查询业务中，从而发生线程并发问题
    //方法就是将synchronized加在@transactional外，
    // 这样就又导致另外一个问题的出现，@transactional是由aop产生的代理对象去执行的
    //如果直接在方法中调用注解了@transactional的方法，执行时就会默认使用this对象去引用
    //从而失去了@transactional的功能
    //解决方法,去获取aop中的代理对象：aopcontext.currentproxy();


    @Transactional
    @Override
    public Result createOrder(SeckillVoucher voucher) {

        UserDTO user =UserHolder.getUser();


        int cout = query().eq("user_id", user.getId()).eq("voucher_id",voucher.getVoucherId()).count();

        if(cout>0){

            return Result.fail("一个用户只允许买一次！");

        }


        Boolean success = iseckillvoucherservice.update().setSql("stock=stock-1").eq("voucher_id", voucher.getVoucherId())
                //乐观锁：认为只要没有发生修改就认为没有问题 缺点：成功率低
                // .eq("stock",voucher.getstock())
                // 改进乐观锁：允许有一定的误差范围，但是不影响业务,例如stock大于0
                .gt("stock",0)
                .update();

        if(!success){

            return Result.fail("库存不足！");

        }

        VoucherOrder order = new VoucherOrder();

        Long orderid = redisidworker.nextId("order");

        order.setId(orderid);

        order.setUserId(user.getId());

        order.setVoucherId(voucher.getVoucherId());

        save(order);

        return Result.ok(orderid);
    }

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        Long userId = voucherOrder.getUserId();


        int cout = query().eq("user_id", userId).eq("voucher_id",voucherOrder.getVoucherId()).count();

        if(cout>0){

            log.error("一个用户只允许买一次！");

        }


        Boolean success = iseckillvoucherservice.update().setSql("stock=stock-1").eq("voucher_id", voucherOrder.getVoucherId())
                //乐观锁：认为只要没有发生修改就认为没有问题 缺点：成功率低
                // .eq("stock",voucher.getstock())
                // 改进乐观锁：允许有一定的误差范围，但是不影响业务,例如stock大于0
                .gt("stock",0)
                .update();

        if(!success){

             log.error("库存不足！");
        }

        save(voucherOrder);
    }




}
