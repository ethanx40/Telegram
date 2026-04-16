package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * LLM 翻译配置管理器
 *
 * 内置多种 LLM 提供商预设（DeepSeek / OpenAI / Claude / Gemini / 自定义），
 * 用户只需选择提供商并填写 API Key 即可使用，端点和模型名自动填入。
 * 默认提供商为 DeepSeek（性价比最高）。
 */
public class LLMTranslateConfig {

    private static volatile LLMTranslateConfig instance;

    private static final String PREF_NAME = "llm_translate_config";

    // 配置 key 常量
    private static final String KEY_ENABLED = "llm_translate_enabled";
    private static final String KEY_PROVIDER = "llm_provider";
    private static final String KEY_API_KEY = "llm_api_key";
    private static final String KEY_API_ENDPOINT = "llm_api_endpoint";
    private static final String KEY_MODEL_NAME = "llm_model_name";
    private static final String KEY_AUTO_TRANSLATE_INCOMING = "auto_translate_incoming";
    private static final String KEY_AUTO_TRANSLATE_OUTGOING = "auto_translate_outgoing";
    private static final String KEY_MY_LANGUAGE = "my_language";
    private static final String KEY_MAX_TOKENS_PER_REQUEST = "max_tokens_per_request";
    private static final String KEY_BATCH_DELAY_MS = "batch_delay_ms";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";

    private static final int DEFAULT_MAX_TOKENS = 2000;
    private static final int DEFAULT_BATCH_DELAY = 300;
    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a professional translation engine. Your ONLY task is to translate text.\n" +
            "RULES:\n" +
            "- Translate the user's message into {target_language}.\n" +
            "- Output ONLY the translated text. Nothing else.\n" +
            "- Do NOT answer, reply to, or interpret the message.\n" +
            "- Do NOT add explanations, notes, or commentary.\n" +
            "- Preserve the original formatting, emojis, punctuation, and line breaks.\n" +
            "- If the text is already in {target_language}, output it unchanged.";

    // ==================== 提供商预设 ====================

    /** 提供商 ID，同时也是 SharedPreferences 中存的值 */
    public static final String PROVIDER_DEEPSEEK  = "deepseek";
    public static final String PROVIDER_OPENAI     = "openai";
    public static final String PROVIDER_CLAUDE     = "claude";
    public static final String PROVIDER_GEMINI     = "gemini";
    public static final String PROVIDER_GROQ       = "groq";
    public static final String PROVIDER_MOONSHOT   = "moonshot";
    public static final String PROVIDER_ZHIPU      = "zhipu";
    public static final String PROVIDER_CUSTOM     = "custom";

    public static final String DEFAULT_PROVIDER = PROVIDER_DEEPSEEK;

    /**
     * 预置提供商信息
     */
    public static class ProviderPreset {
        public final String id;
        public final String displayName;
        public final String endpoint;
        public final String defaultModel;
        public final String[] availableModels;

        public ProviderPreset(String id, String displayName, String endpoint, String defaultModel, String[] availableModels) {
            this.id = id;
            this.displayName = displayName;
            this.endpoint = endpoint;
            this.defaultModel = defaultModel;
            this.availableModels = availableModels;
        }
    }

    /** 所有预置提供商，顺序即为 UI 展示顺序 */
    public static final ProviderPreset[] PROVIDERS = {
        new ProviderPreset(
            PROVIDER_DEEPSEEK,
            "DeepSeek",
            "https://api.deepseek.com/chat/completions",
            "deepseek-chat",
            new String[]{"deepseek-chat", "deepseek-reasoner"}
        ),
        new ProviderPreset(
            PROVIDER_OPENAI,
            "OpenAI",
            "https://api.openai.com/v1/chat/completions",
            "gpt-4o-mini",
            new String[]{"gpt-4o-mini", "gpt-4o", "gpt-4.1-nano", "gpt-4.1-mini", "gpt-4.1", "o4-mini"}
        ),
        new ProviderPreset(
            PROVIDER_CLAUDE,
            "Claude (Anthropic)",
            "https://api.anthropic.com/v1/messages",
            "claude-sonnet-4-20250514",
            new String[]{"claude-sonnet-4-20250514", "claude-haiku-4-20250514", "claude-3-5-haiku-20241022"}
        ),
        new ProviderPreset(
            PROVIDER_GEMINI,
            "Gemini (Google)",
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "gemini-2.0-flash",
            new String[]{"gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-2.5-flash"}
        ),
        new ProviderPreset(
            PROVIDER_GROQ,
            "Groq",
            "https://api.groq.com/openai/v1/chat/completions",
            "llama-3.3-70b-versatile",
            new String[]{"llama-3.3-70b-versatile", "llama-3.1-8b-instant", "gemma2-9b-it", "mixtral-8x7b-32768"}
        ),
        new ProviderPreset(
            PROVIDER_MOONSHOT,
            "Moonshot (月之暗面)",
            "https://api.moonshot.cn/v1/chat/completions",
            "moonshot-v1-8k",
            new String[]{"moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"}
        ),
        new ProviderPreset(
            PROVIDER_ZHIPU,
            "Zhipu (智谱)",
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            "glm-4-flash",
            new String[]{"glm-4-flash", "glm-4-air", "glm-4-airx", "glm-4"}
        ),
        new ProviderPreset(
            PROVIDER_CUSTOM,
            "Custom (OpenAI-compatible)",
            "",
            "",
            new String[]{}
        ),
    };

