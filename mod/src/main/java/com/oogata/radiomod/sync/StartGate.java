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
    /** 読み直しの間に進む時間の見込みの最小値。曲の途中から鳴らし始めるときは、最初からこれだけ先を読む。 */
    public static final long INITIAL_LEAD_MS = 400;
    static final long MAX_LEAD_MS = 8000;
    /** 読み直しは、1回の開始・再開につきこの回数まで。超えたら、準備できた位置から鳴らし、残りは速度の補正に任せる。 */
    public static final int MAX_SEEKS = 3;
    /** 読み直しの最小の間隔。連続で呼ばれても、この間は待つ。 */
    public static final long MIN_SEEK_INTERVAL_MS = 500;

    private long leadMs = INITIAL_LEAD_MS;
    private int seeks;
    private long lastSeekAtMs = Long.MIN_VALUE;
    private boolean measuring;

    /** 取得を始める位置。鳴らすべき位置が0以上なら、準備の間に進む分だけ先を読む。 */
    public static long initialStart(long desiredMs) {
        return desiredMs > 0 ? desiredMs + INITIAL_LEAD_MS : 0;
    }

    /**
     * @param desiredMs 鳴らすべき位置（負なら待つ）
     * @param readyMs   鳴る準備ができている位置（鳴らし始めると、ここから鳴る）
     * @param canSeek   位置を合わせ直せる音源か
     * @param nowMs     いまの時刻（ミリ秒。単調に増える時計ならなんでもよい）
     */
    public Decision decide(long desiredMs, long readyMs, boolean canSeek, long nowMs) {
        if (measuring) {
            // 直前の読み直しから準備ができるまでにかかった時間を、次の先読み量にする。
            long elapsed = Math.max(0, nowMs - lastSeekAtMs);
            leadMs = Math.max(INITIAL_LEAD_MS, Math.min(MAX_LEAD_MS, elapsed * 5 / 4 + 100));
            measuring = false;
        }
        if (desiredMs < 0) {
            return Decision.WAIT;
        }
        long diff = desiredMs - readyMs;
        boolean late = diff > LATE_MS;
        boolean farEarly = -diff > EARLY_MAX_MS;
        boolean intervalOk = lastSeekAtMs == Long.MIN_VALUE || nowMs - lastSeekAtMs >= MIN_SEEK_INTERVAL_MS;
        if (canSeek && (late || farEarly) && seeks < MAX_SEEKS) {
            if (!intervalOk) {
                return Decision.WAIT;
            }
            seeks++;
            lastSeekAtMs = nowMs;
            measuring = true;
            return new Decision(Action.SEEK, desiredMs + leadMs);
        }
        if (diff < 0) {
            return Decision.WAIT;
        }
        // 上限まで読み直しても遅れているときも、ここで鳴らす。残りのずれは速度の補正に任せる。
        return Decision.PLAY;
    }

    /** 読み直しの回数。デバッグ表示用。 */
    public int seeks() {
        return seeks;
    }

    /** 新しい開始・再開のとき。読み直しの回数を戻す（最後の読み直しの時刻は残し、連打でも間隔を守らせる）。 */
    public void reset() {
        leadMs = INITIAL_LEAD_MS;
        seeks = 0;
        measuring = false;
    }
}
