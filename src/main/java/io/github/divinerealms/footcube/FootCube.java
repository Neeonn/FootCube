package io.github.divinerealms.footcube;

import io.github.divinerealms.footcube.core.FCManager;
import org.bukkit.plugin.java.JavaPlugin;

public class FootCube extends JavaPlugin {
  private FCManager fcManager;

  @Override
  public void onEnable() {
    try {
      this.fcManager = new FCManager(this);
      fcManager.getLogger().info("&aSuccessfully enabled.");
    } catch (Exception exception) {
      getLogger().severe("Failed to initialize FootCube: " + exception.getMessage());
      exception.printStackTrace();
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  public void onDisable() {
    if (fcManager != null) {
      fcManager.saveAll();
      if (fcManager.getOrg() != null) fcManager.getOrg().cleanup();
      fcManager.getPhysics().cleanup();
      fcManager.shutdownTasks();
      getServer().getScheduler().cancelTasks(this);
      fcManager.getListenerManager().unregisterAll();
      fcManager.getLogger().info("&cSuccessfully disabled.");
    }
  }
}