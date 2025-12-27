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
      fcManager.getLogger().info("&a✔ &2Successfully enabled &bFootCube v" + getDescription().getVersion() + "&2!");
    } catch (Exception exception) {
      getLogger().log(Level.SEVERE, "Failed to initialize FootCube: " + exception.getMessage(), exception);
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  public void onDisable() {
    if (fcManager == null) return;

    try {
      fcManager.setDisabling(true);
      fcManager.getCubeCleaner().clearCubes();
      fcManager.getPhysicsSystem().removeCubes();
      fcManager.getTaskManager().stopAll();
      if (fcManager.getMatchManager() != null) fcManager.getMatchManager().forceLeaveAllPlayers();
      fcManager.saveAll();
      fcManager.cleanup();
      getServer().getScheduler().cancelTasks(this);
      fcManager.getListenerManager().unregisterAll();
      fcManager.getCachedPlayers().clear();
      fcManager.getLogger().info("&c✘ &4Successfully disabled.");
    } catch (Exception exception) {
      getLogger().log(Level.SEVERE, "Error during plugin shutdown: " + exception.getMessage(), exception);
    }
  }
}
