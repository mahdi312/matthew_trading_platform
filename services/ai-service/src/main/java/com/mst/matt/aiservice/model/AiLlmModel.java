package com.mst.matt.aiservice.model;

/**
 * Describes a single LLM model endpoint available for AI analysis.
 * Ported from desktop {@code service.ai.AiLlmModel}.
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
