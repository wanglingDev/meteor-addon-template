package com.yourname.addon;

import com.yourname.addon.modules.EndShipHunter;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.item.Items;

public class YourAddon extends MeteorAddon {

    // Change display name and item icon as you like
    public static final Category CATEGORY = new Category("EndHunter", Items.ENDER_EYE.getDefaultStack());

    @Override
    public void onInitialize() {
        Modules.get().add(new EndShipHunter());
    }

    @Override
    public String getPackage() {
        return "com.yourname.addon";
    }
}
