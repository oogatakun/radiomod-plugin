package com.oogata.radiomod.net;

import com.oogata.radiomod.RadioMod;
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
    }

    private static void send(Connection connection, ResourceLocation id, byte[] payload) {
        connection.send(new ServerboundCustomPayloadPacket(id, new FriendlyByteBuf(Unpooled.wrappedBuffer(payload))));
    }

    /** ゲーム中のどこからでも、いまのサーバーへ送る。 */
    public static void send(Messages message) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener != null) {
            send(listener.getConnection(), CHANNEL_ID, Codec.encode(message));
        }
    }

    private static void onServerPayload(NetworkEvent.ServerCustomPayloadEvent event) {
        event.getSource().get().setPacketHandled(true);
        ByteBuf payload = event.getPayload();
        byte[] data = new byte[payload.readableBytes()];
        payload.readBytes(data);
        try {
            Messages message = Codec.decode(data);
            RadioMod.LOGGER.info("受信: {}", message); // ステップ1は中身をログに出すだけ
        } catch (Codec.DecodeException e) {
            RadioMod.LOGGER.warn("読めないメッセージを無視: {}", e.getMessage());
        }
    }
}
