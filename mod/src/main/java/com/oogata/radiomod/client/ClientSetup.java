package com.oogata.radiomod.client;

import com.oogata.radiomod.config.ClientConfig;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** クライアント設定の登録と、設定画面・音量スライダーのつなぎ込み。クライアントだけで呼ぶ。 */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void init() {
        ModLoadingContext ctx = ModLoadingContext.get();
        ctx.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ctx.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new RadioConfigScreen(parent)));
        // 設定ファイルの読み込み・書き換えのたびに、再生と同期へ反映する。
        FMLJavaModLoadingContext.get().getModEventBus().addListener((ModConfigEvent e) -> {
            if (e.getConfig().getSpec() == ClientConfig.SPEC) {
                ClientConfig.INSTANCE.apply();
            }
        });
        MinecraftForge.EVENT_BUS.register(new VolumeOptions());
    }
}
