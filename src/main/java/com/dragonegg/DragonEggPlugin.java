package com.dragonegg;

import org.bukkit.plugin.java.JavaPlugin;

public class DragonEggPlugin extends JavaPlugin {

    private DragonEggListener listener;

    @Override
    public void onEnable() {
        listener = new DragonEggListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getScheduler().runTaskTimer(this, listener::tickGlowCheck, 0L, 10L);
        getLogger().info("DragonEggPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.removeAllGlow();
        }
        getLogger().info("DragonEggPlugin disabled!");
    }
}
