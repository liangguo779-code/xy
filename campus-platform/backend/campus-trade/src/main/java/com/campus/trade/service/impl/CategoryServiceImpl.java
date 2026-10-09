package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trade.entity.Category;
import com.campus.trade.mapper.CategoryMapper;
import com.campus.trade.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 商品分类服务实现 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    /** 查询启用状态的全部分类，按排序序号升序（前端自行组装为树） */
    @Override
    public List<Category> getCategoryTree() {
        return list(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSortOrder));
    }
}
