package com.xiafan.agent.controller;

import com.xiafan.agent.common.ApiResponse;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.service.MinioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors main.py: root /, /health and the /rerank file upload to MinIO.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final MinioService minioService;

    public HealthController(MinioService minioService) {
        this.minioService = minioService;
    }

    @GetMapping("/")
    public ApiResponse<Map<String, String>> root() {
        return new ApiResponse<>("200", Map.of("message", "本地知识库后端服务运行中"));
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return new ApiResponse<>("200", Map.of("status", "healthy"));
    }

    @PostMapping("/rerank")
    public Map<String, Object> rerank(@RequestParam("file_data") MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        MinioService.UploadResult result = minioService.uploadFileWithOriginalFormat(bytes, file.getOriginalFilename());
        if (!result.success()) {
            throw new BusinessException(500, "MinIO错误: 文件上传失败");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "上传成功");
        body.put("object_name", result.objectName());
        return body;
    }
}