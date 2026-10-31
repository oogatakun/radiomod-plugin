package com.oogata.radioplugin.radio;

import java.util.regex.Pattern;

/** ラジオ名のルール（仕様書5章）。空白なし、英数字・日本語・_・- のみ。 */
public final class RadioNames {

    private static final Pattern VALID = Pattern.compile(
            "[A-Za-z0-9_\\-\\p{InHiragana}\\p{InKatakana}\\p{InCJKUnifiedIdeographs}\\u3005]+");

    private RadioNames() {
    }

    /** 問題がなければ null、あればエラーメッセージ（日本語）を返す。重複の確認は呼び出し側で行う。 */
    public static String validate(String name) {
        if (name == null || name.isEmpty()) {
            return "ラジオ名を指定してください";
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isWhitespace(name.charAt(i))) {
                return "ラジオ名に空白は使えません";
            }
        }
        if (!VALID.matcher(name).matches()) {
            return "ラジオ名に使えるのは、英数字・日本語・_・- だけです";
        }
        return null;
    }
}
