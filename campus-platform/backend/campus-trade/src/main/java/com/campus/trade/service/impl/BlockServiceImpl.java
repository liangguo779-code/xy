package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.R;
import com.campus.trade.entity.Block;
import com.campus.trade.mapper.BlockMapper;
import com.campus.trade.service.BlockService;
import com.campus.user.feign.UserFeignClient;
import com.campus.user.feign.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlockServiceImpl extends ServiceImpl<BlockMapper, Block> implements BlockService {

    private final BlockMapper blockMapper;
    private final UserFeignClient userFeignClient;

    @Override
    @Transactional
    public void blockUser(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            throw new BusinessException("不能拉黑自己");
        }
        // 检查是否已拉黑
        Long count = blockMapper.selectCount(
                new LambdaQueryWrapper<Block>()
                        .eq(Block::getUserId, userId)
                        .eq(Block::getBlockedUserId, blockedUserId));
        if (count > 0) {
            throw new BusinessException("已拉黑该用户");
        }
        Block block = new Block();
        block.setUserId(userId);
        block.setBlockedUserId(blockedUserId);
        blockMapper.insert(block);
    }

    @Override
    @Transactional
    public void unblockUser(Long userId, Long blockedUserId) {
        blockMapper.delete(
                new LambdaQueryWrapper<Block>()
                        .eq(Block::getUserId, userId)
                        .eq(Block::getBlockedUserId, blockedUserId));
    }

    @Override
    public boolean isBlocked(Long userId, Long targetUserId) {
        return blockMapper.selectCount(
                new LambdaQueryWrapper<Block>()
                        .eq(Block::getUserId, userId)
                        .eq(Block::getBlockedUserId, targetUserId)) > 0;
    }

    @Override
    public boolean isBlockedBy(Long userId, Long targetUserId) {
        return blockMapper.selectCount(
                new LambdaQueryWrapper<Block>()
                        .eq(Block::getUserId, targetUserId)
                        .eq(Block::getBlockedUserId, userId)) > 0;
    }

    @Override
    public Page<UserVO> getBlockList(Long userId, int page, int size) {
        Page<Block> blockPage = blockMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Block>()
                        .eq(Block::getUserId, userId)
                        .orderByDesc(Block::getCreateTime));

        List<UserVO> users = new ArrayList<>();
        for (Block block : blockPage.getRecords()) {
            try {
                R<UserVO> result = userFeignClient.getUserById(block.getBlockedUserId());
                if (result.getCode() == 200 && result.getData() != null) {
                    users.add(result.getData());
                }
            } catch (Exception e) {
                // Feign 调用失败时跳过
            }
        }

        Page<UserVO> result = new Page<>(page, size, blockPage.getTotal());
        result.setRecords(users);
        return result;
    }
}
