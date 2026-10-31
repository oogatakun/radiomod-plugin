package com.oogata.radiomod.sync;

/**
 * 鳴らし始める瞬間（曲の始め・再開・取得が途切れたあとの再開）の判断。
 * この時点では音が止まっているので、位置を合わせ直してよい。
 *
 * <p>鳴らすべき位置が負のあいだは待つ。0以上になったら、鳴る準備ができている位置と比べる。
 * 準備の位置が遅れていたら、進めた位置から読み直す（読み直しの間にも時間が進むので、少し先を狙う）。
 */
public final class StartGate {

    public enum Action { WAIT, PLAY, SEEK }

    public record Decision(Action action, long seekToMs) {
        static final Decision WAIT = new Decision(Action.WAIT, 0);
        static final Decision PLAY = new Decision(Action.PLAY, 0);
    }

    /** 準備の位置が鳴らすべき位置より、これ以上遅れていたら読み直す。これ未満の遅れは速度の補正に任せる。 */
    public static final long LATE_MS = 60;
    /** 準備の位置が鳴らすべき位置より、これ以上先に進んでいたら読み直す（それ未満なら待つ）。 */
    public static final long EARLY_MAX_MS = 2500;
    /** 読み直しの間に進む時間の見込み。曲の途中から鳴らし始めるときは、最初からこれだけ先を読む。 */
    public static final long INITIAL_LEAD_MS = 400;
    static final long MAX_LEAD_MS = 3200;

    private long leadMs = INITIAL_LEAD_MS;

    /** 取得を始める位置。鳴らすべき位置が0以上なら、準備の間に進む分だけ先を読む。 */
    public static long initialStart(long desiredMs) {
        return desiredMs > 0 ? desiredMs + INITIAL_LEAD_MS : 0;
    }

    /**
     * @param desiredMs 鳴らすべき位置（負なら待つ）
     * @param readyMs   鳴る準備ができている位置（鳴らし始めると、ここから鳴る）
     * @param canSeek   位置を合わせ直せる音源か
     */
    public Decision decide(long desiredMs, long readyMs, boolean canSeek) {
        if (desiredMs < 0) {
            return Decision.WAIT;
        }
        long diff = desiredMs - readyMs;
        if (canSeek && (diff > LATE_MS || -diff > EARLY_MAX_MS)) {
            long target = desiredMs + leadMs;
            if (diff > LATE_MS) {
                leadMs = Math.min(MAX_LEAD_MS, leadMs * 2); // 間に合わなかったので、次はもっと先を狙う
            }
            return new Decision(Action.SEEK, target);
        }
        if (diff < 0) {
            return Decision.WAIT;
        }
        leadMs = INITIAL_LEAD_MS;
        return Decision.PLAY;
    }

    public void reset() {
        leadMs = INITIAL_LEAD_MS;
    }
}
