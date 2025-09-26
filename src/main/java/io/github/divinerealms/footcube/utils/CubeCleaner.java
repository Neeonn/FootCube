package io.github.divinerealms.footcube.utils;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Physics;
import io.github.divinerealms.footcube.managers.ConfigManager;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Slime;

import java.util.Collection;

public class CubeCleaner {
  private final FileConfiguration practice;
  private final Physics physics;

  @Getter public boolean empty;
  @Getter public int amount = 0;
  @Getter public int removeInterval;

  public CubeCleaner(FCManager fcManager) {
    ConfigManager configManager = fcManager.getConfigManager();
    this.practice = configManager.getConfig("practice.yml");

    Logger logger = fcManager.getLogger();
    this.physics = fcManager.getPhysics();

    if (!practiceAreasSet()) {
      logger.info(Lang.PRACTICE_AREAS_EMPTY.replace(null));
      return;
    }

    FileConfiguration config = configManager.getConfig("config.yml");
    int minutes = config.getInt("clear-cube-interval", 5);
    this.removeInterval = minutes * 60 * 20;
  }

  public void clearCubes() {
    this.empty = true;
    this.amount = 0;

    for (String locationName : practice.getConfigurationSection("practice-areas").getKeys(false)) {
      Location location = (Location) practice.get("practice-areas." + locationName);
      Collection<Entity> nearbyEntities = location.getWorld().getNearbyEntities(location, 100, 100, 100);

      for (Entity entity : nearbyEntities) {
        if (entity instanceof Slime) {
          this.amount++;
          this.empty = false;
          if (physics.getCubes() != null) physics.getCubes().remove(entity);
          if (physics.getPracticeCubes() != null) physics.getPracticeCubes().remove(entity);
          entity.remove();
        }
      }
    }
  }

  public boolean practiceAreasSet() {
    return practice.contains("practice-areas");
  }
}
