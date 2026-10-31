package com.oogata.radioplugin.player;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * plugin が取得してよい URL の条件。http / https のみで、内部アドレス（localhost・プライベート網など）は拒否する。
 * サーバーから内部ネットワークへ接続させないための検査。
 */
public final class UrlPolicy {

    public enum Verdict {
        OK,
        /** URL として読めない、または http / https ではない */
        INVALID,
        /** localhost や内部アドレス */
        INTERNAL
    }

    private UrlPolicy() {
    }

    /** 名前の引き当てをしない検査。コマンドを受けた時点（メインスレッド）で使う。 */
    public static Verdict check(String url) {
        URI uri = parse(url);
        if (uri == null) {
            return Verdict.INVALID;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return Verdict.INVALID;
        }
        return isInternalHostName(host) ? Verdict.INTERNAL : Verdict.OK;
    }

    /** DNS を引いて、内部アドレスに向いていないかを調べる。ブロックするので、メインスレッドでは呼ばない。 */
    public static Verdict checkResolved(String url) {
        Verdict first = check(url);
        if (first != Verdict.OK) {
            return first;
        }
        String host = parse(url).getHost();
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isInternal(address)) {
                    return Verdict.INTERNAL;
                }
            }
        } catch (UnknownHostException e) {
            // 引けないホストは、取得の段階で失敗として扱う
            return Verdict.OK;
        }
        return Verdict.OK;
    }

    static URI parse(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            return uri;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** ホスト名が文字の上で内部を指しているか（名前の引き当てなし）。 */
    static boolean isInternalHostName(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local")
                || h.endsWith(".internal") || h.endsWith(".lan")) {
            return true;
        }
        if (h.indexOf(':') >= 0 || looksLikeIpv4(h)) {
            try {
                return isInternal(InetAddress.getByName(h)); // 数値リテラルなので引き当ては起きない
            } catch (UnknownHostException e) {
                return true; // 読めない数値表記は拒否する
            }
        }
        return false;
    }

    private static boolean looksLikeIpv4(String h) {
        // 10.0.0.1 や 2130706433 や 0x7f.1 のような、数字と点だけ（16進を含む）のもの
        return !h.isEmpty() && h.matches("[0-9a-fx.]+") && Character.isDigit(h.charAt(0));
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            // 100.64.0.0/10（キャリアNAT）、0.0.0.0/8
            return first == 0 || (first == 100 && second >= 64 && second <= 127);
        }
        // IPv6 のユニークローカル fc00::/7
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }
}
