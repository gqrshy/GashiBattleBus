package com.github.gashiBattleBus;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Phantom;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class GashiBattleBus extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("BattleBusPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BattleBusPlugin has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("battlebus")) {
            if (args.length != 7) {
                sender.sendMessage("Usage: /battlebus x1 y1 z1 x2 y2 z2 time");
                return true;
            }

            try {
                double x1 = Double.parseDouble(args[0]);
                double y1 = Double.parseDouble(args[1]);
                double z1 = Double.parseDouble(args[2]);
                double x2 = Double.parseDouble(args[3]);
                double y2 = Double.parseDouble(args[4]);
                double z2 = Double.parseDouble(args[5]);
                int time = Integer.parseInt(args[6]);

                if (sender instanceof Player player) {
                    startBattleBus(player, x1, y1, z1, x2, y2, z2, time);
                } else {
                    sender.sendMessage("This command can only be run by a player.");
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("Invalid number format.");
            }

            return true;
        }
        return false;
    }

    private void startBattleBus(Player player, double x1, double y1, double z1, double x2, double y2, double z2, int time) {
        Phantom phantom = (Phantom) player.getWorld().spawnEntity(player.getLocation(), EntityType.PHANTOM);
        phantom.addPassenger(player);

        Vector direction = new Vector(x2 - x1, y2 - y1, z2 - z1).normalize().multiply(new Vector(x2 - x1, y2 - y1, z2 - z1).length() / time);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= time * 20 || phantom.isDead()) {
                    phantom.eject();
                    phantom.remove();
                    this.cancel();
                    return;
                }

                phantom.setVelocity(direction);

                if (player.isSneaking()) {
                    phantom.eject();
                    phantom.remove();
                    this.cancel();
                }

                ticks++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }
}
