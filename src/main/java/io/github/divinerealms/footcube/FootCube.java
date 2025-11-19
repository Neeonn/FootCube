package io.github.divinerealms.footcube;

import io.github.divinerealms.footcube.core.FCManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class FootCube extends JavaPlugin {
  private FCManager fcManager;

  @Override
  public void onEnable() {
    try {
      this.fcManager = new FCManager(this);
      fcManager.setEnabling(true);
      fcManager.getLogger().info("&aSuccessfully enabled.");
    } catch (Exception exception) {
      getLogger().log(Level.SEVERE, "Failed to initialize FootCube", exception);
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  public void onDisable() {
    if (fcManager != null) {
      fcManager.setDisabling(true);
      if (fcManager.getMatchManager() != null) fcManager.getMatchManager().forceLeaveAllPlayers();
      fcManager.saveAll();
      fcManager.getPhysicsEngine().cleanup();
      fcManager.shutdownTasks();
      getServer().getScheduler().cancelTasks(this);
      fcManager.getListenerManager().unregisterAll();
      fcManager.getCachedPlayers().clear();
      fcManager.getLogger().info("&cSuccessfully disabled.");
    }
  }
}
