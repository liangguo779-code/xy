package com.campus.trade.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.entity.BanRecord;
import com.campus.common.exception.BusinessException;
import com.campus.common.service.BanService;
import com.campus.trade.dto.*;
import com.campus.trade.entity.ChatMessage;
import com.campus.trade.entity.ChatSession;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.ChatMessageMapper;
import com.campus.trade.mapper.ChatSessionMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.service.ChatService;
import com.campus.trade.websocket.ChatWebSocketHandler;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;
    private final ChatWebSocketHandler wsHandler;
    private final BanService banService;

    public ChatServiceImpl(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper,
                           GoodsMapper goodsMapper, UserMapper userMapper,
                           @Lazy ChatWebSocketHandler wsHandler, BanService banService) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.goodsMapper = goodsMapper;
        this.userMapper = userMapper;
        this.wsHandler = wsHandler;
        this.banService = banService;
    }

    @Override
    @Transactional
    public ChatSessionVO startSession(Long buyerId, Long goodsId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null || goods.getStatus() != 0) {
            throw new BusinessException("商品不存在或已下架");
        }
        if (goods.getUserId().equals(buyerId)) {
            throw new BusinessException("不能和自己聊天");
        }

        // 查询是否已有会话
        ChatSession existing = sessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getGoodsId, goodsId)
                        .eq(ChatSession::getBuyerId, buyerId)
        );

        if (existing != null) {
            return toSessionVO(existing, buyerId);
        }

        // 创建新会话
        ChatSession session = new ChatSession();
        session.setGoodsId(goodsId);
        session.setBuyerId(buyerId);
        session.setSellerId(goods.getUserId());
        session.setLastMsg("对方对你的商品感兴趣");
        session.setLastTime(LocalDateTime.now());
        session.setStatus(1);
        sessionMapper.insert(session);

        // 发送系统消息
        sendSystemMessage(session.getId(), "会话已建立，双方可以开始沟通商品详情");

        // 增加"我想要"计数
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .setSql("want_count = want_count + 1"));

        return toSessionVO(session, buyerId);
    }

    @Override
    public List<ChatSessionVO> getMySessions(Long userId) {
        List<ChatSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .and(w -> w.eq(ChatSession::getBuyerId, userId)
                                .or().eq(ChatSession::getSellerId, userId))
                        .eq(ChatSession::getStatus, 1)
                        .orderByDesc(ChatSession::getLastTime)
        );

        return sessions.stream()
                .map(s -> toSessionVO(s, userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ChatMessageVO> getMessages(Long sessionId, Long userId, int page, int size) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || (!session.getBuyerId().equals(userId) && !session.getSellerId().equals(userId))) {
            throw new BusinessException(403, "无权访问此会话");
        }

        Page<ChatMessage> pageResult = messageMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );

        return pageResult.getRecords().stream()
                .map(this::toMessageVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatMessageVO sendMessage(Long senderId, SendMessageReq req) {
        // 检查封禁
        BanRecord ban = banService.checkUserBan(senderId, "message");
        if (ban != null) {
            throw new BusinessException("您的私信功能已被封禁，原因：" + ban.getReason()
                    + "，解封时间：" + ban.getBanUntil());
        }

        ChatSession session = sessionMapper.selectById(req.getSessionId());
        if (session == null || session.getStatus() != 1) {
            throw new BusinessException("会话不存在或已关闭");
        }
        if (!session.getBuyerId().equals(senderId) && !session.getSellerId().equals(senderId)) {
            throw new BusinessException(403, "无权在此会话发送消息");
        }

        // 防诈骗检测
        checkFraudKeywords(req.getSessionId(), req.getContent());

        // 保存消息
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(req.getSessionId());
        msg.setSenderId(senderId);
        msg.setMsgType(req.getMsgType());
        msg.setContent(req.getContent());
        msg.setExtra(req.getExtra());
        msg.setIsRead(0);
        messageMapper.insert(msg);

        // 更新会话最后消息
        session.setLastMsg(req.getContent());
        session.setLastTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        // 构建 VO
        ChatMessageVO vo = toMessageVO(msg);

        // WebSocket 推送给对方
        Long targetUserId = session.getBuyerId().equals(senderId)
                ? session.getSellerId() : session.getBuyerId();
        wsHandler.pushToUser(targetUserId, Map.of(
                "type", "new_msg",
                "data", vo
        ));

        return vo;
    }

    @Override
    @Transactional
    public void markAsRead(Long sessionId, Long userId) {
        messageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .ne(ChatMessage::getSenderId, userId)
                .eq(ChatMessage::getIsRead, 0)
                .set(ChatMessage::getIsRead, 1));
    }

    private void sendSystemMessage(Long sessionId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setSenderId(0L); // 系统
        msg.setMsgType(2);
        msg.setContent(content);
        msg.setIsRead(0);
        messageMapper.insert(msg);
    }

    /** 防诈骗敏感词检测 */
    private void checkFraudKeywords(Long sessionId, String content) {
        if (content == null) return;
        String[] fraudKeywords = {"转账", "微信支付", "支付宝付款", "扫二维码", "先付款", "保证金", "押金",
                "中奖", "退款", "加微信", "加qq", "点击链接", "银行卡号", "验证码"};
        String lower = content.toLowerCase();
        for (String keyword : fraudKeywords) {
            if (lower.contains(keyword)) {
                sendSystemMessage(sessionId,
                        "⚠️ 防诈骗提醒：检测到敏感词「" + keyword + "」，请勿在平台外转账付款，谨防诈骗！所有交易请通过平台完成。");
                break;
            }
        }
    }

    private ChatSessionVO toSessionVO(ChatSession session, Long currentUserId) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setGoodsId(session.getGoodsId());
        vo.setLastMsg(session.getLastMsg());
        vo.setLastTime(session.getLastTime());

        // 对方信息
        Long otherUserId = session.getBuyerId().equals(currentUserId)
                ? session.getSellerId() : session.getBuyerId();
        User other = userMapper.selectById(otherUserId);
        if (other != null) {
            vo.setOtherUserId(other.getId());
            vo.setOtherNickname(other.getNickname());
            vo.setOtherAvatar(other.getAvatar());
        }

        // 商品信息
        Goods goods = goodsMapper.selectById(session.getGoodsId());
        if (goods != null) {
            vo.setGoodsTitle(goods.getTitle());
            if (goods.getImages() != null && !goods.getImages().isEmpty()) {
                try {
                    String imgs = goods.getImages();
                    // 提取第一个URL
                    if (imgs.startsWith("[")) {
                        int start = imgs.indexOf("\"") + 1;
                        int end = imgs.indexOf("\"", start);
                        if (start > 0 && end > start) {
                            vo.setGoodsImage(imgs.substring(start, end));
                        }
                    } else {
                        vo.setGoodsImage(imgs.split(",")[0].trim());
                    }
                } catch (Exception ignored) {}
            }
        }

        // 未读数
        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .ne(ChatMessage::getSenderId, currentUserId)
                        .eq(ChatMessage::getIsRead, 0)
        );
        vo.setUnreadCount(count.intValue());

        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessage msg) {
        ChatMessageVO vo = new ChatMessageVO();
        BeanUtil.copyProperties(msg, vo);

        if (msg.getSenderId() > 0) {
            User sender = userMapper.selectById(msg.getSenderId());
            if (sender != null) {
                vo.setSenderNickname(sender.getNickname());
                vo.setSenderAvatar(sender.getAvatar());
            }
        } else {
            vo.setSenderNickname("系统");
        }

        return vo;
    }
}
