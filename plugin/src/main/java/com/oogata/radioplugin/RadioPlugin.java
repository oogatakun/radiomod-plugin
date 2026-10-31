package com.oogata.radioplugin;

import com.oogata.radioplugin.command.StubCommand;
import com.oogata.radioplugin.net.NetService;
import com.oogata.radioplugin.player.PlayerSoundModule;
import org.bukkit.plugin.java.JavaPlugin;

public final class RadioPlugin extends JavaPlugin {

    private NetService net;
    private PlayerSoundModule ps;

    @Override
    public void onEnable() {
        net = new NetService(this);
        net.register();

        StubCommand stub = new StubCommand();
        for (String name : new String[] {"radiodisplay", "radioplayer"}) {
            getCommand(name).setExecutor(stub);
        }
        ps = PlayerSoundModule.enable(this, net);
    }

    @Override
    public void onDisable() {
        if (ps != null) {
            ps.disable();
        }
        if (net != null) {
            net.unregister();
        }
    }
}
