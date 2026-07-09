package com.yourname.addon;

import com.yourname.addon.modules.EndShipHunter;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class YourAddon extends MeteorAddon {

    @Override
    public void onInitialize() {
        Modules.get().add(new EndShipHunter());
    }

    @Override
    public String getPackage() {
        return "com.yourname.addon";
    }
}
