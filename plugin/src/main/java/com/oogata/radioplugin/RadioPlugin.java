package com.oogata.radioplugin;

import com.oogata.radioplugin.command.StubCommand;
import com.oogata.radioplugin.command.RadioDisplayCommand;
import com.oogata.radioplugin.net.NetService;
import com.oogata.radioplugin.player.PlayerSoundModule;
import com.oogata.radioplugin.radio.RadioManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class RadioPlugin extends JavaPlugin {

    private NetService net;
    private RadioManager radios;
    private PlayerSoundModule ps;

    @Override
    public void onEnable() {
        net = new NetService(this);
        net.register();

        StubCommand stub = new StubCommand();
        for (String name : new String[] {"radiodisplay", "radioplayer"}) {
            getCommand(name).setExecutor(stub);
        }

        radios = new RadioManager(this);
        radios.enable();
        RadioDisplayCommand rd = new RadioDisplayCommand(radios);
        getCommand("radiodisplay").setExecutor(rd);
        getCommand("radiodisplay").setTabCompleter(rd);
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
