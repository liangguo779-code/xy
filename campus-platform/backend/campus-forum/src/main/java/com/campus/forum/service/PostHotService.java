package com.campus.forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;
import com.campus.forum.mapper.CommentMapper;
import com.campus.forum.mapper.PostMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 热门帖子缓存：zset 维护帖子热度 + 热门列表缓存（cache-aside）。
 *
 * <p>热度 zset（{@code forum:hot:posts}）：member = postId，score = 热度值。
 * 浏览 +1、点赞 ±5、评论 ±3、发帖 +1，实时 ZINCRBY 更新。
 *
 * <p>热门列表缓存（{@code forum:hot:posts:list}）：JSON 序列化的 top50 帖子，
 * 短 TTL（60 秒）自动过期，删帖时主动失效。缓存整份 top50（与入参 size 无关），
 * 命中后按 size 截断，避免"先请求 size=5 再请求 size=20 只拿到 5 条"的截断 bug（P0-7）。
 *
 * <p>容量治理（P2-5）：热度分每日按系数衰减（旧互动权重随时间下降）并移除低分成员，
 * zset 写入时刷新 30 天 TTL；点赞状态 key（post_like: 与 comment_like: 前缀）不加 TTL
 * （过期会导致用户重复点赞使计数虚高），改为每日清理指向已删帖/已删评论的无主 key。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostHotService {

    /** 帖子热度 zset：member = postId，score = 热度值 */
    private static final String HOT_KEY = "forum:hot:posts";
    /** 热门列表缓存：JSON 数组字符串，固定存 top50（不带 size 维度） */
    private static final String CACHE_KEY = "forum:hot:posts:list";
    /** 缓存过期时间（秒） */
    private static final long CACHE_TTL_SECONDS = 60;
    /** 缓存回源固定填充条数：整份缓存按 top50 写入，命中后按入参 size 截断 */
    private static final int CACHE_FILL_SIZE = 50;
    /** 热度权重 */
    private static final double VIEW_WEIGHT = 1d;
    private static final double LIKE_WEIGHT = 5d;
    private static final double COMMENT_WEIGHT = 3d;
    private static final double CREATE_WEIGHT = 1d;

    /** 热度每日衰减系数：旧互动权重随时间下降，新帖才有机会进榜（时间衰减，P2-5） */
    private static final double DAILY_DECAY = 0.8d;

    /** 衰减后低于该分数的成员直接移除，兼做 zset 容量治理 */
    private static final double MIN_SCORE = 0.5d;

    /** 热度 zset 的 TTL（天）：每次写入刷新，长期无互动自动过期后走回源种子重建 */
    private static final long HOT_TTL_DAYS = 30;

    /** 注册 JSR310 等可发现模块，保证 Post 中 LocalDateTime 的序列化可用 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final StringRedisTemplate redis;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;

    // ── 热度更新 ───────────────────────────────────────────────────────────────

    public void onCreate(Long postId) {
        incr(postId, CREATE_WEIGHT);
    }

    public void onView(Long postId) {
        incr(postId, VIEW_WEIGHT);
    }

    /** 点赞/取消点赞：liked=true 加权，false 减权 */
    public void onLike(Long postId, boolean liked) {
        incr(postId, liked ? LIKE_WEIGHT : -LIKE_WEIGHT);
    }

    /** 新增评论：评论数 +3 热度 */
    public void onComment(Long postId) {
        incr(postId, COMMENT_WEIGHT);
    }

    /** 删除评论（含楼中楼）时回滚热度分，count 为本次删除的评论条数 */
    public void onCommentsRemoved(Long postId, int count) {
        if (count > 0) {
            incr(postId, -COMMENT_WEIGHT * count);
        }
    }

    /** 删帖时移除热度并失效列表缓存 */
    public void onDelete(Long postId) {
        redis.opsForZSet().remove(HOT_KEY, String.valueOf(postId));
        redis.delete(CACHE_KEY);
    }

    private void incr(Long postId, double delta) {
        try {
            redis.opsForZSet().incrementScore(HOT_KEY, String.valueOf(postId), delta);
            // 顺带刷新 zset 整体 TTL：长期无互动的热榜自动过期，防止无界增长（P2-5）
            redis.expire(HOT_KEY, Duration.ofDays(HOT_TTL_DAYS));
        } catch (Exception e) {
            log.warn("更新帖子热度失败: postId={}", postId, e);
        }
    }

    // ── 热门列表（cache-aside） ────────────────────────────────────────────────

    /**
     * 获取热门帖子 top-N：优先命中缓存，未命中回源数据库并回填缓存。
     * size 归一到 [1, 50]：缓存固定存 top50，请求更大 size 也没有更多数据（P0-7）。
     */
    public List<Post> getHotPosts(int size) {
        // 入参归一：下限 1，上限固定缓存条数
        int n = Math.max(1, Math.min(size, CACHE_FILL_SIZE));
        // 1. 读缓存
        try {
            String cached = redis.opsForValue().get(CACHE_KEY);
            if (cached != null) {
                List<Post> list = OBJECT_MAPPER.readValue(cached, new TypeReference<List<Post>>() {});
                return list.size() > n ? list.subList(0, n) : list;
            }
        } catch (Exception e) {
            log.warn("读取热门帖子缓存失败，回源数据库: {}", e.getMessage());
        }

        // 2. 回源：固定取 top50 id（而非首个请求的 size），避免先 10 后 20 时后者只能拿到 10 条
        List<Post> hot = loadFromZset(CACHE_FILL_SIZE);
        if (hot.isEmpty()) {
            // 热度 zset 为空（如刚上线）：退化为按浏览量排序，同时把种子数据写入 zset
            hot = postMapper.selectList(new LambdaQueryWrapper<Post>()
                    .eq(Post::getStatus, 1)
                    .orderByDesc(Post::getViewCount)
                    .last("LIMIT " + CACHE_FILL_SIZE));
            for (Post p : hot) {
                incr(p.getId(), CREATE_WEIGHT);
            }
        }

        // 3. 回填缓存（整份 top50 列表，TTL 60 秒）
        try {
            redis.opsForValue().set(CACHE_KEY,
                    OBJECT_MAPPER.writeValueAsString(hot),
                    Duration.ofSeconds(CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("回填热门帖子缓存失败: {}", e.getMessage());
        }
        // 与命中缓存一致：按归一后的 size 截断返回
        return hot.size() > n ? hot.subList(0, n) : hot;
    }

    private List<Post> loadFromZset(int size) {
        Set<String> ids = redis.opsForZSet().reverseRange(HOT_KEY, 0, size * 2L - 1);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> idList = new ArrayList<>();
        for (String id : ids) {
            try {
                idList.add(Long.parseLong(id));
            } catch (NumberFormatException ignored) {
            }
        }
        if (idList.isEmpty()) {
            return List.of();
        }
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .in(Post::getId, idList)
                .eq(Post::getStatus, 1));
        // 按 zset 热度顺序排列（in 查询不保序）
        Map<Long, Post> byId = new HashMap<>();
        for (Post p : posts) {
            byId.put(p.getId(), p);
        }
        List<Post> ordered = new ArrayList<>();
        for (Long id : idList) {
            Post p = byId.get(id);
            if (p != null) {
                ordered.add(p);
            }
            if (ordered.size() >= size) {
                break;
            }
        }
        return ordered;
    }

    // ── 容量治理（P2-5） ──────────────────────────────────────────────────────

    /**
     * 热度分每日衰减（score 衰减系数方案）：
     * 全量分数乘以 {@link #DAILY_DECAY}，低于 {@link #MIN_SCORE} 的成员直接移除。
     * 每天凌晨 3 点执行，衰减后榜单顺序变化，旧的热门列表缓存一并失效。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void decayHotScores() {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().rangeWithScores(HOT_KEY, 0, -1);
            if (tuples == null || tuples.isEmpty()) {
                return;
            }
            int removed = 0;
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                if (t.getScore() == null || t.getValue() == null) {
                    continue;
                }
                double next = t.getScore() * DAILY_DECAY;
                if (next < MIN_SCORE) {
                    redis.opsForZSet().remove(HOT_KEY, t.getValue());
                    removed++;
                } else {
                    redis.opsForZSet().add(HOT_KEY, t.getValue(), next);
                }
            }
            // 衰减后排序已变化，旧热门列表缓存必须失效
            redis.delete(CACHE_KEY);
            redis.expire(HOT_KEY, Duration.ofDays(HOT_TTL_DAYS));
            log.info("热度分每日衰减完成：系数 {}，移除低分成员 {} 个", DAILY_DECAY, removed);
        } catch (Exception e) {
            log.warn("热度分衰减失败: {}", e.getMessage());
        }
    }

    /**
     * 每日清理无主点赞状态 key（post_like: 与 comment_like: 前缀）：
     * 这类 key 存的是"用户是否点过赞"，加 TTL 会在过期后允许重复点赞使计数虚高，
     * 因此改为定期清理指向已删帖/已删评论的 key（P2-5）。
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanupOrphanLikeKeys() {
        cleanupPostLikeKeys();
        cleanupCommentLikeKeys();
    }

    /** 清理帖子已删除/隐藏后遗留的 post_like:{postId}:{userId} */
    private void cleanupPostLikeKeys() {
        List<String> keys = scanKeys("post_like:*");
        if (keys.isEmpty()) {
            return;
        }
        // 按 postId 分组，批量查库判断帖子是否仍可见，减少逐条查询
        Map<Long, List<String>> byPost = new HashMap<>();
        for (String key : keys) {
            try {
                byPost.computeIfAbsent(Long.parseLong(key.split(":")[1]), k -> new ArrayList<>()).add(key);
            } catch (Exception ignored) {
                // 格式异常的 key 直接跳过，等待 TTL 或人工处理
            }
        }
        Set<Long> alive = new HashSet<>();
        for (List<Long> chunk : partition(byPost.keySet())) {
            for (Post p : postMapper.selectBatchIds(chunk)) {
                if (p.getStatus() != null && p.getStatus() == 1) {
                    alive.add(p.getId());
                }
            }
        }
        List<String> dead = new ArrayList<>();
        for (Map.Entry<Long, List<String>> e : byPost.entrySet()) {
            if (!alive.contains(e.getKey())) {
                dead.addAll(e.getValue());
            }
        }
        if (!dead.isEmpty()) {
            redis.delete(dead);
            log.info("清理无主 post_like key {} 个（帖子已删除/隐藏）", dead.size());
        }
    }

    /** 清理评论已删除后遗留的 comment_like:{commentId}:{userId} */
    private void cleanupCommentLikeKeys() {
        List<String> keys = scanKeys("comment_like:*");
        if (keys.isEmpty()) {
            return;
        }
        Map<Long, List<String>> byComment = new HashMap<>();
        for (String key : keys) {
            try {
                byComment.computeIfAbsent(Long.parseLong(key.split(":")[1]), k -> new ArrayList<>()).add(key);
            } catch (Exception ignored) {
                // 格式异常的 key 直接跳过
            }
        }
        Set<Long> alive = new HashSet<>();
        for (List<Long> chunk : partition(byComment.keySet())) {
            for (Comment c : commentMapper.selectBatchIds(chunk)) {
                // 仅清理评论已删除（status=0）后的无主 key；
                // 帖子隐藏但评论仍在的 key 保留，便于 restorePost 后点赞状态不丢
                if (c.getStatus() != null && c.getStatus() == 1) {
                    alive.add(c.getId());
                }
            }
        }
        List<String> dead = new ArrayList<>();
        for (Map.Entry<Long, List<String>> e : byComment.entrySet()) {
            if (!alive.contains(e.getKey())) {
                dead.addAll(e.getValue());
            }
        }
        if (!dead.isEmpty()) {
            redis.delete(dead);
            log.info("清理无主 comment_like key {} 个（评论已删除）", dead.size());
        }
    }

    /** SCAN 遍历匹配的 key（不用 KEYS，避免阻塞 Redis） */
    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(200).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("扫描 Redis key 失败, pattern={}: {}", pattern, e.getMessage());
        }
        return keys;
    }

    /** 将 ID 集合按 500 一批切分，避免 selectBatchIds 一次传入过大 IN 列表 */
    private List<List<Long>> partition(Set<Long> ids) {
        List<List<Long>> chunks = new ArrayList<>();
        List<Long> current = new ArrayList<>(500);
        for (Long id : ids) {
            current.add(id);
            if (current.size() == 500) {
                chunks.add(current);
                current = new ArrayList<>(500);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }
}
