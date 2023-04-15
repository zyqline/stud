package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    IUserService userService;
    @Override
    public Result updateFollow(Long id, boolean isFollow) {
        UserDTO user = UserHolder.getUser();

        if(isFollow){
            Follow follow = new Follow();
            follow.setUserId(user.getId());
            follow.setFollowUserId(id);
            boolean save = save(follow);
            Long add = stringRedisTemplate.opsForSet()
                    .add(RedisConstants.USER_FOLLOW + user.getId(),String.valueOf(id) );
            return Result.ok(save);
        }

        boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", user.getId())
                .eq("follow_user_id", id));
        stringRedisTemplate.opsForSet().remove(RedisConstants.USER_FOLLOW+id,id.toString());
        return Result.ok(remove);
    }

    @Override
    public Result getFollow(Long id) {
        Long id1 = UserHolder.getUser().getId();
        Integer count = query().eq("follow_user_id", id).eq("user_id", id1).count();
        return Result.ok(count>0);
    }

    @Override
    public Result getcommon(Long id) {
        Long id1 = UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate
                .opsForSet().intersect(RedisConstants.USER_FOLLOW + id, RedisConstants.USER_FOLLOW + id1);
        if (intersect==null||intersect.isEmpty()){
            return Result.ok();
        }
        List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> collect1 = userService.listByIds(collect).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(collect1);
    }
}
