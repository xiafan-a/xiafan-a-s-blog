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

    private static final Set<String> HEIC_TYPES = Set.of(".heic", ".heif");

    private final ImageRepository repository;
    private final MinioService minioService;
    private final HeicService heicService;

    public ImageService(ImageRepository repository, MinioService minioService, HeicService heicService) {
        this.repository = repository;
        this.minioService = minioService;
        this.heicService = heicService;
    }

    public Image uploadImage(MultipartFile file) {
        byte[] content = readBytes(file, "读取上传文件失败");
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
        Image saved = repository.insert(objectName, originalName, content.length, contentType, dims[0], dims[1], null, 0);
        return withUrl(saved);
    }

    /** Uploads a Live Photo as a single HEIC: splits it into a still JPEG + short video (when the
     *  HEIC carries one), stores both in the img bucket and links them via video_image_id. */
    public Image uploadLiveImage(MultipartFile file) {
        byte[] content = readBytes(file, "读取上传文件失败");
        String originalName = file.getOriginalFilename() == null ? "image.heic" : file.getOriginalFilename();
        String ext = MinioService.extOf(originalName);
        if (!HEIC_TYPES.contains(ext)) {
            throw new BusinessException(400, "实况照片需上传 .heic 文件");
        }

        HeicService.HeicExtraction extraction = heicService.extract(content);

        // the still is always stored as a converted JPEG (browsers can't render HEIC)
        String stillName = replaceExt(originalName, ".jpg");
        String stillObjectName = MinioService.generateObjectName(stillName, MinioService.generateFileHash(extraction.stillJpeg()));

        Integer videoId = extraction.isLive() ? storeVideo(extraction.videoMp4(), originalName) : null;

        Optional<Image> existing = repository.findByImageName(stillObjectName);
        if (existing.isPresent()) {
            Image image = existing.get();
            if (videoId != null && image.getVideoImageId() == null) {
                image.setVideoImageId(videoId);
                image.setHasVideo(1);
                repository.updateById(image);
            }
            return withUrl(repository.findById(image.getId()).orElseThrow());
        }
        if (!minioService.uploadFileInBucket(extraction.stillJpeg(), stillObjectName, "image/jpeg", minioService.imageBucket())) {
            throw new BusinessException(500, "MinIO上传图片失败");
        }
        Integer[] dims = readDimensions(extraction.stillJpeg());
        Image saved = repository.insert(stillObjectName, stillName, extraction.stillJpeg().length, "image/jpeg",
                dims[0], dims[1], videoId, videoId == null ? 0 : 1);
        return withUrl(saved);
    }

    /** Stores an extracted Live Photo video as its own images row and returns its id. */
    private Integer storeVideo(byte[] videoBytes, String heicOriginalName) {
        String videoName = replaceExt(heicOriginalName, ".mp4");
        String videoObjectName = MinioService.generateObjectName(videoName, MinioService.generateFileHash(videoBytes));
        Optional<Image> existingVideo = repository.findByImageName(videoObjectName);
        if (existingVideo.isPresent()) {
            return existingVideo.get().getId();
        }
        if (!minioService.uploadFileInBucket(videoBytes, videoObjectName, "video/mp4", minioService.imageBucket())) {
            throw new BusinessException(500, "MinIO上传视频失败");
        }
        Image videoRow = repository.insert(videoObjectName, videoName, videoBytes.length, "video/mp4", null, null, null, 0);
        return videoRow.getId();
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
        if (image.getVideoImageId() != null) {
            repository.findById(image.getVideoImageId()).ifPresent(video ->
                    image.setVideoUrl(minioService.getPublicUrlInBucket(video.getImageName(), minioService.imageBucket())));
        }
        return image;
    }

    private static byte[] readBytes(MultipartFile file, String errorMessage) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(400, errorMessage);
        }
    }

    private static String replaceExt(String filename, String newExt) {
        int idx = filename.lastIndexOf('.');
        return (idx >= 0 ? filename.substring(0, idx) : filename) + newExt;
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