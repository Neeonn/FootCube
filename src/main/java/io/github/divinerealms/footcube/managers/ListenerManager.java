package io.github.divinerealms.footcube.managers;

import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.listeners.*;
import io.github.divinerealms.footcube.physics.listeners.*;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;

public class ListenerManager {
  private final FCManager fcManager;
  private final FootCube plugin;
  private final PluginManager pluginManager;

  public ListenerManager(FCManager fcManager) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.pluginManager = fcManager.getPlugin().getServer().getPluginManager();
  }

  public void registerAll() {
    unregisterAll();

    pluginManager.registerEvents(new ChunkCheckers(), plugin);
    pluginManager.registerEvents(new PlayerEvents(fcManager), plugin);
    pluginManager.registerEvents(new SignManipulation(fcManager), plugin);

    pluginManager.registerEvents(new CubeDamageListener(fcManager), plugin);
    pluginManager.registerEvents(new CubeKickListener(fcManager), plugin);
    pluginManager.registerEvents(new CubeTapListener(fcManager), plugin);
    pluginManager.registerEvents(new PlayerChargeListener(fcManager), plugin);
    pluginManager.registerEvents(new PlayerMovementListener(fcManager), plugin);

    fcManager.getLogger().info("&a✔ &2Registered &e5 &2listeners.");
  }

  public void unregisterAll() {
    HandlerList.unregisterAll(plugin);

    fcManager.getLogger().info("&a✔ &2Unregistered listeners.");
  }
}
