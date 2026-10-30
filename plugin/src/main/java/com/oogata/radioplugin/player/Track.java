package com.oogata.radioplugin.player;

/** キューに入る1曲。durationMs が -1 のものはライブ配信。 */
public record Track(String url, String title, long durationMs) {

    public static final long LIVE = -1L;

    public boolean live() {
        return durationMs < 0;
    }
}
