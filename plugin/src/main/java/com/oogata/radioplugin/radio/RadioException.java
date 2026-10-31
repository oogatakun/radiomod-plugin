package com.oogata.radioplugin.radio;

/** ラジオの操作が失敗した。メッセージはそのままプレイヤーに見せられる日本語。 */
public final class RadioException extends RuntimeException {

    public RadioException(String message) {
        super(message);
    }
}
