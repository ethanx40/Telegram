package org.telegram.messenger;

import android.text.TextUtils;
import android.util.LongSparseArray;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 翻译控制器
 *
 * 语言探测流程：
 * 1. 对话开启 LLM 翻译时，从当前可见的对方消息中采样，用 ML Kit 检测对方语言
 * 2. 检测结果持久化到 LLMTranslateConfig.setDialogTargetLanguage()
 * 3. 如果对方语言和我的语言一致，跳过所有翻译
 * 4. 接收消息翻译成我的语言，发送消息翻译成对方语言
 */
public class LLMTranslateController {

    private static final String TAG = "LLMTranslate";
    private static final int MIN_TRANSLATABLE_LENGTH = 3;
    private static final int DETECT_SAMPLE_COUNT = 5; // 采样消息数

    private final Set<Long> translatingKeys = ConcurrentHashMap.newKeySet();
    private final Set<Long> detectingDialogs = ConcurrentHashMap.newKeySet();
    private final int currentAccount;

    public LLMTranslateController(int account) {
        this.currentAccount = account;
    }

    // ==================== 对方语言探测 ====================

    /**
     * 从对方消息列表中采样检测对方使用的语言，结果持久化。
     * 在对话开启 LLM 翻译时调用一次。
     *
     * @param dialogId 对话 ID
     * @param peerMessages 对方发送的消息列表（从当前可见消息中筛选）
     * @param onDetected 检测完成回调，参数为检测到的语言代码（可能为 null）
     */
    public void detectPeerLanguage(long dialogId, List<MessageObject> peerMessages,
                                   Utilities.Callback<String> onDetected) {
        if (!detectingDialogs.add(dialogId)) {
            // 正在检测中
            if (onDetected != null) onDetected.run(null);
            return;
        }

        // 已有持久化的对方语言，直接返回
        String saved = LLMTranslateConfig.getInstance().getDialogTargetLanguage(dialogId);
        if (!TextUtils.isEmpty(saved)) {
            detectingDialogs.remove(dialogId);
            if (onDetected != null) onDetected.run(saved);
            return;
        }

        // 收集对方消息文本采样
        StringBuilder sampleText = new StringBuilder();
        int sampled = 0;
        for (MessageObject msg : peerMessages) {
            if (msg.isOutOwner()) continue;
            String text = getTranslatableText(msg);
            if (!TextUtils.isEmpty(text) && text.length() >= MIN_TRANSLATABLE_LENGTH) {
                if (sampleText.length() > 0) sampleText.append(" ");
                sampleText.append(text);
                sampled++;
                if (sampled >= DETECT_SAMPLE_COUNT) break;
            }
        }

        if (sampleText.length() == 0) {
            detectingDialogs.remove(dialogId);
            if (onDetected != null) onDetected.run(null);
            return;
        }

        // 用 ML Kit 本地检测
        LanguageDetector.detectLanguage(sampleText.toString(), detectedLang -> {
            detectingDialogs.remove(dialogId);
            if (detectedLang != null && !"und".equals(detectedLang)) {
                String normalized = normalizeLanguage(detectedLang);
                // 持久化
                LLMTranslateConfig.getInstance().setDialogTargetLanguage(dialogId, normalized);
                FileLog.d(TAG + ": detected peer language for dialog " + dialogId + " = " + normalized);
                if (onDetected != null) onDetected.run(normalized);
            } else {
                if (onDetected != null) onDetected.run(null);
            }
        }, e -> {
            detectingDialogs.remove(dialogId);
            FileLog.e(TAG + ": language detection failed", e);
            if (onDetected != null) onDetected.run(null);
        });
    }

    /**
     * 获取对方语言（已持久化的优先，否则返回 null）
     */
    public String getPeerLanguage(long dialogId) {
        return LLMTranslateConfig.getInstance().getDialogTargetLanguage(dialogId);
    }

    /**
     * 判断对方语言是否和我一样（一样则不需要翻译）
     */
    public boolean isPeerLanguageSameAsMine(long dialogId) {
        String peerLang = getPeerLanguage(dialogId);
        if (TextUtils.isEmpty(peerLang)) return false;
        String myLang = LLMTranslateConfig.getInstance().getDialogMyLanguage(dialogId);
        return normalizeLanguage(peerLang).equals(normalizeLanguage(myLang));
    }

    // ==================== 接收消息翻译 ====================

