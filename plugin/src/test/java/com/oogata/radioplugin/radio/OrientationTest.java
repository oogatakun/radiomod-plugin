package com.oogata.radioplugin.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class OrientationTest {

    @Test
    void 相手のいる方向に向く() {
        assertEquals(0, Orientation.quarterTurnsToward(0, 5));
        assertEquals(1, Orientation.quarterTurnsToward(5, 1));
        assertEquals(2, Orientation.quarterTurnsToward(1, -5));
        assertEquals(3, Orientation.quarterTurnsToward(-5, 0));
        assertEquals(0, Orientation.quarterTurnsToward(0, 0));
    }

    @Test
    void 前面の向きと回転が一致する() {
        // 前面は +Z。q 回転後の前面が frontX/frontZ になる
        for (int q = 0; q < 4; q++) {
            Matrix4f m = Orientation.rotateAboutCenter(new Matrix4f(), q, 0.5f, 0.5f);
            Vector3f front = m.transformDirection(new Vector3f(0, 0, 1));
            assertEquals(Orientation.frontX(q), Math.round(front.x), "q=" + q);
            assertEquals(Orientation.frontZ(q), Math.round(front.z), "q=" + q);
        }
    }

    @Test
    void 回転しても1ブロックの箱は同じ範囲に収まる() {
        for (int q = 0; q < 4; q++) {
            Matrix4f m = Orientation.rotateAboutCenter(new Matrix4f(), q, 0.5f, 0.5f);
            for (int i = 0; i < 8; i++) {
                Vector3f v = m.transformPosition(i & 1, (i >> 1) & 1, (i >> 2) & 1, new Vector3f());
                assertEquals(true, v.x > -1e-5f && v.x < 1 + 1e-5f && v.z > -1e-5f && v.z < 1 + 1e-5f, "q=" + q + " " + v);
            }
        }
    }

    @Test
    void 回転は元の行列の変形のあとにかかる() {
        Matrix4f m = new Matrix4f().translate(0.25f, 0f, 0.75f);
        Vector3f p = Orientation.rotateAboutCenter(m, 1, 0.5f, 0.5f).transformPosition(new Vector3f());
        // (0.25, 0.75) を中心 (0.5, 0.5) まわりに 90 度（+Z が +X へ向かう向き）回す
        assertEquals(0.75f, p.x, 1e-5f);
        assertEquals(0.75f, p.z, 1e-5f);
    }

    @Test
    void テレポート先の向きはラジオの前面を見る() {
        assertEquals(180f, Orientation.yawLookingAtFront(0), 1e-4f);
        assertEquals(90f, Orientation.yawLookingAtFront(1), 1e-4f);
        assertEquals(0f, Orientation.yawLookingAtFront(2), 1e-4f);
        assertEquals(-90f, Orientation.yawLookingAtFront(3), 1e-4f);
    }
}
