package com.campus.app.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.campus.common.entity.User;
import com.campus.common.mapper.UserMapper;
import com.campus.common.result.R;
import com.campus.forum.entity.Comment;
import com.campus.forum.entity.Post;
import com.campus.forum.mapper.CommentMapper;
import com.campus.forum.mapper.PostMapper;
import com.campus.trade.entity.ChatMessage;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Report;
import com.campus.trade.mapper.ChatMessageMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理后台-举报目标内容查询（仅 ADMIN）：
 * 举报管理页需要看到被举报的原文才能裁决，但评论/帖子在 forum、商品/消息在 trade，
 * 业务模块互不依赖 —— 本控制器放在聚合层 campus-app，按 targetType 回查原文。
 */
@SaCheckRole("ADMIN")
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportTargetController {

    private final ReportMapper reportMapper;
    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final GoodsMapper goodsMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;

    /** 查询某条举报目标的原文内容与作者（处理举报时展示裁决依据） */
    @GetMapping("/{id}/target")
    public R<Map<String, Object>> target(@PathVariable Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) return R.fail("举报不存在");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetType", report.getTargetType());
        data.put("targetId", report.getTargetId());
        String content = null;
        Long authorId = null;
        Integer targetStatus = null;

        switch (report.getTargetType() == null ? "" : report.getTargetType()) {
            case "comment" -> {
                Comment c = commentMapper.selectById(report.getTargetId());
                if (c != null) {
                    content = c.getContent();
                    authorId = c.getUserId();
                    targetStatus = c.getStatus();
                }
            }
            case "post" -> {
                Post p = postMapper.selectById(report.getTargetId());
                if (p != null) {
                    content = p.getTitle() + "\n" + p.getContent();
                    authorId = p.getUserId();
                    targetStatus = p.getStatus();
                }
            }
            case "goods" -> {
                Goods g = goodsMapper.selectById(report.getTargetId());
                if (g != null) {
                    content = g.getTitle();
                    authorId = g.getUserId();
                }
            }
            case "message" -> {
                ChatMessage m = chatMessageMapper.selectById(report.getTargetId());
                if (m != null) {
                    content = m.getContent();
                    authorId = m.getSenderId();
                }
            }
            case "user" -> {
                User u = userMapper.selectById(report.getTargetId());
                if (u != null) {
                    content = "被举报用户：" + u.getNickname() + "（ID " + u.getId() + "）";
                    authorId = u.getId();
                }
            }
            default -> content = "未知目标类型";
        }

        data.put("content", content == null ? "（目标已不存在或已删除）" : content);
        if (authorId != null) {
            User author = userMapper.selectById(authorId);
            data.put("authorId", authorId);
            data.put("authorNickname", author == null ? String.valueOf(authorId) : author.getNickname());
        }
        if (targetStatus != null) data.put("targetStatus", targetStatus);
        return R.ok(data);
    }
}
