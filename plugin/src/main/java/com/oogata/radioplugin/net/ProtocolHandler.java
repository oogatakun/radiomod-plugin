package com.oogata.radioplugin.net;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * mod から届いたメッセージの処理（HELLO の記録・版の確認・時計合わせ）。Bukkit に依存しないので、単体で試せる。
 * docs/protocol.md の「時計合わせ」と「手順とルール」の1・13に当たる。
 */
public final class ProtocolHandler {

    /** 送信先。mod 導入済みかどうかは呼び出し側（記録は {@link #hasMod}）で見る。 */
    public interface Sender {
        void send(UUID player, Messages message);
    }

    /** 処理した結果を、再生側へ伝える。 */
    public interface Events {
        void hello(UUID player);

        void error(UUID player, Messages.Error error);
    }

    private final Sender sender;
    private final LongSupplier serverNowMs;
    private final Consumer<String> info;
    private final Consumer<String> warn;
    private final Events events;
    private final Set<UUID> modPlayers = ConcurrentHashMap.newKeySet();

    public ProtocolHandler(Sender sender, LongSupplier serverNowMs, Consumer<String> info, Consumer<String> warn,
                    Events events) {
        this.sender = sender;
        this.serverNowMs = serverNowMs;
        this.info = info;
        this.warn = warn;
        this.events = events;
    }

    public boolean hasMod(UUID player) {
        return modPlayers.contains(player);
    }

    public void remove(UUID player) {
        modPlayers.remove(player);
    }

    public void clear() {
        modPlayers.clear();
    }

    /** デコード済みのメッセージを処理する。name はログ用のプレイヤー名。 */
    public void onMessage(UUID player, String name, Messages message) {
        if (message instanceof Messages.TimeReq req) {
            // 時計合わせ: 間に何も挟まず、clientNanos をそのまま返す。HELLO 済みのプレイヤーにだけ返す。
            if (hasMod(player)) {
                sender.send(player, new Messages.Time(req.clientNanos(), serverNowMs.getAsLong()));
            }
        } else if (message instanceof Messages.Hello hello) {
            onHello(player, name, hello);
        } else if (message instanceof Messages.Error error) {
            if (hasMod(player)) {
                events.error(player, error);
            }
        } else {
            warn.accept(name + " から未処理のメッセージ: " + message);
        }
    }

    private void onHello(UUID player, String name, Messages.Hello hello) {
        if (hello.protocolVersion() != Messages.PROTOCOL_VERSION) {
            modPlayers.remove(player);
            warn.accept(name + " の mod は protocol " + hello.protocolVersion() + "(mod " + hello.modVersion()
                    + ") で、サーバーは " + Messages.PROTOCOL_VERSION + " です。何も送りません。");
            return;
        }
        modPlayers.add(player);
        info.accept("HELLO を受信: " + name + " (mod " + hello.modVersion() + ", protocol "
                + hello.protocolVersion() + ")");
        events.hello(player);
    }
}
