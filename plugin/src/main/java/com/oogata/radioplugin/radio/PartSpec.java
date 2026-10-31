package com.oogata.radioplugin.radio;

import java.util.List;
import org.joml.Matrix4f;

/** ラジオのパーツ1つぶん（BlockDisplay）の設計。block は {@code minecraft:name[prop=value]} の形。 */
public record PartSpec(String block, Matrix4f matrix, List<String> tags) {
}
