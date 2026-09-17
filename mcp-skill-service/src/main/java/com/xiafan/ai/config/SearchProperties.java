package com.xiafan.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 联网搜索路由配置（app.search.*）。
 *
 * <p>{@code web_search} 默认走 Tavily（{@code app.tavily.*}），原本由 npx 启动的
 * {@code bing-cn-mcp}（{@code McpWebSearchClient}）降级为备选：只有当 Tavily 里出现 Key 缺失、
 * 超时、限流或上游报错时才会自动切过去。把 {@code provider} 设为 {@code bing} 可以反过来，
 * 让 bing-cn-mcp 优先、Tavily 备选；{@code fallback-enabled=false} 则关闭备选，只打主源。</p>
 */
@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {

    /** 主搜索源：tavily（默认）或 bing。 */
    private String provider = "tavily";

    /** 主源失败或未配置时是否自动切到备选源。 */
    private boolean fallbackEnabled = true;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }
}
