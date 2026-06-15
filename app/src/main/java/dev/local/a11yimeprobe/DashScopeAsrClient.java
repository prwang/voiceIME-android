package dev.local.a11yimeprobe;

import android.os.CancellationSignal;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Real cloud ASR via Alibaba Cloud DashScope's OpenAI-compatible Chat Completions
 * endpoint (spec section 19). Low-dependency: HttpURLConnection + org.json +
 * android.util.Base64 only — no OkHttp/Retrofit/SDK.
 *
 * POST <endpoint>/chat/completions
 *   Authorization: Bearer <key>
 *   Content-Type: application/json
 *   body: { model, messages:[{role:user, content:[{type:input_audio,
 *           input_audio:{data:"data:audio/wav;base64,..."}}]}], stream:false,
 *           asr_options:{language?, enable_itn} }
 * Text is read from choices[0].message.content. Final-only (stream=false) per
 * spec section 8.4. Audio inlined as base64 data URI (<= 10 MB).
 */
public final class DashScopeAsrClient implements AsrClient {

    @Override
    public AsrResult transcribe(File audioFile, AsrRequest request, CancellationSignal cancel)
            throws Exception {
        if (request.apiKey == null || request.apiKey.isEmpty()) {
            throw new IllegalStateException("ASR_NO_API_KEY");
        }

        byte[] wav = readAllBytes(audioFile);
        String dataUri = "data:audio/wav;base64," + Base64.encodeToString(wav, Base64.NO_WRAP);

        JSONObject inputAudio = new JSONObject().put("data", dataUri);
        JSONObject audioPart = new JSONObject()
                .put("type", "input_audio")
                .put("input_audio", inputAudio);
        JSONArray content = new JSONArray().put(audioPart);
        JSONObject message = new JSONObject().put("role", "user").put("content", content);
        JSONArray messages = new JSONArray().put(message);

        JSONObject asrOptions = new JSONObject();
        if (request.languageHint != null && !request.languageHint.isEmpty()) {
            asrOptions.put("language", request.languageHint);
        }
        asrOptions.put("enable_itn", request.enableItn);

        JSONObject body = new JSONObject()
                .put("model", request.model)
                .put("messages", messages)
                .put("stream", false)
                .put("asr_options", asrOptions);

        String url = joinUrl(request.endpoint, "chat/completions");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(request.connectTimeoutMs);
            conn.setReadTimeout(request.readTimeoutMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + request.apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            if (cancel != null && cancel.isCanceled()) {
                throw new InterruptedIOException("ASR_CANCELLED");
            }

            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = readStream(in);

            if (status != 200) {
                throw new IOException("ASR_HTTP_" + status + " " + truncate(resp, 300));
            }

            JSONObject json = new JSONObject(resp);
            String requestId = json.optString("id", null);
            String text = "";
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject choice0 = choices.optJSONObject(0);
                JSONObject msg = choice0 != null ? choice0.optJSONObject("message") : null;
                if (msg != null) {
                    text = contentToString(msg.opt("content"));
                }
            }
            return new AsrResult(text, false, requestId);
        } finally {
            conn.disconnect();
        }
    }

    /** content is normally a String; tolerate the array-of-parts shape too. */
    private static String contentToString(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof JSONArray) {
            JSONArray arr = (JSONArray) content;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject part = arr.optJSONObject(i);
                if (part != null) {
                    sb.append(part.optString("text", ""));
                }
            }
            return sb.toString();
        }
        return content.toString();
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static String readStream(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String joinUrl(String base, String path) {
        if (base == null) {
            base = "";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + path;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
