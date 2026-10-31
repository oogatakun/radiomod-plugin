package com.oogata.radioplugin;

import com.oogata.radioplugin.command.StubCommand;
import com.oogata.radioplugin.command.RadioDisplayCommand;
import com.oogata.radioplugin.net.NetService;
import com.oogata.radioplugin.player.PlayerSoundModule;
import com.oogata.radioplugin.radio.RadioManager;
import com.oogata.radioplugin.radio.RadioPlayerModule;
import org.bukkit.plugin.java.JavaPlugin;

public final class RadioPlugin extends JavaPlugin {

    private NetService net;
    private RadioManager radios;
    private PlayerSoundModule ps;
    private RadioPlayerModule rp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledModels();
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
        rp = RadioPlayerModule.enable(this, net, radios, ps.resolver());
    }

    @Override
    public void onDisable() {
        if (rp != null) {
            rp.disable();
        }
        if (ps != null) {
            ps.disable();
        }
        if (net != null) {
            net.unregister();
        }
    }

    /** jar に同梱したモデルを、plugins/RadioPlugin/models/ に出す。すでにあるファイル(手で直したものなど)は上書きしない。 */
    private void saveBundledModels() {
        for (String name : new String[] {"luxuryboombox"}) {
            String path = "models/" + name + ".mcfunction";
            if (!new java.io.File(getDataFolder(), path).exists()) {
                saveResource(path, false);
            }
        }
    }
}
