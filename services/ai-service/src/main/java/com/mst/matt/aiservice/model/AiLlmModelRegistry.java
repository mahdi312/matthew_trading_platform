package com.mst.matt.aiservice.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Registry of LLM models available for AI analysis.
 * Ported from desktop {@code service.ai.AiLlmModelRegistry}.
 * <p>
 * Property keys follow the {@code app.api.*} namespace defined in
 * {@code application.yml}.  All keys default to empty string so the
 * service starts cleanly with zero API keys configured.
 */
@Component
public class AiLlmModelRegistry {

    private static final List<AiLlmModel> ALL_MODELS = List.of(
            // OpenAI
            model("openai:gpt-4o-mini",  "OpenAI", "GPT-4o Mini",   "gpt-4o-mini",
                    "https://api.openai.com/v1",   AiLlmModel.ApiFormat.OPENAI_COMPAT, "openai"),
            model("openai:gpt-4o",       "OpenAI", "GPT-4o",        "gpt-4o",
                    "https://api.openai.com/v1",   AiLlmModel.ApiFormat.OPENAI_COMPAT, "openai"),
            model("openai:gpt-4-turbo",  "OpenAI", "GPT-4 Turbo",   "gpt-4-turbo",
                    "https://api.openai.com/v1",   AiLlmModel.ApiFormat.OPENAI_COMPAT, "openai"),

            // Groq
            model("groq:llama-3.3-70b",  "Groq", "Llama 3.3 70B",        "llama-3.3-70b-versatile",
                    "https://api.groq.com/openai/v1", AiLlmModel.ApiFormat.OPENAI_COMPAT, "groq"),
            model("groq:llama-3.1-8b",   "Groq", "Llama 3.1 8B Instant", "llama-3.1-8b-instant",
                    "https://api.groq.com/openai/v1", AiLlmModel.ApiFormat.OPENAI_COMPAT, "groq"),
            model("groq:gemma2-9b",      "Groq", "Gemma 2 9B",           "gemma2-9b-it",
                    "https://api.groq.com/openai/v1", AiLlmModel.ApiFormat.OPENAI_COMPAT, "groq"),

            // Cerebras
            model("cerebras:gpt-oss-120b", "Cerebras", "GPT OSS 120B", "gpt-oss-120b",
                    "https://api.cerebras.ai/v1", AiLlmModel.ApiFormat.OPENAI_COMPAT, "cerebras"),
            model("cerebras:glm-4.7",      "Cerebras", "GLM 4.7",      "zai-glm-4.7",
                    "https://api.cerebras.ai/v1", AiLlmModel.ApiFormat.OPENAI_COMPAT, "cerebras"),
            model("cerebras:gemma-4-31b",  "Cerebras", "Gemma 4 31B",  "gemma-4-31b",
                    "https://api.cerebras.ai/v1", AiLlmModel.ApiFormat.OPENAI_COMPAT, "cerebras"),

            // DeepSeek
            model("deepseek:chat",      "DeepSeek", "DeepSeek Chat",     "deepseek-chat",
                    "https://api.deepseek.com", AiLlmModel.ApiFormat.OPENAI_COMPAT, "deepseek"),
            model("deepseek:reasoner",  "DeepSeek", "DeepSeek Reasoner", "deepseek-reasoner",
                    "https://api.deepseek.com", AiLlmModel.ApiFormat.OPENAI_COMPAT, "deepseek"),

            // Anthropic Claude
            model("anthropic:claude-sonnet", "Anthropic", "Claude Sonnet 4", "claude-sonnet-4-20250514",
                    "https://api.anthropic.com/v1", AiLlmModel.ApiFormat.ANTHROPIC, "anthropic"),
            model("anthropic:claude-opus",   "Anthropic", "Claude Opus 4",   "claude-opus-4-20250514",
                    "https://api.anthropic.com/v1", AiLlmModel.ApiFormat.ANTHROPIC, "anthropic"),
            model("anthropic:claude-haiku",  "Anthropic", "Claude Haiku 3.5","claude-3-5-haiku-20241022",
                    "https://api.anthropic.com/v1", AiLlmModel.ApiFormat.ANTHROPIC, "anthropic")
    );

