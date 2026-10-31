package com.oogata.radiomod.net;

import com.oogata.radiomod.RadioMod;
import com.oogata.radiomod.sync.ClientSync;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

/**
 * radiomod:main の送受信（クライアント側）。
 *
 * <p>SimpleChannel は先頭にディスクリミネータを付けるので使わず、EventNetworkChannel で
 * 先頭の1バイト（メッセージ種別）を含む生のペイロードを読み書きする。
 */
public final class RadioNet {

    private static final ResourceLocation CHANNEL_ID = new ResourceLocation(Messages.CHANNEL);
    private static final ResourceLocation REGISTER_ID = new ResourceLocation("minecraft", "register");

    private static EventNetworkChannel channel;

    private RadioNet() {
    }

    public static void init() {
        // バージョン照合は常に受け入れる（Forge でないサーバーとも通信するため）。
        channel = NetworkRegistry.newEventChannel(CHANNEL_ID, () -> "1", v -> true, v -> true);
        channel.addListener(RadioNet::onServerPayload);
        MinecraftForge.EVENT_BUS.addListener(RadioNet::onLogin);
        MinecraftForge.EVENT_BUS.addListener(RadioNet::onLogout);
        ClientSync.init();
    }

    private static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Connection connection = event.getConnection();
        if (connection == null) {
            return;
        }
        // Forge でないサーバーは、クライアントが登録したチャンネルにしか送らない。
        send(connection, REGISTER_ID, (Messages.CHANNEL + "\0").getBytes(StandardCharsets.UTF_8));
        String version = ModList.get().getModContainerById(RadioMod.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
        send(connection, CHANNEL_ID, Codec.encode(new Messages.Hello(Messages.PROTOCOL_VERSION, version)));
        RadioMod.LOGGER.info("HELLO を送信 (mod {}, protocol {})", version, Messages.PROTOCOL_VERSION);
        ClientSync.onConnected();
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSync.onDisconnected();
    }

    private static void send(Connection connection, ResourceLocation id, byte[] payload) {
        connection.send(new ServerboundCustomPayloadPacket(id, new FriendlyByteBuf(Unpooled.wrappedBuffer(payload))));
    }

    /** どのスレッドからでも、いまのサーバーへ送る。接続がなくて送れなかったら false。 */
    public static boolean send(Messages message) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null) {
            return false;
        }
        try {
            send(listener.getConnection(), CHANNEL_ID, Codec.encode(message));
            return true;
        } catch (RuntimeException e) {
            RadioMod.LOGGER.warn("送れないメッセージを捨てました: {} ({})", message, e.toString());
            return false;
        }
    }

    private static void onServerPayload(NetworkEvent.ServerCustomPayloadEvent event) {
        event.getSource().get().setPacketHandled(true);
        long t1 = System.nanoTime(); // 時計合わせは、受け取った瞬間の時刻を使う
        ByteBuf payload = event.getPayload();
        byte[] data = new byte[payload.readableBytes()];
        payload.readBytes(data);
        Messages message = ClientSync.decode(data);
        if (message == null) {
            return;
        }
        try {
            ClientSync.onMessage(message, t1);
        } catch (RuntimeException e) {
            // 1つのメッセージの失敗で、通信全体を止めない。
            RadioMod.LOGGER.warn("メッセージの処理に失敗: {} ({})", message, e.toString());
        }
    }
}
