package com.xiafan.agent.service;

import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.Image;
import com.xiafan.agent.repository.ImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Image upload: content-hash MinIO object names, deduped rows keyed by image_name. */
@Service
public class ImageService {

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg");

    private static final Map<String, String> IMAGE_MIME = Map.of(
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".gif", "image/gif",
            ".webp", "image/webp",
            ".bmp", "image/bmp",
            ".svg", "image/svg+xml");

    private final ImageRepository repository;
    private final MinioService minioService;

    public ImageService(ImageRepository repository, MinioService minioService) {
        this.repository = repository;
        this.minioService = minioService;
    }

    public Image uploadImage(MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(400, "读取上传文件失败");
        }
        String originalName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String ext = MinioService.extOf(originalName);
        if (!SUPPORTED_IMAGE_TYPES.contains(ext)) {
            throw new BusinessException(400, "不支持的图片类型: " + ext);
        }
        String objectName = MinioService.generateObjectName(originalName, MinioService.generateFileHash(content));
        // same content hashes to the same object name → return the existing row instead of duplicating
        Optional<Image> existing = repository.findByImageName(objectName);
        if (existing.isPresent()) {
            return withUrl(existing.get());
        }
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = IMAGE_MIME.getOrDefault(ext, "application/octet-stream");
        }
        if (!minioService.uploadFileInBucket(content, objectName, contentType, minioService.imageBucket())) {
            throw new BusinessException(500, "MinIO上传失败");
        }
        Integer[] dims = readDimensions(content);
        Image saved = repository.insert(objectName, originalName, content.length, contentType, dims[0], dims[1]);
        return withUrl(saved);
    }

    public Image getImage(int id) {
        return repository.findById(id)
                .map(this::withUrl)
                .orElseThrow(() -> new BusinessException(404, "图片不存在"));
    }

    public Image getImageByName(String imageName) {
        return repository.findByImageName(imageName)
                .map(this::withUrl)
                .orElseThrow(() -> new BusinessException(404, "图片不存在"));
    }

    public List<Image> listImages(int skip, int limit) {
        return repository.findAll(skip, limit).stream().map(this::withUrl).toList();
    }

    public boolean deleteImage(int id) {
        return repository.softDelete(id) > 0;
    }

    private Image withUrl(Image image) {
        image.setUrl(minioService.getPublicUrlInBucket(image.getImageName(), minioService.imageBucket()));
        return image;
    }

    private static Integer[] readDimensions(byte[] data) {
        try (InputStream in = new ByteArrayInputStream(data)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                return new Integer[]{null, null};
            }
            return new Integer[]{img.getWidth(), img.getHeight()};
        } catch (Exception e) {
            return new Integer[]{null, null};
        }
    }
}