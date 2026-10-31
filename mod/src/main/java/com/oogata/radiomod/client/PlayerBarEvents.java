package com.oogata.radiomod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/** 左下のバーの表示(HUD)と、押している間だけカーソルを出すキーの登録。 */
public final class PlayerBarEvents {

    public static final KeyMapping SHOW_CURSOR = new KeyMapping("key.radiomod.show_cursor",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.radiomod");

    private PlayerBarEvents() {
    }

    /** MOD イベントバスのイベント。 */
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(SHOW_CURSOR);
    }

    /** MOD イベントバスのイベント。 */
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("player_bar", (gui, graphics, partialTick, width, height) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.screen instanceof CursorScreen) {
                return; // CursorScreen が自分で描く
            }
            if (PlayerBar.update()) {
                PlayerBar.render(graphics, width, height, -1, -1);
            }
        });
    }

    /** FORGE イベントバスのイベント。キーが押されたら、カーソルを出す画面を開く。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.screen != null) {
            return;
        }
        if (SHOW_CURSOR.isDown() && PlayerBar.update()) {
            mc.setScreen(new CursorScreen(SHOW_CURSOR));
        }
    }
}
