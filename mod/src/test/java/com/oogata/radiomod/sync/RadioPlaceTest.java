package com.oogata.radiomod.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RadioPlaceTest {

    private static RadioPlace.ListenerPos at(String dim, double x, double y, double z) {
        return new RadioPlace.ListenerPos(dim, x, y, z);
    }

    @Test
    void 近くでは全開_範囲の端で0_間は直線的に下がる() {
        assertEquals(1.0, RadioPlace.gainAt(0, 24), 1e-9);
        assertEquals(1.0, RadioPlace.gainAt(RadioPlace.FULL_VOLUME_DISTANCE, 24), 1e-9);
        assertEquals(0.5, RadioPlace.gainAt(13, 24), 1e-9); // 2〜24 の真ん中
        assertEquals(0.0, RadioPlace.gainAt(24, 24), 1e-9);
        assertEquals(0.0, RadioPlace.gainAt(100, 24), 1e-9);
    }

    @Test
    void 距離が離れるほど音は小さくなる() {
        double prev = 2;
        for (double d = 0; d <= 30; d += 1) {
            double g = RadioPlace.gainAt(d, 24);
            assertTrue(g <= prev + 1e-12);
            prev = g;
        }
    }

    @Test
    void 距離と音量は聞く側の位置から計算する() {
        RadioPlace.ListenerPos[] listener = {at("minecraft:overworld", 3, 64, 4)};
        RadioPlace place = new RadioPlace(() -> listener[0]);
        place.set("minecraft:overworld", 0, 64, 0, 24);
        assertEquals(5.0, place.distance(), 1e-9);
        assertEquals(RadioPlace.gainAt(5, 24), place.gain(), 1e-9);
    }

    @Test
    void 別の次元とワールド外では距離がNaNで音量は0() {
        RadioPlace.ListenerPos[] listener = {at("minecraft:the_nether", 0, 64, 0)};
        RadioPlace place = new RadioPlace(() -> listener[0]);
        place.set("minecraft:overworld", 0, 64, 0, 24);
        assertTrue(Double.isNaN(place.distance()));
        assertEquals(0.0, place.gain());
        listener[0] = null;
        assertTrue(Double.isNaN(place.distance()));
        assertEquals(0.0, place.gain());
    }

    @Test
    void MOVEで座標が変わると距離も変わる() {
        RadioPlace place = new RadioPlace(() -> at("minecraft:overworld", 0, 64, 0));
        place.set("minecraft:overworld", 10, 64, 0, 24);
        assertEquals(10.0, place.distance(), 1e-9);
        place.move("minecraft:overworld", 0, 64, 20);
        assertEquals(20.0, place.distance(), 1e-9);
        assertEquals(24f, place.range());
    }
}
