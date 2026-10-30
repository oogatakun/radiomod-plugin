package com.oogata.radiomod.audio;

import com.oogata.radiomod.net.Messages;

/** 取得・再生の失敗。reason は protocol.md の ERROR の reason と同じ値。 */
public final class AudioFailure extends Exception {

    private final byte reason;

    public AudioFailure(byte reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public AudioFailure(byte reason, String detail, Throwable cause) {
        super(detail, cause);
        this.reason = reason;
    }

    public byte reason() {
        return reason;
    }

    public String detail() {
        return getMessage() == null ? "" : getMessage();
    }

    public static AudioFailure fetch(String detail) {
        return new AudioFailure(Messages.Error.FETCH_FAILED, detail);
    }

    public static AudioFailure unsupported(String detail) {
        return new AudioFailure(Messages.Error.UNSUPPORTED_URL, detail);
    }

    public static AudioFailure decode(String detail) {
        return new AudioFailure(Messages.Error.DECODE_FAILED, detail);
    }

    public static AudioFailure other(String detail) {
        return new AudioFailure(Messages.Error.OTHER, detail);
    }
}
