package com.campus.ai.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.campus.ai.chat.AiService;
import com.campus.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 知识库管理接口（仅 ADMIN 角色）：文件列表、上传、编辑、启停、删除、重建。 */
@SaCheckRole("ADMIN")
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private final AiService aiService;

    /** 知识库文件列表（含大小、后缀、启用状态）。 */
    @GetMapping("/list")
    public R<Map<String, Object>> list() {
        return R.ok(aiService.getKnowledgeList());
    }

    /** 上传知识文件，后台异步入库。 */
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(aiService.uploadKnowledge(file));
    }

    /** 获取文件内容（统一转 Markdown），供后台在线编辑。 */
    @GetMapping("/{filename}/content")
    public R<Map<String, Object>> content(@PathVariable String filename) {
        return R.ok(aiService.getKnowledgeContent(filename));
    }

    /** 更新文件内容并同步重新入库。 */
    @PutMapping("/{filename}")
    public R<Map<String, Object>> update(@PathVariable String filename, @RequestBody Map<String, String> body) {
        return R.ok(aiService.updateKnowledge(filename, body.get("content")));
    }

    /** 切换文件启用/禁用状态。 */
    @PostMapping("/toggle/{filename}")
    public R<Map<String, Object>> toggle(@PathVariable String filename) {
        return R.ok(aiService.toggleKnowledge(filename));
    }

    /** 删除文件及其全部索引分块。 */
    @DeleteMapping("/{filename}")
    public R<Void> delete(@PathVariable String filename) {
        aiService.deleteKnowledge(filename);
        return R.ok();
    }

    /** 触发全量重建索引（异步）。 */
    @PostMapping("/rebuild")
    public R<Map<String, Object>> rebuild() {
        return R.ok(aiService.rebuildKnowledge());
    }

    /** 查询重建进度。 */
    @GetMapping("/rebuild/status")
    public R<Map<String, Object>> rebuildStatus() {
        return R.ok(aiService.getRebuildStatus());
    }
}
