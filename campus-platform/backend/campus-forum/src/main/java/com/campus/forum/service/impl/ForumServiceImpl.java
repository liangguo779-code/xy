package com.campus.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.entity.BanRecord;
import com.campus.common.entity.ContentAuditLog;
import com.campus.common.exception.BusinessException;
import com.campus.common.moderation.AuditLogService;
import com.campus.common.moderation.ContentFilterService;
import com.campus.common.service.BanService;
import com.campus.common.service.NotificationService;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;
import com.campus.forum.entity.PostFavorite;
import com.campus.forum.mapper.CommentMapper;
import com.campus.forum.mapper.PostFavoriteMapper;
import com.campus.forum.mapper.PostMapper;
import com.campus.forum.service.ForumService;
import com.campus.forum.service.PostHotService;
import com.campus.forum.service.PostIndexService;
import com.campus.forum.service.PostTopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 论坛业务实现：帖子与评论的增删改查、评论树组装、点赞与收藏（点赞状态存 Redis，计数落库）。
 * 浏览/点赞/评论后同步回写 ES 计数字段，保证 hot 排序不使用陈旧值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumServiceImpl extends ServiceImpl<PostMapper, Post> implements ForumService {

    /** 深分页上限：page*size 超过该值直接拒绝，与 ES max_result_window 口径一致 */
    private static final int MAX_RESULT_WINDOW = 10000;

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final BanService banService;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final PostIndexService postIndexService;
    private final PostHotService postHotService;
    private final PostTopService postTopService;
    private final ContentFilterService contentFilterService;
    private final AuditLogService auditLogService;

    /**
     * 分页查询正常状态（status=1）帖子，支持多维筛选（分类/关键词/时间范围）与排序（最新/热度）。
     * 关键词检索走 Elasticsearch 倒排索引（ik 分词），ES 异常时降级数据库 LIKE。
     * 两路排序口径一致（P1-13）：hot 为 like*5+comment*3+view 加权，latest 为置顶优先+时间倒序。
     */
    @Override
    public Page<Post> listPosts(String category, String keyword, String sortBy,
                                java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
                                int page, int size) {
        // 深分页守卫（P2-3）：ES 受 max_result_window=10000 限制，DB 深 offset 也低效，
        // 统一提前拦截并给出友好提示
        if ((long) page * size > MAX_RESULT_WINDOW) {
            throw new BusinessException("分页超出限制，最多查询前 " + MAX_RESULT_WINDOW
                    + " 条结果，请增加筛选条件或调整时间范围缩小结果集");
        }
        if (StringUtils.hasText(keyword)) {
            try {
                PostIndexService.SearchResult result = postIndexService.search(
                        keyword, category, sortBy, startTime, endTime, (page - 1) * size, size);
                Map<Long, Post> byId = new HashMap<>();
                if (!result.ids().isEmpty()) {
                    // 回表必须过滤 status=1（P0-6）：ES 同步可能滞后，已删/隐藏帖子
                    // 不能因索引残留而泄漏到搜索结果
                    for (Post p : list(new LambdaQueryWrapper<Post>()
                            .in(Post::getId, result.ids())
                            .eq(Post::getStatus, 1))) {
                        byId.put(p.getId(), p);
                    }
                }
                // 保持 ES 返回顺序（相关度或所选排序）
                List<Post> ordered = result.ids().stream()
                        .map(byId::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                // ES 有结果才返回；0 条时降级 MySQL LIKE（与 trade GoodsServiceImpl 口径一致）：
                // 索引重建滞后/漏同步时，不能因索引为空而永远搜不到已存在的帖子
                if (result.total() > 0) {
                    // 回填搜索高亮片段（P1-15）：命中词用 <em> 包裹，与 trade GoodsSearchService 一致
                    for (Post p : ordered) {
                        Map<String, List<String>> hl = result.highlights().get(p.getId());
                        if (hl == null) {
                            continue;
                        }
                        List<String> titleHl = hl.get("title");
                        if (titleHl != null && !titleHl.isEmpty()) {
                            p.setTitle(titleHl.get(0));
                        }
                        List<String> contentHl = hl.get("content");
                        if (contentHl != null && !contentHl.isEmpty()) {
                            p.setContent(contentHl.get(0));
                        }
                    }
                    Page<Post> resultPage = new Page<>(page, size, result.total());
                    resultPage.setRecords(ordered);
                    return resultPage;
                }
                log.info("ES 搜索无结果，降级到 MySQL: keyword={}", keyword);
            } catch (BusinessException e) {
                // 深分页守卫等业务校验错误不能被降级路径吞掉
                throw e;
            } catch (Exception e) {
                log.warn("ES 检索失败，降级数据库 LIKE: {}", e.getMessage());
            }
        }
        // 无筛选默认列表（访问热路径）：置顶帖走 Redis 缓存（cache-aside），
        // 与非置顶帖时间序拼接，整体序与原查询 is_top DESC, create_time DESC 一致；
        // 带筛选/热度过滤走下方原查询，避免缓存与筛选口径纠缠
        if (!StringUtils.hasText(keyword) && !StringUtils.hasText(category)
                && startTime == null && endTime == null && !"hot".equals(sortBy)) {
            return listDefaultWithTopCache(page, size);
        }
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 1);
        if (StringUtils.hasText(category)) wrapper.eq(Post::getCategory, category);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Post::getTitle, keyword).or().like(Post::getContent, keyword));
        }
        if (startTime != null) wrapper.ge(Post::getCreateTime, startTime);
        if (endTime != null) wrapper.lt(Post::getCreateTime, endTime);
        if ("hot".equals(sortBy)) {
            // 热度加权排序：点赞*5 + 评论*3 + 浏览（权重与 PostHotService 及 ES script sort 一致）；
            // last() 与 orderBy* 不能混用，否则会出现两个 ORDER BY
            wrapper.last("ORDER BY (like_count * 5 + comment_count * 3 + view_count) DESC, is_top DESC, create_time DESC");
        } else {
            // 默认/最新：置顶优先，再按创建时间倒序
            wrapper.orderByDesc(Post::getIsTop)
                    .orderByDesc(Post::getCreateTime);
        }
        return page(new Page<>(page, size), wrapper);
    }

    /**
     * 无筛选默认列表：置顶帖（Redis 缓存）+ 非置顶帖（时间倒序）拼接分页。
     * 虚拟列表 = [置顶帖..., 非置顶帖...]，切片序与整表 is_top DESC, create_time DESC 一致；
     * 非置顶帖用 isTop=0 过滤天然排除置顶，不会与缓存切片重复。
     */
    private Page<Post> listDefaultWithTopCache(int page, int size) {
        List<Post> pinned = postTopService.getTopPosts();
        int pinnedCount = pinned.size();
        int start = (page - 1) * size;

        // 本页的置顶部分
        List<Post> records = new ArrayList<>();
        if (start < pinnedCount) {
            records.addAll(pinned.subList(start, Math.min(pinnedCount, start + size)));
        }

        // 非置顶帖总数（分页 total 用）与本页的非置顶部分（wrapper 各自独立，避免渲染歧义）
        long recentTotal = count(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 1)
                .eq(Post::getIsTop, 0));
        int need = size - records.size();
        if (need > 0) {
            long recentOffset = Math.max(0, start - pinnedCount);
            records.addAll(list(new LambdaQueryWrapper<Post>()
                    .eq(Post::getStatus, 1)
                    .eq(Post::getIsTop, 0)
                    .orderByDesc(Post::getCreateTime)
                    .last("LIMIT " + need + " OFFSET " + recentOffset)));
        }

        Page<Post> result = new Page<>(page, size, pinnedCount + recentTotal);
        result.setRecords(records);
        return result;
    }

    /** 获取帖子详情并累加浏览量，userId 非空时回填当前用户点赞状态 */
    @Override
    public Post getPostDetail(Long id, Long userId) {
        Post post = getById(id);
        if (post == null || post.getStatus() != 1) throw new BusinessException("帖子不存在");
        // 用 SQL 原子自增浏览数，避免并发场景下读改写互相覆盖
        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, id).setSql("view_count = view_count + 1"));
        // getById 拿到的是自增前旧值，回填自增后的浏览数（先判 null）
        post.setViewCount(post.getViewCount() == null ? 1 : post.getViewCount() + 1);
        postHotService.onView(id);
        // 同步 ES 计数字段（hot 排序依赖 view/like/comment 计数），复用同一次计数推算
        postIndexService.updateCounts(id, post.getViewCount(), post.getLikeCount(), post.getCommentCount());
        if (userId != null) {
            // 点赞状态存于 Redis，存在 key 即视为已点赞
            String likeKey = "post_like:" + id + ":" + userId;
            post.setLiked(Boolean.TRUE.equals(redisTemplate.hasKey(likeKey)));
        }
        return post;
    }

    /** 发帖：校验论坛封禁与内容安全后落库，计数清零、未置顶、状态正常 */
    @Override
    public Post createPost(Long userId, Post post) {
        // 检查论坛封禁
        BanRecord ban = banService.checkUserBan(userId, "forum");
        if (ban != null) {
            throw new BusinessException("您的论坛功能已被封禁，原因：" + ban.getReason()
                    + "，解封时间：" + ban.getBanUntil());
        }

        // 内容安全过滤：标题与正文分别过本地关键词库（level1 拒绝 / level2 替换 / level3 送审）
        ContentFilterService.FilterResult titleCheck = contentFilterService.filter(post.getTitle());
        ContentFilterService.FilterResult bodyCheck = contentFilterService.filter(post.getContent());
        if (titleCheck.action() == ContentFilterService.Action.REJECT
                || bodyCheck.action() == ContentFilterService.Action.REJECT) {
            List<String> hits = new ArrayList<>(titleCheck.hitWords());
            hits.addAll(bodyCheck.hitWords());
            publishAudit("post", null, post.getTitle() + "\n" + post.getContent(), hits, 0);
            throw new BusinessException("内容包含违规信息，请修改后发布");
        }
        post.setTitle(titleCheck.finalText());
        post.setContent(bodyCheck.finalText());
        boolean needReview = titleCheck.needReview() || bodyCheck.needReview();

        post.setUserId(userId);
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setIsTop(0);
        // level3 自定义词命中 → 送审（status=3 暂不展示，待人审后恢复）
        post.setStatus(needReview ? 3 : 1);
        save(post);
        List<String> hits = new ArrayList<>(titleCheck.hitWords());
        hits.addAll(bodyCheck.hitWords());
        if (!hits.isEmpty()) {
            publishAudit("post", post.getId(), post.getTitle() + "\n" + post.getContent(), hits, post.getStatus());
        }
        // 同步 ES 搜索索引 + 初始化热度
        postIndexService.indexPost(post);
        postHotService.onCreate(post.getId());
        return post;
    }

    /** 编辑帖子（仅作者）：只覆盖标题/正文/分类/图片，计数与状态不受影响 */
    @Override
    public Post updatePost(Long userId, Long id, Post post) {
        Post existing = getById(id);
        if (existing == null || existing.getStatus() != 1) throw new BusinessException("帖子不存在");
        if (!existing.getUserId().equals(userId)) throw new BusinessException("只能编辑自己的帖子");

        existing.setTitle(post.getTitle());
        existing.setContent(post.getContent());
        existing.setCategory(post.getCategory());
        existing.setImages(post.getImages());
        updateById(existing);
        // 同步 ES 搜索索引
        postIndexService.indexPost(existing);
        // 置顶帖内容可能被缓存，编辑后失效
        postTopService.invalidate();
        return existing;
    }

    /** 删除帖子（仅作者）：逻辑删除，status 置 0 保留数据便于恢复与审计 */
    @Override
    public void deletePost(Long userId, Long id) {
        Post existing = getById(id);
        if (existing == null || existing.getStatus() != 1) throw new BusinessException("帖子不存在");
        if (!existing.getUserId().equals(userId)) throw new BusinessException("只能删除自己的帖子");

        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, id).set(Post::getStatus, 0));
        // 同步移除 ES 索引与热度/缓存
        postIndexService.deletePost(id);
        postHotService.onDelete(id);
        postTopService.invalidate();
    }

    /** 热门帖子 top-N：zset 热度 + Redis 缓存（cache-aside，TTL 60 秒） */
    @Override
    public List<Post> getHotPosts(int size) {
        return postHotService.getHotPosts(size);
    }

    /** 点赞/取消点赞帖子（开关语义），返回 true-点赞 false-取消点赞 */
    @Override
    @Transactional
    public boolean likePost(Long userId, Long postId) {
        Post target = getById(postId);
        if (target == null || target.getStatus() != 1) throw new BusinessException("帖子不存在");
        // Redis key 记录点赞状态，key 已存在则本次为取消点赞
        String likeKey = "post_like:" + postId + ":" + userId;
        Boolean alreadyLiked = redisTemplate.hasKey(likeKey);
        if (Boolean.TRUE.equals(alreadyLiked)) {
            redisTemplate.delete(likeKey);
            // GREATEST 防止并发取消点赞时计数被减成负数
            update(new LambdaUpdateWrapper<Post>().eq(Post::getId, postId).setSql("like_count = GREATEST(like_count - 1, 0)"));
            postHotService.onLike(postId, false);
            // 一次查库取最新计数并同步 ES
            Post latest = getById(postId);
            if (latest != null) {
                postIndexService.updateCounts(postId, latest.getViewCount(), latest.getLikeCount(), latest.getCommentCount());
            }
            return false;
        }
        redisTemplate.opsForValue().set(likeKey, "1");
        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, postId).setSql("like_count = like_count + 1"));
        postHotService.onLike(postId, true);
        // 一次查库结果既用于同步 ES 计数，也用于通知帖子作者
        Post post = getById(postId);
        if (post != null) {
            postIndexService.updateCounts(postId, post.getViewCount(), post.getLikeCount(), post.getCommentCount());
            if (!post.getUserId().equals(userId)) {
                notificationService.send(post.getUserId(), "forum_like",
                        "帖子被点赞", "有人点赞了你的帖子「" + post.getTitle() + "」",
                        "{\"postId\":" + postId + "}");
            }
        }
        return true;
    }

    /** 分页查询帖子的正常评论（status=1），按时间正序；userId 非空时批量回填点赞状态 */
    @Override
    public Page<Comment> getComments(Long postId, Long userId, int page, int size) {
        Page<Comment> result = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .eq(Comment::getStatus, 1)
                        .orderByAsc(Comment::getCreateTime));
        // 与评论树接口一致：批量回填当前用户点赞状态（P3-8）
        fillLiked(result.getRecords(), userId);
        return result;
    }

    /** 获取帖子评论树：根评论为返回值，楼中楼挂在各节点 children 下 */
    @Override
    public List<Comment> getCommentTree(Long postId, Long userId) {
        // 一次查出全部正常评论再在内存中组树，避免逐层递归查库
        List<Comment> all = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .eq(Comment::getStatus, 1)
                        .orderByAsc(Comment::getCreateTime));

        // 与平铺接口共用同一套批量回填逻辑，保证两种返回结构下 liked 语义一致（P3-8）
        fillLiked(all, userId);

        // parentId 为 null 或 0 的是根评论，其余按 parentId 分组挂到对应节点的 children
        Map<Long, List<Comment>> parentMap = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(Comment::getParentId));

        List<Comment> roots = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .collect(Collectors.toList());

        for (Comment c : all) {
            c.setChildren(parentMap.getOrDefault(c.getId(), Collections.emptyList()));
        }

        return roots;
    }

    /**
     * 批量回填当前用户对评论的点赞状态（multiGet 一次往返，避免逐条 hasKey 的 N 次 RTT）。
     * 平铺接口与评论树接口共用，保证 liked 语义一致：true-已点赞 false-未点赞，未登录不回填。
     */
    private void fillLiked(List<Comment> comments, Long userId) {
        if (userId == null || comments == null || comments.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(comments.size());
        for (Comment c : comments) {
            keys.add("comment_like:" + c.getId() + ":" + userId);
        }
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        for (int i = 0; i < comments.size(); i++) {
            // multiGet 与 keys 顺序一致：value 非空即存在 key，视为已点赞
            comments.get(i).setLiked(values != null && values.get(i) != null);
        }
    }

    /** 点赞/取消点赞评论（开关语义），返回 true-点赞 false-取消点赞 */
    @Override
    @Transactional
    public boolean likeComment(Long userId, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getStatus() != 1) throw new BusinessException("评论不存在");
        // 与帖子点赞同理：Redis key 判定状态，GREATEST 防止计数为负
        String likeKey = "comment_like:" + commentId + ":" + userId;
        Boolean alreadyLiked = redisTemplate.hasKey(likeKey);
        if (Boolean.TRUE.equals(alreadyLiked)) {
            redisTemplate.delete(likeKey);
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, commentId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
            return false;
        }
        redisTemplate.opsForValue().set(likeKey, "1");
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .eq(Comment::getId, commentId)
                .setSql("like_count = like_count + 1"));
        // 评论点赞补通知（P2-11）：只通知评论作者，自赞不打扰
        if (!comment.getUserId().equals(userId)) {
            Post post = getById(comment.getPostId());
            String postTitle = post != null ? post.getTitle() : "";
            notificationService.send(comment.getUserId(), "forum_like",
                    "评论被点赞", "有人点赞了你在「" + postTitle + "」下的评论",
                    "{\"postId\":" + comment.getPostId() + ",\"commentId\":" + commentId + "}");
        }
        return true;
    }

    /** 发表评论或回复：落库、累加帖子评论数，并通知帖子作者与被回复者 */
    @Override
    public Comment createComment(Long userId, Long postId, Long parentId, String content, String images) {
        // 检查论坛封禁
        BanRecord ban = banService.checkUserBan(userId, "forum");
        if (ban != null) {
            throw new BusinessException("您的论坛功能已被封禁，原因：" + ban.getReason()
                    + "，解封时间：" + ban.getBanUntil());
        }
        Post post = getById(postId);
        if (post == null || post.getStatus() != 1) throw new BusinessException("帖子不存在");

        // 内容安全过滤：本地关键词库（level1 拒绝 / level2 替换 / level3 送审）
        ContentFilterService.FilterResult filtered = contentFilterService.filter(content);
        if (filtered.action() == ContentFilterService.Action.REJECT) {
            publishAudit("comment", null, content, filtered.hitWords(), 0);
            throw new BusinessException("评论包含违规信息，请修改后发布");
        }

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        // 未指定父评论时记 0，表示一级评论
        comment.setParentId(parentId != null ? parentId : 0L);
        comment.setContent(filtered.finalText());
        comment.setImages(images);
        comment.setLikeCount(0);
        // level3 自定义词命中 → 送审（status=3 暂不展示，待人审后恢复）
        comment.setStatus(filtered.needReview() ? 3 : 1);
        commentMapper.insert(comment);
        if (!filtered.hitWords().isEmpty()) {
            publishAudit("comment", comment.getId(), filtered.finalText(), filtered.hitWords(), comment.getStatus());
        }

        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, postId).setSql("comment_count = comment_count + 1"));
        postHotService.onComment(postId);
        // 一次查库取最新计数，用于同步 ES 计数字段
        Post latest = getById(postId);
        if (latest != null) {
            postIndexService.updateCounts(postId, latest.getViewCount(), latest.getLikeCount(), latest.getCommentCount());
        }

        // 通知帖子作者有新评论
        if (!post.getUserId().equals(userId)) {
            notificationService.send(post.getUserId(), "forum_comment",
                    "帖子有新评论", "有人评论了你的帖子「" + post.getTitle() + "」",
                    "{\"postId\":" + postId + "}");
        }

        // 如果是回复评论，通知被回复的评论作者
        if (parentId != null && parentId > 0) {
            Comment parentComment = commentMapper.selectById(parentId);
            // 被回复者若就是帖子作者，上面已发过新评论通知，跳过以免重复打扰
            if (parentComment != null && !parentComment.getUserId().equals(userId)
                    && !parentComment.getUserId().equals(post.getUserId())) {
                notificationService.send(parentComment.getUserId(), "forum_reply",
                        "评论被回复", "有人回复了你在「" + post.getTitle() + "」下的评论",
                        "{\"postId\":" + postId + "}");
            }
        }

        return comment;
    }

    /**
     * 写发布环节的审计流水（trigger=1）。
     * 拒绝发布时对象尚未落库，targetId 记 0、finalStatus 记 0（拒绝发布约定值）。
     */
    private void publishAudit(String targetType, Long targetId, String content,
                              List<String> hits, Integer finalStatus) {
        if (hits == null || hits.isEmpty()) return;
        ContentAuditLog entry = new ContentAuditLog();
        entry.setTargetType(targetType);
        entry.setTargetId(targetId == null ? 0L : targetId);
        entry.setTriggerType(AuditLogService.TRIGGER_PUBLISH);
        entry.setContent(content);
        entry.setHitWords(String.join(",", hits));
        entry.setFinalStatus(finalStatus);
        auditLogService.record(entry);
    }

    /** 删除评论（逻辑删除）：评论作者或帖子作者可操作，连同楼中楼一并删除并回滚计数与热度 */
    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getStatus() != 1) throw new BusinessException("评论不存在");
        Post post = getById(comment.getPostId());
        boolean isCommentAuthor = comment.getUserId().equals(userId);
        boolean isPostAuthor = post != null && post.getUserId().equals(userId);
        // 权限：本人的评论，或自己帖子下他人的评论（P2-11）
        if (!isCommentAuthor && !isPostAuthor) {
            throw new BusinessException("只能删除自己的评论或自己帖子下的评论");
        }
        doDeleteComment(comment);
    }

    /** 管理端删除评论：跳过作者校验，回滚逻辑与用户删除一致 */
    @Override
    @Transactional
    public void adminDeleteComment(Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) throw new BusinessException("评论不存在");
        if (comment.getStatus() != 1) return; // 已删评论幂等返回
        doDeleteComment(comment);
    }

    /**
     * 删除评论的公共实现：
     * 连同楼中楼回复一起逻辑删除（父删子留会变成挂不到树上的孤儿回复），
     * 并回滚帖子评论计数（GREATEST 防负）、热度分与 ES 计数字段，
     * 同时清理被删评论的点赞状态 key，避免无主 key 滞留。
     */
    private void doDeleteComment(Comment comment) {
        Long postId = comment.getPostId();
        // 查出该帖全部正常评论，递归收集楼中楼
        List<Comment> all = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getStatus, 1));
        Map<Long, List<Comment>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(Comment::getParentId));
        List<Comment> toDelete = new ArrayList<>();
        collectWithReplies(comment, childrenMap, toDelete);

        List<Long> ids = toDelete.stream().map(Comment::getId).collect(Collectors.toList());
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .in(Comment::getId, ids)
                .set(Comment::getStatus, 0));

        // 回滚帖子评论计数与热度分（发评论时每条 +1 计数 / +3 热度，删除对称回滚）
        int removed = ids.size();
        update(new LambdaUpdateWrapper<Post>().eq(Post::getId, postId)
                .setSql("comment_count = GREATEST(comment_count - " + removed + ", 0)"));
        postHotService.onCommentsRemoved(postId, removed);

        // 同步 ES 计数字段，保证 hot 排序不使用陈旧值
        Post latest = getById(postId);
        if (latest != null) {
            postIndexService.updateCounts(postId, latest.getViewCount(), latest.getLikeCount(), latest.getCommentCount());
        }

        // 清理被删评论的点赞状态 key（无主 key 由每日清理兜底，这里即时清理更及时）
        for (Comment c : toDelete) {
            try {
                Set<String> likeKeys = redisTemplate.keys("comment_like:" + c.getId() + ":*");
                if (likeKeys != null && !likeKeys.isEmpty()) {
                    redisTemplate.delete(likeKeys);
                }
            } catch (Exception e) {
                log.warn("清理评论点赞 key 失败, commentId={}: {}", c.getId(), e.getMessage());
            }
        }
    }

    /** 递归收集评论及其全部楼中楼回复 */
    private void collectWithReplies(Comment root, Map<Long, List<Comment>> childrenMap, List<Comment> out) {
        out.add(root);
        for (Comment child : childrenMap.getOrDefault(root.getId(), Collections.emptyList())) {
            collectWithReplies(child, childrenMap, out);
        }
    }

    /** 收藏/取消收藏帖子（开关语义），返回 true-收藏 false-取消收藏 */
    @Override
    public boolean toggleFavorite(Long userId, Long postId) {
        PostFavorite existing = postFavoriteMapper.selectOne(
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .eq(PostFavorite::getPostId, postId));
        // 已收藏则删除记录（取消收藏），否则新增，一次接口完成开关
        if (existing != null) {
            postFavoriteMapper.deleteById(existing.getId());
            return false;
        }
        PostFavorite fav = new PostFavorite();
        fav.setUserId(userId);
        fav.setPostId(postId);
        postFavoriteMapper.insert(fav);
        return true;
    }

    /** 查询用户是否已收藏该帖子 */
    @Override
    public boolean isFavorited(Long userId, Long postId) {
        return postFavoriteMapper.selectCount(
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .eq(PostFavorite::getPostId, postId)) > 0;
    }

    /** 分页查询用户收藏的帖子，按收藏时间倒序 */
    @Override
    public Page<Post> getMyFavorites(Long userId, int page, int size) {
        Page<PostFavorite> favPage = postFavoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .orderByDesc(PostFavorite::getCreateTime));

        Page<Post> result = new Page<>(page, size, favPage.getTotal());
        // 先按收藏记录分页再回查帖子，已删除的帖子会被过滤，本页条数可能少于 size
        List<Post> posts = favPage.getRecords().stream()
                .map(f -> getById(f.getPostId()))
                .filter(p -> p != null && p.getStatus() == 1)
                .collect(Collectors.toList());
        result.setRecords(posts);
        return result;
    }

    /** 分页查询用户自己发布的正常帖子 */
    @Override
    public Page<Post> getMyPosts(Long userId, int page, int size) {
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .eq(Post::getStatus, 1)
                        .orderByDesc(Post::getCreateTime));
    }
}
