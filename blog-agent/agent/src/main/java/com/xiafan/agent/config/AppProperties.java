package com.xiafan.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings bound from environment variables (mirrors fastApiProject/config/settings.py).
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String uploadDir = "./uploads";
    private int chunkSize = 5000;
    private int chunkOverlap = 600;

    private final RagConfig rag = new RagConfig();
    private final ApiConfig api = new ApiConfig();
    private final EmbeddingConfig embedding = new EmbeddingConfig();
    private final RerankConfig rerank = new RerankConfig();
    private final MinioConfig minio = new MinioConfig();
    private final RedisConfig redis = new RedisConfig();
    private final AgentConfig agent = new AgentConfig();
    private final ChatConfig chat = new ChatConfig();
    private final GuessConfig guess = new GuessConfig();

    @Data
    public static class RagConfig {
        private int topK = 20;
        private double threshold = 0.7;
        private int maxContextLength = 20000;
        private boolean enableQueryOptimization = true;
        private boolean enableSemanticRerank = true;
        private boolean enableContextCompression = true;
        private boolean enableResponseOptimization = true;
        private int rerankTopK = 10;
        private int compressionThreshold = 8000;
        private double querySelectionThreshold = 0.5;
    }

    @Data
    public static class ApiConfig {
        private String key = "";
        private String url = "";
        private String model = "";
        private String preModel = "";
        private String extendModel = "qwen3.5-flash";
        private String resultModel = "";
        private double temperature = 0;
        private boolean useMock = true;
    }

    @Data
    public static class EmbeddingConfig {
        private String model = "";
        private int dimension = 768;
    }

    @Data
    public static class RerankConfig {
        private String apiUrl = "";
        private String model = "qwen3-vl-rerank";
    }

    @Data
    public static class MinioConfig {
        private String endpoint = "";
        private String accessKey = "";
        private String secretKey = "";
        private boolean secure = false;
        private String bucket = "knowledge-base";
        private String imgBucket = "img";
        private String publicUrl = "http://xiafana.asia:9000";
    }

    @Data
    public static class RedisConfig {
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
        private int database = 0;
        private int sessionTtl = 86400;
    }

    @Data
    public static class AgentConfig {
        private String defaultTools = "web_search,web_browser,get_Date";
        private int maxReactIterations = 5;
        private boolean reactThinkingEnabled = true;
        private boolean browserHeadless = true;
        private int browserTimeout = 30000;
    }

    @Data
    public static class ChatConfig {
        private String identificationSet = "identificationSet";
        private String headKey = "identification";
    }

    @Data
    public static class GuessConfig {
        private int listCacheTtl = 10800;
        private int similarityCacheTtl = 86400;
    }
}