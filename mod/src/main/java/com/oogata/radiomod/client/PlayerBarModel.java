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

    public enum Action { PREVIOUS, TOGGLE, NEXT, LIST, CLOSE }

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
     * ボタンに対応する CONTROL の action。LIST(プレーヤー画面を開く)は、サーバーへの操作ではないので null。
     * 権限や、共有を聞いているかどうかは、サーバーが確かめる。CLOSE の STOP は、共有では自分が聞くのをやめるだけ。
     */
    public static Byte controlAction(Action action) {
        return switch (action) {
            case PREVIOUS -> Messages.Control.PREVIOUS;
            case TOGGLE -> Messages.Control.PAUSE;
            case NEXT -> Messages.Control.SKIP;
            case CLOSE -> Messages.Control.STOP;
            case LIST -> null;
        };
    }

    /** 進行バーの割合(0〜1)。ライブや長さが分からないときは -1。 */
    public static double progress(NowPlaying n) {
        if (n.durationMs() <= 0 || n.positionMs() < 0) {
            return -1;
        }
        return Math.max(0, Math.min(1, (double) n.positionMs() / n.durationMs()));
    }

    /** 3:05 のような表示。1時間以上なら 1:00:00 のように、時間も出す。 */
    public static String time(long ms) {
        long total = Math.max(0, ms) / 1000;
        long hours = total / 3600;
        long minutes = total % 3600 / 60;
        long seconds = total % 60;
        if (hours > 0) {
            return hours + ":" + String.format("%02d", minutes) + ":" + String.format("%02d", seconds);
        }
        return minutes + ":" + String.format("%02d", seconds);
    }
}
