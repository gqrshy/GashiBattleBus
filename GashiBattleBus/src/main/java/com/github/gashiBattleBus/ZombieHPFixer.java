package com.github.gashiBattleBus;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ZombieHPFixer extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onZombieSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Zombie) {
            Zombie zombie = (Zombie) entity;
            zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(7.0);
            zombie.setHealth(7.0);
        }
    }
}