package io.github.divinerealms.footcube.managers;

import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.listeners.*;
import org.bukkit.Server;
import org.bukkit.event.HandlerList;

public class ListenerManager {
  private final FCManager fcManager;
  private final FootCube plugin;
  private final Server server;

  public ListenerManager(FCManager fcManager) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.server = fcManager.getPlugin().getServer();
  }

  public void registerAll() {
    unregisterAll();

    server.getPluginManager().registerEvents(new ChunkCheckers(), plugin);
    server.getPluginManager().registerEvents(new PlayerEvents(fcManager), plugin);
    server.getPluginManager().registerEvents(new SignManipulation(fcManager), plugin);
    server.getPluginManager().registerEvents(new BallEvents(fcManager), plugin);

    fcManager.getLogger().info("&a✔ &2Registered &e5 &2listeners.");
  }

  public void unregisterAll() {
    HandlerList.unregisterAll(plugin);

    fcManager.getLogger().info("&a✔ &2Unregistered listeners.");
  }
}
