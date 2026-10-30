package com.oogata.radioplugin.player;

import java.util.ArrayList;
import java.util.List;

/** キューの表示用の文字列を作る（チャットとスコアボード）。 */
public final class QueueFormatter {

    /** サイドバーに出せる行数の上限。 */
    public static final int SCORE_MAX_LINES = 15;
    private static final int SCORE_TITLE_CHARS = 24;
    private static final String CURRENT_MARK = "▶";

    private QueueFormatter() {
    }

    public static String duration(long ms) {
        if (ms < 0) {
            return "LIVE";
        }
        long total = ms / 1000;
        long h = total / 3600;
        long m = total % 3600 / 60;
        long s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    public static String stateName(PlaybackEngine.State state) {
        return switch (state) {
            case STOPPED -> "停止中";
            case PLAYING -> "再生中";
            case PAUSED -> "一時停止中";
        };
    }

    public static String loopName(PlaybackEngine.Loop loop) {
        return switch (loop) {
            case NONE -> "なし";
            case SINGLE -> "単曲";
            case PLAYLIST -> "プレイリスト全体";
        };
    }

    /** チャット用。番号・タイトル・長さを並べ、再生中の曲に印を付ける。 */
    public static List<String> chatLines(PlaybackEngine engine) {
        List<Track> queue = engine.queue();
        List<String> lines = new ArrayList<>();
        lines.add("キュー（" + queue.size() + "曲） 状態: " + stateName(engine.state())
                + " / loop: " + loopName(engine.loop()));
        for (int i = 0; i < queue.size(); i++) {
            Track t = queue.get(i);
            String mark = i == engine.index() && engine.state() != PlaybackEngine.State.STOPPED
                    ? CURRENT_MARK : " ";
            lines.add(mark + " " + (i + 1) + ". " + t.title() + " [" + duration(t.durationMs()) + "]");
        }
        return lines;
    }

    /** サイドバー用。15行に収まるよう、再生中の曲の前後を中心に出し、長いタイトルは切り詰める。 */
    public static List<String> scoreLines(List<Track> queue, int index, boolean playing) {
        int size = queue.size();
        int from = 0;
        if (size > SCORE_MAX_LINES) {
            from = Math.max(0, Math.min(index - SCORE_MAX_LINES / 2, size - SCORE_MAX_LINES));
        }
        int to = Math.min(size, from + SCORE_MAX_LINES);
        List<String> lines = new ArrayList<>();
        for (int i = from; i < to; i++) {
            String mark = playing && i == index ? CURRENT_MARK : " ";
            lines.add(mark + (i + 1) + " " + clip(queue.get(i).title(), SCORE_TITLE_CHARS));
        }
        return lines;
    }

    static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
