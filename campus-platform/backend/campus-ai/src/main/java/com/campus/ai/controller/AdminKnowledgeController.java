package com.campus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.campus.ai.service.AiService;
import com.campus.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@SaCheckRole("ADMIN")
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private final AiService aiService;

    @GetMapping("/list")
    public R<Map<String, Object>> list() {
        return R.ok(aiService.getKnowledgeList());
    }

    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(aiService.uploadKnowledge(file));
    }

    @GetMapping("/{filename}/content")
    public R<Map<String, Object>> content(@PathVariable String filename) {
        return R.ok(aiService.getKnowledgeContent(filename));
    }

    @PutMapping("/{filename}")
    public R<Map<String, Object>> update(@PathVariable String filename, @RequestBody Map<String, String> body) {
        return R.ok(aiService.updateKnowledge(filename, body.get("content")));
    }

    @PostMapping("/toggle/{filename}")
    public R<Map<String, Object>> toggle(@PathVariable String filename) {
        return R.ok(aiService.toggleKnowledge(filename));
    }

    @DeleteMapping("/{filename}")
    public R<Void> delete(@PathVariable String filename) {
        aiService.deleteKnowledge(filename);
        return R.ok();
    }

    @PostMapping("/rebuild")
    public R<Map<String, Object>> rebuild() {
        return R.ok(aiService.rebuildKnowledge());
    }

    @GetMapping("/rebuild/status")
    public R<Map<String, Object>> rebuildStatus() {
        return R.ok(aiService.getRebuildStatus());
    }
}
