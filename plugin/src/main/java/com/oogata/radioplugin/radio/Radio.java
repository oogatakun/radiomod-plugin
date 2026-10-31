package com.oogata.radioplugin.radio;

import java.util.UUID;

/** 設置済みのラジオ1台ぶんの保存データ。座標は設置位置（選んだブロックの上面中央）。 */
public final class Radio {

    private final UUID id;
    private String name;
    private String world;
    private double x;
    private double y;
    private double z;
    /** モデルの向き。0 = 南（+Z）向き、以降 90 度ずつ（1 = 東、2 = 北、3 = 西）。 */
    private int rotation;
    private boolean keep;
    /** モデルのパーツ数（名前表示は含まない）。欠けの検出に使う。 */
    private int partCount;
    /** モデルの高さ（名前表示の位置に使う）。 */
    private double height = 1.0;

    public Radio(UUID id, String name, String world, double x, double y, double z, int rotation) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = Math.floorMod(rotation, 4);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public void setPosition(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int rotation() {
        return rotation;
    }

    public boolean keep() {
        return keep;
    }

    public void setKeep(boolean keep) {
        this.keep = keep;
    }

    public int partCount() {
        return partCount;
    }

    public void setPartCount(int partCount) {
        this.partCount = partCount;
    }

    public double height() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    /** 設置位置が入っているチャンクの X 座標。 */
    public int chunkX() {
        return (int) Math.floor(x) >> 4;
    }

    public int chunkZ() {
        return (int) Math.floor(z) >> 4;
    }
}
