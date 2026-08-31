package com.xiafan.agent.service;

import com.xiafan.agent.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** Mirrors minioService.py (object storage, MD5-hashed object names, presigned URLs). */
@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);
    private static final java.util.Map<String, String> SUPPORTED_FILE_TYPES = java.util.Map.of(
            ".txt", "text/plain",
            ".md", "text/markdown",
            ".pdf", "application/pdf",
            ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final AppProperties props;
    private volatile MinioClient client;

    public MinioService(AppProperties props) {
        this.props = props;
        try {
            AppProperties.MinioConfig c = props.getMinio();
            if (c.getEndpoint() == null || c.getEndpoint().isEmpty()) {
                log.warn("MinIO endpoint not configured; MinIO disabled.");
                return;
            }
            // allow MINIO_ENDPOINT in bare "host:port" form (as in the Python .env):
            // the minio SDK requires a scheme, so default to http(s) per MINIO_SECURE
            String endpoint = c.getEndpoint();
            if (!endpoint.toLowerCase().startsWith("http://")
                    && !endpoint.toLowerCase().startsWith("https://")) {
                endpoint = (c.isSecure() ? "https://" : "http://") + endpoint;
            }
            this.client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(c.getAccessKey(), c.getSecretKey())
                    .build();
        } catch (Exception e) {
            log.warn("MinIO init failed: {}", e.getMessage());
            this.client = null;
        }
    }

    private MinioClient client() {
        if (client == null) {
            throw new IllegalStateException("MinIO is not configured");
        }
        return client;
    }

    private String bucket() {
        return props.getMinio().getBucket();
    }

    /** Bucket used for image uploads; falls back to the default bucket when unset. */
    public String imageBucket() {
        String b = props.getMinio().getImgBucket();
        return (b == null || b.isBlank()) ? props.getMinio().getBucket() : b;
    }

    public boolean isAvailable() {
        return client != null;
    }

    private void ensureBucketExists(String targetBucket) {
        try {
            if (!client().bucketExists(BucketExistsArgs.builder().bucket(targetBucket).build())) {
                client().makeBucket(MakeBucketArgs.builder().bucket(targetBucket).build());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean uploadFile(byte[] fileData, String objectName, String contentType) {
        return upload(fileData, objectName, contentType, bucket());
    }

    public boolean uploadFileInBucket(byte[] fileData, String objectName, String contentType, String targetBucket) {
        return upload(fileData, objectName, contentType, targetBucket);
    }

    private boolean upload(byte[] fileData, String objectName, String contentType, String targetBucket) {
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        try {
            ensureBucketExists(targetBucket);
            client().putObject(PutObjectArgs.builder()
                    .bucket(targetBucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(fileData), fileData.length, -1)
                    .contentType(contentType)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("MinIO upload failed: {}", e.getMessage());
            return false;
        }
    }

    public byte[] downloadFile(String objectName) {
        try {
            try (InputStream in = client().getObject(
                    GetObjectArgs.builder().bucket(bucket()).object(objectName).build())) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("MinIO download failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean deleteFile(String objectName) {
        try {
            client().removeObject(RemoveObjectArgs.builder().bucket(bucket()).object(objectName).build());
            return true;
        } catch (Exception e) {
            log.warn("MinIO delete failed: {}", e.getMessage());
            return false;
        }
    }

    public String getPresignedUrl(String objectName) {
        return presignedUrl(objectName, bucket());
    }

    private String presignedUrl(String objectName, String targetBucket) {
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(targetBucket)
                    .object(objectName)
                    .expiry(3600)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO presigned URL failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Public (unsigned) URL for an object in a bucket that allows anonymous read (the img bucket),
     * routed through the public domain instead of the server IP. Falls back to a presigned URL
     * when no public domain is configured.
     */
    public String getPublicUrlInBucket(String objectName, String targetBucket) {
        String base = props.getMinio().getPublicUrl();
        if (base == null || base.isBlank()) {
            return presignedUrl(objectName, targetBucket);
        }
        String b = (targetBucket == null || targetBucket.isBlank()) ? bucket() : targetBucket;
        return stripTrailingSlash(base) + "/" + b + "/" + objectName;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") && s.length() > 1 ? s.substring(0, s.length() - 1) : s;
    }

    public String getPresignedPutUrl(String objectName, String contentType) {
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket())
                    .object(objectName)
                    .expiry(3600)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO presigned PUT URL failed: {}", e.getMessage());
            return null;
        }
    }

    public boolean fileExists(String objectName) {
        try {
            client().statObject(StatObjectArgs.builder().bucket(bucket()).object(objectName).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String generateFileHash(byte[] fileData) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(fileData);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String extOf(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx).toLowerCase() : "";
    }

    public static String generateObjectName(String originalFilename, String fileHash) {
        return fileHash + extOf(originalFilename);
    }

    public String contentTypeFor(String ext) {
        return SUPPORTED_FILE_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    public record UploadResult(boolean success, String objectName, String presignedUrl) {
    }

    public UploadResult uploadFileWithOriginalFormat(byte[] fileData, String originalFilename) {
        String ext = extOf(originalFilename);
        String contentType = contentTypeFor(ext);
        String objectName = generateObjectName(originalFilename, generateFileHash(fileData));
        boolean ok = uploadFile(fileData, objectName, contentType);
        if (!ok) {
            return new UploadResult(false, objectName, null);
        }
        return new UploadResult(true, objectName, getPresignedUrl(objectName));
    }

    public UploadResult uploadFileWithOriginalByte(byte[] fileData, String fileName, String fileType) {
        String objectName = generateObjectName(fileName, generateFileHash(fileData));
        boolean ok = uploadFile(fileData, objectName, fileType);
        if (!ok) {
            return new UploadResult(false, objectName, null);
        }
        return new UploadResult(true, objectName, getPresignedUrl(objectName));
    }
}