    public void checkAndTranslateIncoming(MessageObject msg) {
        if (!shouldTranslate(msg, false)) return;

        long dialogId = msg.getDialogId();

        // 对方语言和我一样，跳过
        if (isPeerLanguageSameAsMine(dialogId)) return;

        String myLang = LLMTranslateConfig.getInstance().getDialogMyLanguage(dialogId);

        // 已有缓存的翻译且语言一致
        if (msg.messageOwner.translatedText != null &&
                TextUtils.equals(msg.messageOwner.translatedToLanguage, myLang)) {
            applyTranslationToUI(msg);
            return;
        }

        String text = getTranslatableText(msg);
        if (TextUtils.isEmpty(text) || text.length() < MIN_TRANSLATABLE_LENGTH) return;

        long key = makeKey(dialogId, msg.getId());
        if (!translatingKeys.add(key)) return;

        // 顺便更新对方语言检测（如果还没有的话）
        String peerLang = getPeerLanguage(dialogId);
        if (TextUtils.isEmpty(peerLang)) {
            LanguageDetector.detectLanguage(text, detectedLang -> {
                if (detectedLang != null && !"und".equals(detectedLang)) {
                    String norm = normalizeLanguage(detectedLang);
                    LLMTranslateConfig.getInstance().setDialogTargetLanguage(dialogId, norm);
                    // 如果检测到和自己语言一样，跳过翻译
                    if (norm.equals(normalizeLanguage(myLang))) {
                        translatingKeys.remove(key);
                        return;
                    }
                }
                doTranslate(msg, text, myLang, key);
            }, e -> doTranslate(msg, text, myLang, key));
        } else {
            doTranslate(msg, text, myLang, key);
        }
    }

    // ==================== 发送消息翻译 ====================

    public void translateOutgoingSentMessage(MessageObject msg, long dialogId) {
        if (!shouldTranslate(msg, true)) return;

        // 对方语言和我一样，跳过
        if (isPeerLanguageSameAsMine(dialogId)) return;

        String text = getTranslatableText(msg);
        if (TextUtils.isEmpty(text) || text.length() < MIN_TRANSLATABLE_LENGTH) return;

        long key = makeKey(dialogId, msg.getId());
        if (!translatingKeys.add(key)) return;

        // 目标语言 = 对方语言
        String targetLang = getPeerLanguage(dialogId);
        if (TextUtils.isEmpty(targetLang)) {
            // 对方语言未知，暂不翻译（等探测完成后新消息再翻译）
            translatingKeys.remove(key);
            return;
        }

        String myLang = LLMTranslateConfig.getInstance().getDialogMyLanguage(dialogId);
        if (normalizeLanguage(targetLang).equals(normalizeLanguage(myLang))) {
            translatingKeys.remove(key);
            return;
        }

        doTranslate(msg, text, targetLang, key);
    }

    // ==================== 核心翻译 ====================

