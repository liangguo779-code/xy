package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.entity.Block;
import com.campus.common.entity.User;
import com.campus.trade.mapper.BlockMapper;
import com.campus.common.mapper.UserMapper;
import com.campus.trade.service.BlockService;
import com.campus.common.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlockServiceImpl extends ServiceImpl<BlockMapper, Block> implements BlockService {

    private final BlockMapper blockMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public void blockUser(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            throw new BusinessException("不能拉黑自己");
        }
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
            User user = userMapper.selectById(block.getBlockedUserId());
            if (user != null) {
                UserVO vo = new UserVO();
                BeanUtils.copyProperties(user, vo);
                users.add(vo);
            }
        }

        Page<UserVO> result = new Page<>(page, size, blockPage.getTotal());
        result.setRecords(users);
        return result;
    }
}