    private SharedPreferences prefs;

    private LLMTranslateConfig() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, 0);
    }

    public static LLMTranslateConfig getInstance() {
        if (instance == null) {
            synchronized (LLMTranslateConfig.class) {
                if (instance == null) {
                    instance = new LLMTranslateConfig();
                }
            }
        }
        return instance;
    }

    // ==================== 提供商 ====================

    public String getProviderId() {
        return prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER);
    }

    public void setProviderId(String providerId) {
        prefs.edit().putString(KEY_PROVIDER, providerId).apply();
    }

    /** 获取当前选中的提供商预设，找不到则返回 Custom */
    public ProviderPreset getCurrentProvider() {
        String id = getProviderId();
        for (ProviderPreset p : PROVIDERS) {
            if (p.id.equals(id)) return p;
        }
        return PROVIDERS[PROVIDERS.length - 1]; // Custom
    }

    /**
     * 切换提供商：自动填入端点和默认模型，但保留用户已填的 API Key
     */
    public void switchProvider(String providerId) {
        setProviderId(providerId);
        ProviderPreset preset = getCurrentProvider();
        if (!PROVIDER_CUSTOM.equals(providerId)) {
            setApiEndpoint(preset.endpoint);
            setModelName(preset.defaultModel);
        }
    }

    // ==================== 启用 ====================

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false) && !TextUtils.isEmpty(getApiKey());
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    // ==================== API Key ====================

    public String getApiKey() {
        // 每个提供商独立存储 key，方便切换不丢失
        String providerKey = prefs.getString(KEY_API_KEY + "_" + getProviderId(), null);
        if (providerKey != null) return providerKey;
        // 兼容旧版单一 key
        return prefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String apiKey) {
        prefs.edit()
            .putString(KEY_API_KEY + "_" + getProviderId(), apiKey)
            .putString(KEY_API_KEY, apiKey)
            .apply();
    }

    // ==================== API 端点 ====================

    public String getApiEndpoint() {
        String endpoint = prefs.getString(KEY_API_ENDPOINT, null);
        if (endpoint != null) return endpoint;
        return getCurrentProvider().endpoint;
    }

    public void setApiEndpoint(String endpoint) {
        prefs.edit().putString(KEY_API_ENDPOINT, endpoint).apply();
    }

    // ==================== 模型名 ====================

    public String getModelName() {
        String model = prefs.getString(KEY_MODEL_NAME, null);
        if (model != null) return model;
        return getCurrentProvider().defaultModel;
    }

    public void setModelName(String modelName) {
        prefs.edit().putString(KEY_MODEL_NAME, modelName).apply();
    }

    // ==================== 翻译开关 ====================

    public boolean isAutoTranslateIncoming() {
        return prefs.getBoolean(KEY_AUTO_TRANSLATE_INCOMING, true);
    }

    public void setAutoTranslateIncoming(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE_INCOMING, enabled).apply();
    }

    public boolean isAutoTranslateOutgoing() {
        return prefs.getBoolean(KEY_AUTO_TRANSLATE_OUTGOING, true);
    }

    public void setAutoTranslateOutgoing(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE_OUTGOING, enabled).apply();
    }

    // ==================== 语言 ====================

    public String getMyLanguage() {
        String lang = prefs.getString(KEY_MY_LANGUAGE, null);
        if (lang == null) {
            lang = TranslateController.currentLanguage();
        }
        return lang;
    }

    public void setMyLanguage(String language) {
        prefs.edit().putString(KEY_MY_LANGUAGE, language).apply();
    }

    public String getDialogTargetLanguage(long dialogId) {
        return prefs.getString("dialog_target_lang_" + dialogId, null);
    }

    public void setDialogTargetLanguage(long dialogId, String language) {
        prefs.edit().putString("dialog_target_lang_" + dialogId, language).apply();
    }

    // ==================== 对话级开关 ====================

    public boolean isDialogLLMTranslateEnabled(long dialogId) {
        return prefs.getBoolean("dialog_llm_enabled_" + dialogId, false);
    }

    public void setDialogLLMTranslateEnabled(long dialogId, boolean enabled) {
        prefs.edit().putBoolean("dialog_llm_enabled_" + dialogId, enabled).apply();
    }

    /** 对话级：是否翻译收到的消息（默认跟随全局） */
    public boolean isDialogIncomingEnabled(long dialogId) {
        String key = "dialog_incoming_" + dialogId;
        if (!prefs.contains(key)) return isAutoTranslateIncoming();
        return prefs.getBoolean(key, true);
    }

    public void setDialogIncomingEnabled(long dialogId, boolean enabled) {
        prefs.edit().putBoolean("dialog_incoming_" + dialogId, enabled).apply();
    }

    /** 对话级：是否翻译发出的消息（默认跟随全局） */
    public boolean isDialogOutgoingEnabled(long dialogId) {
        String key = "dialog_outgoing_" + dialogId;
        if (!prefs.contains(key)) return isAutoTranslateOutgoing();
        return prefs.getBoolean(key, true);
    }

    public void setDialogOutgoingEnabled(long dialogId, boolean enabled) {
        prefs.edit().putBoolean("dialog_outgoing_" + dialogId, enabled).apply();
    }

    /** 对话级：我的语言（默认跟随全局） */
    public String getDialogMyLanguage(long dialogId) {
        String lang = prefs.getString("dialog_my_lang_" + dialogId, null);
        return lang != null ? lang : getMyLanguage();
    }

    public void setDialogMyLanguage(long dialogId, String language) {
        prefs.edit().putString("dialog_my_lang_" + dialogId, language).apply();
    }

    /** 对话级：对方语言 */
    public String getDialogPeerLanguage(long dialogId) {
        return getDialogTargetLanguage(dialogId);
    }

    public void setDialogPeerLanguage(long dialogId, String language) {
        setDialogTargetLanguage(dialogId, language);
    }

    // ==================== 高级 ====================

    public int getMaxTokensPerRequest() {
        return prefs.getInt(KEY_MAX_TOKENS_PER_REQUEST, DEFAULT_MAX_TOKENS);
    }

    public void setMaxTokensPerRequest(int maxTokens) {
        prefs.edit().putInt(KEY_MAX_TOKENS_PER_REQUEST, maxTokens).apply();
    }

    public int getBatchDelayMs() {
        return prefs.getInt(KEY_BATCH_DELAY_MS, DEFAULT_BATCH_DELAY);
    }

    public void setBatchDelayMs(int delayMs) {
        prefs.edit().putInt(KEY_BATCH_DELAY_MS, delayMs).apply();
    }

    private static final String OLD_DEFAULT_PROMPT =
            "You are a translator. Translate the following message to {target_language}. " +
            "Only output the translation, nothing else. Preserve formatting, emojis, and line breaks.";

    public String getSystemPrompt() {
        String saved = prefs.getString(KEY_SYSTEM_PROMPT, null);
        // 自动升级旧版默认 prompt
        if (saved == null || saved.equals(OLD_DEFAULT_PROMPT)) {
            return DEFAULT_SYSTEM_PROMPT;
        }
        return saved;
    }

    public void setSystemPrompt(String prompt) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply();
    }

    public String buildSystemPrompt(String targetLanguage) {
        return getSystemPrompt().replace("{target_language}", targetLanguage);
    }

    // ==================== 是否已配置完成 ====================

    public boolean isConfigured() {
        return !TextUtils.isEmpty(getApiKey()) && !TextUtils.isEmpty(getApiEndpoint());
    }

    /**
     * 是否为 Claude 提供商（Claude 的 API 格式略有不同）
     */
    public boolean isClaude() {
        return PROVIDER_CLAUDE.equals(getProviderId());
    }
}
