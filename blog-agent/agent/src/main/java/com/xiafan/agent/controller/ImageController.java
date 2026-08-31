package com.xiafan.agent.controller;

import com.xiafan.agent.common.ApiResponse;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.Image;
import com.xiafan.agent.service.ImageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Image upload and metadata: stores the file in MinIO under its content hash and keeps the
 * uploader's original name plus metadata in the images table.
 */
@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping
    public Image upload(@RequestParam("file") MultipartFile file) {
        return imageService.uploadImage(file);
    }

    @GetMapping("/{id}")
    public Image get(@PathVariable int id) {
        return imageService.getImage(id);
    }

    @GetMapping("/name/{imageName}")
    public Image getByName(@PathVariable String imageName) {
        return imageService.getImageByName(imageName);
    }

    @GetMapping
    public ApiResponse<List<Image>> list(@RequestParam(defaultValue = "0") int skip,
                                         @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(imageService.listImages(skip, limit));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> delete(@PathVariable int id) {
        if (!imageService.deleteImage(id)) {
            throw new BusinessException(404, "图片不存在");
        }
        return ApiResponse.ok(Map.of("message", "图片删除成功"));
    }
}