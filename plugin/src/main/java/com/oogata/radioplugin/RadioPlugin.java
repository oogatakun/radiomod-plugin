package com.oogata.radioplugin;

import com.oogata.radioplugin.command.StubCommand;
import com.oogata.radioplugin.net.NetService;
import org.bukkit.plugin.java.JavaPlugin;

public final class RadioPlugin extends JavaPlugin {

    private NetService net;

    @Override
    public void onEnable() {
        net = new NetService(this);
        net.register();

        StubCommand stub = new StubCommand();
        for (String name : new String[] {"playersound", "radiodisplay", "radioplayer"}) {
            getCommand(name).setExecutor(stub);
        }
    }

    @Override
    public void onDisable() {
        if (net != null) {
            net.unregister();
        }
    }
}
