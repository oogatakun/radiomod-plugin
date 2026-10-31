package com.oogata.radiomod.client;

import com.oogata.radiomod.net.Messages;
import com.oogata.radiomod.sync.SlotRouter.NowPlaying;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 左下のバーに、どの曲を出すかと、ボタンが実行するコマンド。Minecraft には依存しない。 */
public final class PlayerBarModel {

    public enum Action { PREVIOUS, TOGGLE, NEXT }

    /** × で隠した曲(trackId)。その曲が終わる(スロットから消える)までは出さない。 */
    private final Set<Long> dismissed = new HashSet<>();

    /**
     * 出す曲を選ぶ。鳴っているもの(audible)だけが対象で、止まっていないものを優先する。
     * 同じ状態なら、共有 → 個人 → ラジオの順。
     */
    public Optional<NowPlaying> choose(List<NowPlaying> all) {
        dismissed.removeIf(id -> all.stream().noneMatch(n -> n.trackId() == id));
        return all.stream()
                .filter(NowPlaying::audible)
                .filter(n -> !dismissed.contains(n.trackId()))
                .min(Comparator.comparing(NowPlaying::paused).thenComparingInt(n -> rank(n.slot())));
    }

    public void dismiss(long trackId) {
        dismissed.add(trackId);
    }

    private static int rank(Messages.Slot slot) {
        return switch (slot.slotType()) {
            case Messages.Slot.SHARED -> 0;
            case Messages.Slot.PERSONAL -> 1;
            default -> 2;
        };
    }

    /**
     * ボタンが実行するコマンド(先頭の / なし)。ラジオは、mod がラジオの名前を知らないため、まだ操作できない(null)。
     */
    public static String command(Messages.Slot slot, Action action) {
        String sub = switch (action) {
            case PREVIOUS -> "previous";
            case TOGGLE -> "pause";
            case NEXT -> "skip";
        };
        return switch (slot.slotType()) {
            case Messages.Slot.PERSONAL -> "ps " + sub;
            case Messages.Slot.SHARED -> "ps all " + sub;
            default -> null;
        };
    }

    /** 進行バーの割合(0〜1)。ライブや長さが分からないときは -1。 */
    public static double progress(NowPlaying n) {
        if (n.durationMs() <= 0 || n.positionMs() < 0) {
            return -1;
        }
        return Math.max(0, Math.min(1, (double) n.positionMs() / n.durationMs()));
    }

    /** 3:05 のような表示。 */
    public static String time(long ms) {
        long s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
