package com.oogata.radiomod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * キーを押している間だけ開く、何も描かない画面。開いている間はカーソルが出て、左下のバーをクリックできる。
 * キーを離すと閉じる。
 */
public final class CursorScreen extends Screen {

    private final KeyMapping key;

    public CursorScreen(KeyMapping key) {
        super(Component.empty());
        this.key = key;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        PlayerBar.update();
        if (!isKeyHeld() || !PlayerBar.visible()) {
            onClose();
        }
    }

    private boolean isKeyHeld() {
        InputConstants.Key k = key.getKey();
        if (k.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), k.getValue());
        }
        return false; // マウスのボタンなどに割り当てた場合は、開いたらすぐ閉じる(使えない割り当て)
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        PlayerBar.render(g, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return button == 0 && PlayerBar.click(width, height, mouseX, mouseY);
    }
}
