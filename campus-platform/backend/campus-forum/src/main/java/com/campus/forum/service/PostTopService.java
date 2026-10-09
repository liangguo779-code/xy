package com.campus.forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.forum.entity.Post;
import com.campus.forum.mapper.PostMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 置顶帖子缓存（cache-aside）：置顶帖读多写少——默认列表每页都以置顶帖打头，
 * 而置顶/取消是管理员低频操作，适合缓存整份置顶列表。
 *
 * <p>缓存（{@code forum:top:posts:list}）：JSON 序列化的置顶帖子列表
 * （status=1 且 isTop=1，按 createTime DESC，与列表排序口径一致），短 TTL（120 秒）兜底。
 *
 * <p>主动失效时机：置顶/取消置顶、隐藏/恢复、删除（管理端与用户端）、编辑——
 * 这些操作都会改变置顶集合或置顶帖内容。失效失败最多让列表滞后一个 TTL，
 * 且读取侧过滤 status/isTop，不会把已隐藏/已取消置顶的帖错误展示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostTopService {

    /** 置顶列表缓存：JSON 数组字符串 */
    private static final String CACHE_KEY = "forum:top:posts:list";
    /** 缓存过期时间（秒）：兜底失效，主动失效为主 */
    private static final long CACHE_TTL_SECONDS = 120;

    /** 注册 JSR310 等可发现模块，保证 Post 中 LocalDateTime 的序列化可用 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final StringRedisTemplate redis;
    private final PostMapper postMapper;

    /**
     * 获取置顶帖子列表：优先命中缓存，未命中回源数据库并回填。
     * 读取侧过滤 status=1 且 isTop=1，缓存滞后时不会展示已隐藏/已取消置顶的帖子。
     */
    public List<Post> getTopPosts() {
        try {
            String cached = redis.opsForValue().get(CACHE_KEY);
            if (cached != null) {
                List<Post> list = OBJECT_MAPPER.readValue(cached, new TypeReference<List<Post>>() {});
                return list.stream()
                        .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                        .filter(p -> p.getIsTop() != null && p.getIsTop() == 1)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("读取置顶帖子缓存失败，回源数据库: {}", e.getMessage());
        }

        List<Post> top = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 1)
                .eq(Post::getIsTop, 1)
                .orderByDesc(Post::getCreateTime));

        try {
            redis.opsForValue().set(CACHE_KEY,
                    OBJECT_MAPPER.writeValueAsString(top),
                    Duration.ofSeconds(CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("回填置顶帖子缓存失败: {}", e.getMessage());
        }
        return top;
    }

    /** 主动失效：置顶集合或置顶帖内容变化时调用（置顶/取消/隐藏/恢复/删除/编辑） */
    public void invalidate() {
        try {
            redis.delete(CACHE_KEY);
        } catch (Exception e) {
            log.warn("失效置顶帖子缓存失败: {}", e.getMessage());
        }
    }
}
