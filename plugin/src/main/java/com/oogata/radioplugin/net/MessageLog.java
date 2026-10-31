package com.oogata.radioplugin.net;

/** mod とやり取りするメッセージの、1行の診断ログ。 */
public final class MessageLog {

    private MessageLog() {
    }

    /** TIME / TIME_REQ は量が多いので、debug-net のときだけ出す。 */
    public static boolean isNoisy(Messages message) {
        return message instanceof Messages.Time || message instanceof Messages.TimeReq;
    }

    /**
     * @param outgoing true なら サーバー→mod、false なら mod→サーバー
     * @param nowMs    ログを出す時点のサーバー時刻
     */
    public static String format(boolean outgoing, String player, Messages message, long nowMs) {
        StringBuilder sb = new StringBuilder(outgoing ? "送信 → " : "受信 ← ").append(player).append(' ');
        if (message instanceof Messages.Play m) {
            sb.append("PLAY ").append(slot(m.slot())).append(" trackId=").append(m.trackId())
                    .append(" url=").append(m.url()).append(" durationMs=").append(m.durationMs())
                    .append(origin(m.originMs(), nowMs));
        } else if (message instanceof Messages.Pause m) {
            sb.append("PAUSE ").append(slot(m.slot())).append(" positionMs=")
                    .append(m.positionMs());
        } else if (message instanceof Messages.Resume m) {
            sb.append("RESUME ").append(slot(m.slot())).append(origin(m.originMs(), nowMs));
        } else if (message instanceof Messages.Stop m) {
            sb.append("STOP ").append(slot(m.slot()));
        } else if (message instanceof Messages.Sync m) {
            sb.append("SYNC ").append(slot(m.slot())).append(origin(m.originMs(), nowMs));
        } else if (message instanceof Messages.Move m) {
            sb.append("MOVE ").append(slot(m.slot()));
        } else if (message instanceof Messages.Time m) {
            sb.append("TIME clientNanos=").append(m.clientNanos()).append(" serverNowMs=")
                    .append(m.serverNowMs());
        } else if (message instanceof Messages.Hello m) {
            sb.append("HELLO protocol=").append(m.protocolVersion()).append(" mod=")
                    .append(m.modVersion());
        } else if (message instanceof Messages.Error m) {
            sb.append("ERROR ").append(slot(m.slot())).append(" trackId=").append(m.trackId())
                    .append(" reason=").append(m.reason()).append(" detail=").append(m.detail());
        } else if (message instanceof Messages.TimeReq m) {
            sb.append("TIME_REQ clientNanos=").append(m.clientNanos());
        }
        return sb.append(" now=").append(nowMs).toString();
    }

    private static String origin(long originMs, long nowMs) {
        return " originMs=" + originMs + " (開始まで " + (originMs - nowMs) + "ms)";
    }

    private static String slot(Messages.Slot slot) {
        return switch (slot.slotType()) {
            case Messages.Slot.PERSONAL -> "slot=個人";
            case Messages.Slot.SHARED -> "slot=共有";
            default -> "slot=ラジオ(" + slot.radioId() + ")";
        };
    }
}
