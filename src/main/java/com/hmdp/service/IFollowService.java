package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IFollowService extends IService<Follow> {

    Result updateFollow(Long id, boolean isFollow);

    Result getFollow(Long id);

    Result getcommon(Long id);
}
