package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hmdp.service.IVoucherOrderService;
import javax.annotation.Resource;


@RestController
@RequestMapping("/voucher-order")



public class VoucherOrderController {

    @Autowired
    IVoucherOrderService iVoucherOrderService;
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {

        return iVoucherOrderService.seckillVoucher(voucherId);
    }
}