    // ── @Value bindings (all default to empty so startup never fails) ─────────

    @Value("${app.api.openai.key:}")    private String openAiKey;
    @Value("${app.api.groq.key:}")      private String groqKey;
    @Value("${app.api.cerebras.key:}")  private String cerebrasKey;
    @Value("${app.api.deepseek.key:}")  private String deepSeekKey;
    @Value("${app.api.anthropic.key:}") private String anthropicKey;

    @Value("${app.api.openai.model:gpt-4o-mini}") private String defaultOpenAiModel;
    @Value("${app.api.groq.model:}")               private String groqModelOverride;
    @Value("${app.api.cerebras.model:}")           private String cerebrasModelOverride;
    @Value("${app.api.deepseek.model:}")           private String deepSeekModelOverride;
    @Value("${app.api.anthropic.model:}")          private String anthropicModelOverride;

    // ── Public API ────────────────────────────────────────────────────────────

    public List<AiLlmModel> allModels() { return ALL_MODELS; }

    public List<AiLlmModel> configuredModels() {
        return ALL_MODELS.stream().filter(this::hasApiKey).toList();
    }

    public Optional<AiLlmModel> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return ALL_MODELS.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    public Optional<AiLlmModel> defaultModel() {
        // Prefer OpenAI with the configured default model id
        Optional<AiLlmModel> configured = configuredModels().stream()
                .filter(m -> "openai".equals(m.apiKeyProperty()))
                .filter(m -> m.modelId().equals(defaultOpenAiModel))
                .findFirst()
                .map(this::effectiveModel);
        if (configured.isPresent()) return configured;

        return configuredModels().stream()
                .findFirst()
                .map(this::effectiveModel);
    }

    /** Applies optional {@code app.api.<provider>.model} override. */
    public AiLlmModel effectiveModel(AiLlmModel model) {
        String override = normalizeOverride(model.apiKeyProperty(),
                providerModelOverride(model.apiKeyProperty()));
        if (override == null || override.isBlank() || override.equals(model.modelId())) {
            return model;
        }
        return new AiLlmModel(model.id(), model.providerName(), model.displayName(),
                override.trim(), model.baseUrl(), model.format(), model.apiKeyProperty());
    }

    public boolean hasApiKey(AiLlmModel model) {
        return !resolveApiKey(model).isBlank();
    }

    public String resolveApiKey(AiLlmModel model) {
        return switch (model.apiKeyProperty()) {
            case "openai"    -> openAiKey    != null ? openAiKey    : "";
            case "groq"      -> groqKey      != null ? groqKey      : "";
            case "cerebras"  -> cerebrasKey  != null ? cerebrasKey  : "";
            case "deepseek"  -> deepSeekKey  != null ? deepSeekKey  : "";
            case "anthropic" -> anthropicKey != null ? anthropicKey : "";
            default          -> "";
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String providerModelOverride(String prop) {
        return switch (prop) {
            case "groq"      -> groqModelOverride;
            case "cerebras"  -> cerebrasModelOverride;
            case "deepseek"  -> deepSeekModelOverride;
            case "anthropic" -> anthropicModelOverride;
            default          -> "";
        };
    }

    private static String normalizeOverride(String provider, String override) {
        if (override == null || override.isBlank()) return override;
        if ("groq".equals(provider)     && "llama-3.3-70b".equals(override)) return "llama-3.3-70b-versatile";
        if ("cerebras".equals(provider) && "llama-3.3-70b".equals(override)) return "gpt-oss-120b";
        return override;
    }

    private static AiLlmModel model(String id, String provider, String display, String modelId,
                                    String baseUrl, AiLlmModel.ApiFormat format, String keyProp) {
        return new AiLlmModel(id, provider, display, modelId, baseUrl, format, keyProp);
    }
}