    private void doTranslate(MessageObject msg, String text, String targetLang, long key) {
        Utilities.globalQueue.postRunnable(() -> {
            String result = callLLMSync(text, targetLang);
            translatingKeys.remove(key);

            if (result != null && !result.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.TL_textWithEntities translated = new TLRPC.TL_textWithEntities();
                    translated.text = result;
                    translated.entities = new ArrayList<>();

                    msg.messageOwner.translatedText = translated;
                    msg.messageOwner.translatedToLanguage = targetLang;

                    MessagesStorage.getInstance(currentAccount)
                            .updateMessageCustomParams(msg.getDialogId(), msg.messageOwner);

                    applyTranslationToUI(msg);
                });
            }
        });
    }

    private void applyTranslationToUI(MessageObject msg) {
        if (msg.messageOwner.translatedText == null) return;

        msg.translated = true;
        msg.summarized = false;

        String original = msg.messageOwner.message != null ? msg.messageOwner.message : "";
        String translatedStr = msg.messageOwner.translatedText.text != null ? msg.messageOwner.translatedText.text : "";
        String combined = original + "\n\n" + translatedStr;

        msg.applyNewText(combined);
        msg.generateCaption();

        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.messageTranslated, msg, false, true);
    }

    // ==================== LLM API ====================

    /**
     * 同步翻译文本（在后台线程调用）。公开给 ChatActivityEnterView 使用。
     */
    public String callTranslateSync(String text, String targetLanguage) {
        return callLLMSync(text, targetLanguage);
    }

    /**
     * 公开的语言代码标准化方法
     */
    public static String normLang(String lang) {
        if (lang == null) return "";
        lang = lang.toLowerCase().trim();
        if (lang.startsWith("zh")) return "zh";
        if ("nb".equals(lang) || "nn".equals(lang)) return "no";
        int idx = lang.indexOf('_');
        if (idx > 0) lang = lang.substring(0, idx);
        idx = lang.indexOf('-');
        if (idx > 0) lang = lang.substring(0, idx);
        return lang;
    }

    private String callLLMSync(String text, String targetLanguage) {
        try {
            LLMTranslateConfig config = LLMTranslateConfig.getInstance();
            String langName = getLanguageDisplayName(targetLanguage);

            JSONObject body = new JSONObject();
            body.put("model", config.getModelName());
            body.put("max_tokens", config.getMaxTokensPerRequest());
            body.put("temperature", 0.3);

            JSONArray messages = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", config.buildSystemPrompt(langName));
            messages.put(sys);
            JSONObject usr = new JSONObject();
            usr.put("role", "user");
            usr.put("content", text);
            messages.put(usr);
            body.put("messages", messages);

            return executeHttp(config.getApiEndpoint(), config.getApiKey(),
                    config.isClaude(), body.toString());
        } catch (Exception e) {
            FileLog.e(TAG, e);
            return null;
        }
    }

    private String executeHttp(String endpoint, String apiKey, boolean isClaude, String body) {
        HttpURLConnection conn = null;
        try {
            if (isClaude) body = toClaudeFormat(body);

            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);

            if (isClaude) {
                conn.setRequestProperty("x-api-key", apiKey);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
            } else {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                return parseResponse(readStream(conn.getInputStream()));
            } else {
                String err = readStream(conn.getErrorStream());
                FileLog.e(TAG + ": HTTP " + code + " - " + err);
                return null;
            }
        } catch (Exception e) {
            FileLog.e(TAG, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(java.io.InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private String parseResponse(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("choices")) {
                return obj.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content").trim();
            }
            if (obj.has("content")) {
                JSONArray arr = obj.getJSONArray("content");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject b = arr.getJSONObject(i);
                    if ("text".equals(b.optString("type")))
                        sb.append(b.getString("text"));
                }
                return sb.toString().trim();
            }
        } catch (Exception e) {
            FileLog.e(TAG, e);
        }
        return null;
    }

    private String toClaudeFormat(String openAiBody) {
        try {
            JSONObject src = new JSONObject(openAiBody);
            JSONObject dst = new JSONObject();
            dst.put("model", src.optString("model"));
            dst.put("max_tokens", src.optInt("max_tokens", 2000));
            JSONArray srcMsgs = src.getJSONArray("messages");
            JSONArray dstMsgs = new JSONArray();
            for (int i = 0; i < srcMsgs.length(); i++) {
                JSONObject m = srcMsgs.getJSONObject(i);
                if ("system".equals(m.getString("role")))
                    dst.put("system", m.getString("content"));
                else
                    dstMsgs.put(m);
            }
            dst.put("messages", dstMsgs);
            return dst.toString();
        } catch (Exception e) {
            return openAiBody;
        }
    }

    // ==================== 判断逻辑 ====================

    private boolean shouldTranslate(MessageObject msg, boolean isOutgoing) {
        if (msg == null || msg.messageOwner == null) return false;

        LLMTranslateConfig config = LLMTranslateConfig.getInstance();
        if (!config.isEnabled()) return false;

        long dialogId = msg.getDialogId();
        if (!config.isDialogLLMTranslateEnabled(dialogId)) return false;

        // 使用对话级开关
        if (isOutgoing) {
            if (!config.isDialogOutgoingEnabled(dialogId)) return false;
            if (!msg.isOutOwner()) return false;
        } else {
            if (!config.isDialogIncomingEnabled(dialogId)) return false;
            if (msg.isOutOwner()) return false;
        }

        int type = msg.type;
        if (type != MessageObject.TYPE_TEXT &&
                type != MessageObject.TYPE_PHOTO &&
                type != MessageObject.TYPE_VIDEO &&
                type != MessageObject.TYPE_FILE) {
            return false;
        }

        if (msg.isRestrictedMessage || msg.isSponsored()) return false;
        if (DialogObject.isEncryptedDialog(dialogId)) return false;
        return true;
    }

    private String getTranslatableText(MessageObject msg) {
        if (msg.messageOwner == null) return null;
        String text = msg.messageOwner.message;
        if (TextUtils.isEmpty(text) && msg.caption != null) {
            text = msg.caption.toString();
        }
        return text;
    }

    // ==================== 工具方法 ====================

    private long makeKey(long dialogId, int msgId) {
        return (dialogId * 100003L) ^ (msgId & 0xFFFFFFFFL);
    }

    private String normalizeLanguage(String lang) {
        return normLang(lang);
    }

    private String getLanguageDisplayName(String code) {
        if (code == null) return "English";
        switch (code.toLowerCase()) {
            case "zh": case "zh-cn": case "zh-hans": return "Simplified Chinese";
            case "zh-tw": case "zh-hant": return "Traditional Chinese";
            case "en": return "English"; case "ja": return "Japanese";
            case "ko": return "Korean";  case "fr": return "French";
            case "de": return "German";  case "es": return "Spanish";
            case "pt": return "Portuguese"; case "ru": return "Russian";
            case "ar": return "Arabic";  case "hi": return "Hindi";
            case "it": return "Italian"; case "vi": return "Vietnamese";
            case "th": return "Thai";    case "id": return "Indonesian";
            case "tr": return "Turkish"; case "uk": return "Ukrainian";
            case "pl": return "Polish";  case "nl": return "Dutch";
            case "sv": return "Swedish"; case "no": case "nb": return "Norwegian";
            default: return code;
        }
    }

    public void clearDialogCache(long dialogId) {
        LLMTranslateConfig.getInstance().setDialogTargetLanguage(dialogId, null);
    }

    public void cleanup() {
        translatingKeys.clear();
        detectingDialogs.clear();
    }
}
