package com.oogata.radiomod.client;

import com.oogata.radiomod.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** mod の設定画面（mod 一覧の「設定」から開く）。音量・ラジオ音のオン・オフ・同期の設定を変える。 */
public final class RadioConfigScreen extends Screen {

    private static final int ROW = 24;

    private final Screen parent;
    private EditBox startBox;
    private EditBox stopBox;
    private EditBox maxBox;
    private EditBox offsetBox;

    public RadioConfigScreen(Screen parent) {
        super(Component.literal("Radio Mod"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientConfig c = ClientConfig.INSTANCE;
        int w = 200;
        int x = (width - w) / 2;
        int y = 32;
        addRenderableWidget(new VolumeSlider(x, y, w, "音楽（/ps）", c.musicVolume));
        y += ROW;
        addRenderableWidget(new VolumeSlider(x, y, w, "ラジオ", c.radioVolume));
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(c.radioEnabled.get())
                .create(x, y, w, 20, Component.literal("ラジオの音"), (b, v) -> {
                    c.radioEnabled.set(v);
                    c.apply();
                }));
        y += ROW + 6;
        startBox = numberBox(x + 120, y, 80, "補正を始める幅（ms）", c.driftStartMs.get());
        y += ROW;
        stopBox = numberBox(x + 120, y, 80, "補正をやめる幅（ms）", c.driftStopMs.get());
        y += ROW;
        maxBox = numberBox(x + 120, y, 80, "速度調整の上限（±%）", c.maxDeviationPercent.get());
        y += ROW;
        offsetBox = numberBox(x + 120, y, 80, "手動の補正値（ms）", c.manualOffsetMs.get());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, height - 28, w, 20).build());
    }

    private EditBox numberBox(int x, int y, int w, String label, Number value) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(label));
        box.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
        box.setValue(value instanceof Integer ? Integer.toString(value.intValue()) : Double.toString(value.doubleValue()));
        addRenderableWidget(box);
        return box;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        int x = (width - 200) / 2;
        for (EditBox box : new EditBox[] {startBox, stopBox, maxBox, offsetBox}) {
            g.drawString(font, box.getMessage(), x, box.getY() + 6, 0xA0A0A0);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        ClientConfig c = ClientConfig.INSTANCE;
        setDouble(c.driftStartMs, startBox.getValue(), 1, 1000);
        setDouble(c.driftStopMs, stopBox.getValue(), 0, 1000);
        setDouble(c.maxDeviationPercent, maxBox.getValue(), 0, 10);
        setInt(c.manualOffsetMs, offsetBox.getValue(), -2000, 2000);
        c.apply();
        ClientConfig.SPEC.save();
        minecraft.setScreen(parent);
    }

    /** 数として読めない入力は無視し、範囲外は範囲に収める。 */
    private static void setDouble(ForgeConfigSpec.DoubleValue value, String text, double min, double max) {
        try {
            value.set(Math.max(min, Math.min(max, Double.parseDouble(text))));
        } catch (NumberFormatException e) {
            // 読めない入力は、いまの値のままにする。
        }
    }

    private static void setInt(ForgeConfigSpec.IntValue value, String text, int min, int max) {
        try {
            value.set((int) Math.max(min, Math.min(max, Math.round(Double.parseDouble(text)))));
        } catch (NumberFormatException e) {
            // 読めない入力は、いまの値のままにする。
        }
    }

    private static final class VolumeSlider extends AbstractSliderButton {
        private final String caption;
        private final ForgeConfigSpec.IntValue config;

        VolumeSlider(int x, int y, int w, String caption, ForgeConfigSpec.IntValue config) {
            super(x, y, w, 20, Component.empty(), config.get() / 100.0);
            this.caption = caption;
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(caption + ": " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            config.set((int) Math.round(value * 100));
            ClientConfig.INSTANCE.apply();
        }
    }
}
