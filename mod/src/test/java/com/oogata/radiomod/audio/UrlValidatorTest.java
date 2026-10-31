package com.oogata.radiomod.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oogata.radiomod.net.Messages;
import org.junit.jupiter.api.Test;

class UrlValidatorTest {

    @Test
    void acceptsHttpAndHttps() throws Exception {
        assertEquals("http://example.com/a.mp3", UrlValidator.validate("http://example.com/a.mp3"));
        assertEquals("https://example.com:8000/stream?x=1", UrlValidator.validate("  https://example.com:8000/stream?x=1 "));
        assertEquals("HTTPS://EXAMPLE.COM/a", UrlValidator.validate("HTTPS://EXAMPLE.COM/a"));
    }

    @Test
    void acceptsPercentEscapedUrls() throws Exception {
        String url = "https://filedn.com/lM7rLs0xJ8D4NY4k6reRFI4/0102%20-%20%E3%81%BB%E3%81%8A%E3%81%9A%E3%81%8D%20-%20New%20Classic%20ORIGINAL%20SOUNDTRACK.mp3";
        assertEquals(url, UrlValidator.validate(url));
        assertEquals("https://hstorage.jp/dl/03EbrXHWiFn9.mp3", UrlValidator.validate("https://hstorage.jp/dl/03EbrXHWiFn9.mp3"));
    }

    @Test
    void rejectsUrlCutOffInsideEscape() {
        AudioFailure f = assertThrows(AudioFailure.class,
                () -> UrlValidator.validate("https://example.com/a%20-%20%E3%81%BB%E3%81%8A%E3%81%9A%"));
        assertEquals(Messages.Error.UNSUPPORTED_URL, f.reason());
        assertTrue(f.detail().contains("256"));
    }

    @Test
    void rejectsOtherSchemesAsUnsupported() {
        for (String url : new String[] {"ftp://example.com/a.mp3", "file:///C:/a.mp3", "test://hello", "example.com/a.mp3",
                "javascript:alert(1)"}) {
            AudioFailure f = assertThrows(AudioFailure.class, () -> UrlValidator.validate(url), url);
            assertEquals(Messages.Error.UNSUPPORTED_URL, f.reason(), url);
        }
    }

    @Test
    void rejectsEmptyMalformedAndHostless() {
        for (String url : new String[] {null, "", "   ", "http://", "http:///a.mp3", "http://exa mple.com/a", "http://a b/\n"}) {
            AudioFailure f = assertThrows(AudioFailure.class, () -> UrlValidator.validate(url), String.valueOf(url));
            assertEquals(Messages.Error.UNSUPPORTED_URL, f.reason());
        }
    }

    @Test
    void rejectsTooLongUrl() throws Exception {
        String base = "http://example.com/";
        String ok = base + "a".repeat(UrlValidator.MAX_URL_BYTES - base.length());
        assertEquals(ok, UrlValidator.validate(ok));
        assertThrows(AudioFailure.class, () -> UrlValidator.validate(ok + "a"));
        // 上限はバイト数。日本語は1文字3バイトで数える。
        assertThrows(AudioFailure.class, () -> UrlValidator.validate(base + "あ".repeat(700)));
    }
}
