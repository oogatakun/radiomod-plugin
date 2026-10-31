package com.oogata.radioplugin.player;

import com.oogata.radioplugin.net.Messages;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 再生サービスが外の世界（プレイヤー・チャット・mod への送信）に触れる入口。テストでは偽物に差し替える。 */
public interface PlayerHost {

    /** チャットのボタン。クリックで command を実行する。 */
    record Button(String label, String command) {
    }

    String nameOf(UUID player);

    boolean isOnline(UUID player);

    boolean hasMod(UUID player);

    /** オンラインで mod 導入済みのプレイヤー。 */
    Collection<UUID> modPlayers();

    /** mod へ送る。mod 未導入のプレイヤーには何も送らない。 */
    void send(UUID player, Messages message);

    void tell(UUID player, String text);

    void tellButtons(UUID player, String text, List<Button> buttons);

    /** 曲が始まったときの通知（設定の announce に従って出す）。 */
    void announceTrack(UUID player, String title);

    void showScore(UUID player, String title, List<String> lines);

    void hideScore(UUID player);
}
