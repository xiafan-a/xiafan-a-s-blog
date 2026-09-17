package com.xiafan.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tavily Search 配置（app.tavily.*）。工具 tavily_search 由 {@code BuiltinTools} 注册，
 * 实际调用在 {@code TavilySearchClient}。
 */
@ConfigurationProperties(prefix = "app.tavily")
public class TavilySearchProperties {

    private boolean enabled = true;
    private String apiKey = "";
    private String apiUrl = "https://api.tavily.com";
    private String searchDepth = "basic";
    private int maxResults = 5;
    private String topic = "general";
    private boolean includeAnswer = false;
    private String timeRange = "";
    private int contentMaxLength = 500;
    private int timeoutSeconds = 30;
    private String extractDepth = "basic";
    private String extractFormat = "markdown";
    private int extractMaxContentLength = 20000;
    private int chunksPerSource = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getSearchDepth() {
        return searchDepth;
    }

    public void setSearchDepth(String searchDepth) {
        this.searchDepth = searchDepth;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public boolean isIncludeAnswer() {
        return includeAnswer;
    }

    public void setIncludeAnswer(boolean includeAnswer) {
        this.includeAnswer = includeAnswer;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        this.timeRange = timeRange;
    }

    public int getContentMaxLength() {
        return contentMaxLength;
    }

    public void setContentMaxLength(int contentMaxLength) {
        this.contentMaxLength = contentMaxLength;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getExtractDepth() {
        return extractDepth;
    }

    public void setExtractDepth(String extractDepth) {
        this.extractDepth = extractDepth;
    }

    public String getExtractFormat() {
        return extractFormat;
    }

    public void setExtractFormat(String extractFormat) {
        this.extractFormat = extractFormat;
    }

    public int getExtractMaxContentLength() {
        return extractMaxContentLength;
    }

    public void setExtractMaxContentLength(int extractMaxContentLength) {
        this.extractMaxContentLength = extractMaxContentLength;
    }

    public int getChunksPerSource() {
        return chunksPerSource;
    }

    public void setChunksPerSource(int chunksPerSource) {
        this.chunksPerSource = chunksPerSource;
    }
}
