package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Autowired
    UserServiceImpl userService;
    @Autowired
    IFollowService followService;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryHotBlog(Integer current) {

        Page<Blog> pageBlogs=query().orderByDesc("liked").page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = pageBlogs.getRecords();
        records.forEach(blog -> {
           if(UserHolder.getUser()!=null)
               isBlogIslike(blog);
            selectBlogUserById(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        selectBlogUserById(blog);
        isBlogIslike(blog);
        return Result.ok(blog);
    }

    private void isBlogIslike(Blog blog) {
        Double score = stringRedisTemplate.opsForZSet()
                .score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), UserHolder.getUser().getId().toString());
        blog.setIsLike(score!=null);
    }

    @Override
    public Result isLike(Long id) {
        UserDTO user = UserHolder.getUser();
        if(user==null)
            return Result.ok("请先登录");

        Double score = stringRedisTemplate.opsForZSet()
                .score(RedisConstants.BLOG_LIKED_KEY + id, user.getId().toString());

        if(score==null){
            boolean updateSuccess = update().setSql("liked=liked+1").eq("id", id).update();

            if(updateSuccess){
                stringRedisTemplate.opsForZSet().
                       add(RedisConstants.BLOG_LIKED_KEY+id,user.getId().toString(),System.currentTimeMillis());
            }

        }else {

            boolean updateSuccess = update().setSql("liked=liked-1").eq("id", id).update();

            if(updateSuccess){
                stringRedisTemplate.opsForZSet().
                        remove(RedisConstants.BLOG_LIKED_KEY+id,user.getId().toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result likesBlogById(Long id) {
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        if(top5==null||top5.isEmpty()){
            return Result.ok();
        }
        List<Long> collect = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String ids = StrUtil.join(",", collect);
        List<UserDTO> userCollectors = userService.query().in("id", collect).last("ORDER BY FIELD(id," + ids + ")").list().stream().
                map(user -> BeanUtil.copyProperties(user, UserDTO.class)).
                collect(Collectors.toList());
        return Result.ok(userCollectors);
    }

    @Override
    public Result getFollowBlogs(long max, Integer offset) {
        Long id = UserHolder.getUser().getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + id,0,max,offset,2);
        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        ArrayList<Long> longs = new ArrayList<>(typedTuples.size());

        int newOffset=1;
        long minTime=0;
        ArrayList<Blog> blogs = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> type:typedTuples) {
            longs.add(Long.valueOf(type.getValue()));
            Blog blog = query().eq("id", type.getValue()).one();
            isBlogIslike(blog);
            selectBlogUserById(blog);
            blogs.add(blog);
            if(type.getScore().longValue()==minTime){
                newOffset++;
            }else {
                newOffset=1;
                minTime=type.getScore().longValue();
            }
        }

        ScrollResult scrollResult=new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(newOffset);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    @Override
    public Result saveBlog(Blog blog) {
        Long id = UserHolder.getUser().getId();
        blog.setUserId(id);
        save(blog);
        List<Blog> list = query().eq("user_id", blog.getUserId()).orderByDesc("id").list();
        System.out.println(list.get(0));
        List<Follow> followUser = followService.query().eq("follow_user_id", id).list();
        for (Follow follow:followUser) {
            stringRedisTemplate.opsForZSet()
                    .add(RedisConstants.FEED_KEY+follow.getUserId(),list.get(0).getId().toString(),System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }


    private void selectBlogUserById(Blog blog) {
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
