package dev.local.a11yimeprobe;

/**
 * ASR request parameters (spec section 19.1). The apiKey is carried here so the
 * client stays stateless; endpoint is the OpenAI-compatible base URL.
 */
public final class AsrRequest {

    public final String apiKey;
    public final String endpoint;     // base URL, e.g. https://dashscope-intl.aliyuncs.com/compatible-mode/v1
    public final String model;
    public final String languageHint; // "" = auto-detect
    public final boolean enableItn;
    public final int connectTimeoutMs;
    public final int readTimeoutMs;

    public AsrRequest(String apiKey, String endpoint, String model, String languageHint,
                      boolean enableItn, int connectTimeoutMs, int readTimeoutMs) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
        this.languageHint = languageHint;
        this.enableItn = enableItn;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public static AsrRequest fromConfig(ProbeConfig config) {
        return new AsrRequest(
                config.getAsrApiKey(),
                config.getAsrBaseUrl(),
                config.getAsrModel(),
                config.getAsrLanguage(),
                config.enableItn(),
                10000,
                60000);
    }
}
