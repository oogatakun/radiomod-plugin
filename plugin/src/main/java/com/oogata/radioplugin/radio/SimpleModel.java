package com.oogata.radioplugin.radio;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

/** モデルのファイルがないときに使う簡易モデル（仕様書5章の表）。1ブロックの箱の中に収める。 */
public final class SimpleModel {

    public static final String TAG = "radiosimple";
    /** ランプのパーツにだけ付く目印。再生中は点灯させる。 */
    public static final String LAMP_TAG = "radiomod_lamp";

    private SimpleModel() {
    }

    public static List<PartSpec> parts() {
        List<PartSpec> p = new ArrayList<>();
        p.add(part("minecraft:dark_oak_planks", new Matrix4f().translate(0.05f, 0f, 0.3f).scale(0.9f, 0.5f, 0.4f)));
        p.add(part("minecraft:iron_trapdoor[facing=north,half=bottom,open=true]",
                new Matrix4f().translate(0.12f, 0.08f, 0.3f).scale(0.4f, 0.34f, 0.4f)));
        p.add(part("minecraft:light_gray_concrete", new Matrix4f().translate(0.62f, 0.2f, 0.7f).scale(0.12f, 0.12f, 0.03f)));
        p.add(part("minecraft:light_gray_concrete", new Matrix4f().translate(0.78f, 0.2f, 0.7f).scale(0.12f, 0.12f, 0.03f)));
        p.add(new PartSpec("minecraft:redstone_lamp[lit=false]",
                new Matrix4f().translate(0.65f, 0.5f, 0.4f).scale(0.15f, 0.15f, 0.15f),
                List.of(TAG, LAMP_TAG)));
        p.add(part("minecraft:end_rod", new Matrix4f().translate(0.2f, 0.5f, 0.45f).rotateZ(-0.3f).scale(1f, 0.9f, 1f)));
        return p;
    }

    private static PartSpec part(String block, Matrix4f m) {
        return new PartSpec(block, m, List.of(TAG));
    }
}
