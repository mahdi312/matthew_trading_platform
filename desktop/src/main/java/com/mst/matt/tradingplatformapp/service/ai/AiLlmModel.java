package com.mst.matt.tradingplatformapp.service.ai;

/**
 * A selectable LLM model for the AI News &amp; Insights tab.
 */
public record AiLlmModel(
        String id,
        String providerName,
        String displayName,
        String modelId,
        String baseUrl,
        ApiFormat format,
        String apiKeyProperty
) {
    public enum ApiFormat {
        OPENAI_COMPAT,
        ANTHROPIC
    }

    public String label() {
        return providerName + " · " + displayName;
    }
}
