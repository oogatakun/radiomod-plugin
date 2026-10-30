package com.oogata.radioplugin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.oogata.radioplugin.player.UrlPolicy.Verdict;
import org.junit.jupiter.api.Test;

class UrlPolicyTest {

    @Test
    void httpとhttpsだけ通す() {
        assertEquals(Verdict.OK, UrlPolicy.check("https://example.com/a.mp3"));
        assertEquals(Verdict.OK, UrlPolicy.check("http://example.com:8000/stream"));
        assertEquals(Verdict.OK, UrlPolicy.check("HTTPS://Example.com/a"));
        assertEquals(Verdict.INVALID, UrlPolicy.check("ftp://example.com/a.mp3"));
        assertEquals(Verdict.INVALID, UrlPolicy.check("file:///etc/passwd"));
        assertEquals(Verdict.INVALID, UrlPolicy.check("example.com/a.mp3"));
        assertEquals(Verdict.INVALID, UrlPolicy.check("ytsearch:abc"));
        assertEquals(Verdict.INVALID, UrlPolicy.check("not a url"));
        assertEquals(Verdict.INVALID, UrlPolicy.check("https://"));
    }

    @Test
    void 内部アドレスは拒否する() {
        for (String url : new String[] {
                "http://localhost/a", "http://LOCALHOST:8080/a", "http://foo.localhost/a",
                "http://127.0.0.1/a", "http://127.1.2.3/a", "http://10.0.0.5/a", "http://192.168.1.10/a",
                "http://172.16.0.1/a", "http://172.31.255.255/a", "http://169.254.169.254/latest",
                "http://0.0.0.0/a", "http://[::1]/a", "http://[fe80::1]/a", "http://[fd00::1]/a",
                "http://100.64.0.1/a", "http://printer.local/a"}) {
            assertEquals(Verdict.INTERNAL, UrlPolicy.check(url), url);
        }
    }

    @Test
    void 外部のアドレスは通す() {
        assertEquals(Verdict.OK, UrlPolicy.check("http://8.8.8.8/a"));
        assertEquals(Verdict.OK, UrlPolicy.check("http://172.32.0.1/a"));
        assertEquals(Verdict.OK, UrlPolicy.check("http://11.0.0.1/a"));
    }

    @Test
    void 数値の別表記でも内部アドレスを拒否する() {
        assertEquals(Verdict.INTERNAL, UrlPolicy.check("http://2130706433/a")); // 127.0.0.1
        // URL として読めない表記は、内部扱いでなくても INVALID で弾かれる
        assertEquals(Verdict.INVALID, UrlPolicy.check("http://0x7f.0.0.1/a"));
    }
}
