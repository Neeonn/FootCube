package io.github.divinerealms.footcube;

import io.github.divinerealms.footcube.core.FCManager;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public class FootCube extends JavaPlugin {

  private FCManager fcManager;

  @Override
  public void onEnable() {
    try {
      this.fcManager = new FCManager(this);
      fcManager.setEnabling(true);
      fcManager.getLogger()
          .info("&a✔ &2Successfully enabled &bFootCube v" + getDescription().getVersion() + "&2!");
    } catch (Exception exception) {
      getLogger().log(Level.SEVERE, "Failed to initialize FootCube: " + exception.getMessage(),
          exception);
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  public void onDisable() {
    if (fcManager == null) {
      return;
    }

    try {
      fcManager.setDisabling(true);
      if (fcManager.getMatchManager() != null) {
        fcManager.getMatchManager().forceLeaveAllPlayers();
      }
      fcManager.getPhysicsSystem().removeCubes();
      fcManager.getTaskManager().stopAll();
      fcManager.saveAll();
      fcManager.cleanup();
      getServer().getScheduler().cancelTasks(this);
      fcManager.getLogger().info("&c✘ &4Successfully disabled.");
    } catch (Exception exception) {
      getLogger().log(Level.SEVERE, "Error during plugin shutdown: " + exception.getMessage(),
          exception);
    }
  }
}
