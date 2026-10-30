package com.oogata.radiomod.audio;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * 再生するURLの検証。http と https だけを受け付ける。
 * 内部アドレスの拒否は plugin の役目で、mod は自分のPCから取りに行くだけなので見ない。
 */
public final class UrlValidator {

    /** protocol.md の url の上限（UTF-8 のバイト数）。 */
    public static final int MAX_URL_BYTES = 2048;

    private UrlValidator() {
    }

    /** 前後の空白を除いたURLを返す。使えないURLなら reason=1（非対応のURL）の AudioFailure。 */
    public static String validate(String url) throws AudioFailure {
        if (url == null || url.isBlank()) {
            throw AudioFailure.unsupported("URL が空です");
        }
        String trimmed = url.strip();
        if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_URL_BYTES) {
            throw AudioFailure.unsupported("URL が長すぎます (最大 " + MAX_URL_BYTES + " バイト)");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i)) || Character.isISOControl(trimmed.charAt(i))) {
                throw AudioFailure.unsupported("URL に空白や制御文字が含まれています");
            }
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw AudioFailure.unsupported("URL の形式が正しくありません: " + e.getReason());
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw AudioFailure.unsupported("http と https の URL だけ使えます");
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw AudioFailure.unsupported("URL にホスト名がありません");
        }
        return trimmed;
    }
}
