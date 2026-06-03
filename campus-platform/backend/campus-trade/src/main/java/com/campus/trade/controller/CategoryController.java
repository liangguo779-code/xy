package com.campus.trade.controller;

import com.campus.common.result.R;
import com.campus.trade.entity.Category;
import com.campus.trade.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public R<List<Category>> list() {
        return R.ok(categoryService.getCategoryTree());
    }
}
