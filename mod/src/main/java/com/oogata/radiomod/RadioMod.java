package com.oogata.radiomod;

import com.mojang.logging.LogUtils;
import com.oogata.radiomod.net.RadioNet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

@Mod(RadioMod.MOD_ID)
public final class RadioMod {

    public static final String MOD_ID = "radiomod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RadioMod() {
        // クライアントだけに入れる mod。サーバーに mod がなくても接続できるようにする。
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, server) -> true));
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
            RadioNet.init();
        }
    }
}
