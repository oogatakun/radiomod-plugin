package com.oogata.radiomod.client;

import com.oogata.radiomod.RadioMod;
import com.oogata.radiomod.config.ClientConfig;
import java.lang.reflect.Field;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「音とサウンド設定」画面に、「音楽（/ps）」と「ラジオ」の音量スライダーを足す。
 * 画面の中身（OptionsList）は、フィールドの型で見つける（名前は版や環境で変わるため）。
 */
public final class VolumeOptions {

    private static volatile boolean hookFailed;

    @SubscribeEvent
    public void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof SoundOptionsScreen screen) || hookFailed) {
            return;
        }
        OptionsList list = findList(screen);
        if (list == null) {
            hookFailed = true;
            RadioMod.LOGGER.warn("音とサウンド設定画面にスライダーを足せませんでした。mod の設定画面から音量を変えてください");
            return;
        }
        ClientConfig c = ClientConfig.INSTANCE;
        list.addSmall(slider("音楽（/ps）", c.musicVolume), slider("ラジオ", c.radioVolume));
    }

    /** 画面を閉じるとき、変えた値を設定ファイルに書く。 */
    @SubscribeEvent
    public void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof SoundOptionsScreen) {
            ClientConfig.SPEC.save();
        }
    }

    private static OptionInstance<Double> slider(String caption, ForgeConfigSpec.IntValue value) {
        return new OptionInstance<>(caption, OptionInstance.noTooltip(),
                (label, v) -> Component.literal(caption + ": ").append(
                        v == 0 ? CommonComponents.OPTION_OFF : Component.literal(Math.round(v * 100) + "%")),
                OptionInstance.UnitDouble.INSTANCE, value.get() / 100.0, v -> {
                    value.set((int) Math.round(v * 100));
                    ClientConfig.INSTANCE.apply();
                });
    }

    private static OptionsList findList(Screen screen) {
        for (Class<?> k = screen.getClass(); k != null && k != Screen.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (OptionsList.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (OptionsList) f.get(screen);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
