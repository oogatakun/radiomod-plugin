package com.oogata.radioplugin.radio;

import org.joml.Matrix4f;

/**
 * ラジオの向き（90度単位）の計算。向き q は 0 = 南（+Z）、1 = 東、2 = 北、3 = 西。
 * モデルの前面は +Z 側にあるものとして扱う。
 */
public final class Orientation {

    private Orientation() {
    }

    /** ラジオの前面が (dx, dz) の方向を向く q。dx, dz は、ラジオから見た相手の位置。 */
    public static int quarterTurnsToward(double dx, double dz) {
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz >= 0 ? 0 : 2;
        }
        return dx > 0 ? 1 : 3;
    }

    public static int frontX(int q) {
        return new int[] {0, 1, 0, -1}[Math.floorMod(q, 4)];
    }

    public static int frontZ(int q) {
        return new int[] {1, 0, -1, 0}[Math.floorMod(q, 4)];
    }

    /** ラジオの前に立って、ラジオを見るときのプレイヤーの yaw。 */
    public static float yawLookingAtFront(int q) {
        return (float) Math.toDegrees(Math.atan2(frontX(q), -frontZ(q)));
    }

    /** m に、中心 (cx, cz) まわりの Y 軸回転（q × 90 度）を左からかけた行列。 */
    public static Matrix4f rotateAboutCenter(Matrix4f m, int q, float cx, float cz) {
        Matrix4f out = new Matrix4f();
        if (Math.floorMod(q, 4) == 0) {
            return out.set(m);
        }
        return out.translate(cx, 0f, cz)
                .rotateY((float) Math.toRadians(90.0 * Math.floorMod(q, 4)))
                .translate(-cx, 0f, -cz)
                .mul(m);
    }
}
