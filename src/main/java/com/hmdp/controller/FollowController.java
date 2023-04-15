package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import com.hmdp.service.impl.FollowServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    IFollowService followService;
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id,@PathVariable("isFollow") boolean isFollow){

        return followService.updateFollow(id,isFollow);
    }


    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable("id") Long id){
        return followService.getFollow(id);
    }

    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long id){
        return followService.getcommon(id);
    }
}
