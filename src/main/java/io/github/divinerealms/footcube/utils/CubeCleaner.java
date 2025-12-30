package io.github.divinerealms.footcube.utils;

import static io.github.divinerealms.footcube.configs.Lang.PRACTICE_AREAS_EMPTY;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Slime;

public class CubeCleaner {

  private final FileConfiguration practice;
  private final PhysicsData physicsData;

  private final List<PracticeArea> practiceAreas;

  @Getter
  public boolean empty;
  @Getter
  public int amount = 0;
  @Getter
  public int removeInterval;

  public CubeCleaner(FCManager fcManager) {
    ConfigManager configManager = fcManager.getConfigManager();
    this.practice = configManager.getConfig("practice.yml");
    this.physicsData = fcManager.getPhysicsData();
    this.practiceAreas = new ArrayList<>();
    Logger logger = fcManager.getLogger();

    FileConfiguration config = configManager.getConfig("config.yml");
    int minutes = config.getInt("clear-cube-interval", 5);
    this.removeInterval = minutes * 60 * 20;

    if (noPracticeAreasSet()) {
      logger.info(PRACTICE_AREAS_EMPTY);
      return;
    }
    loadPracticeAreas();
  }

  private void loadPracticeAreas() {
    practiceAreas.clear();

    if (!practice.contains("practice-areas")) {
      return;
    }
    if (practice.getConfigurationSection("practice-areas") == null) {
      return;
    }

    for (String locationName : practice.getConfigurationSection("practice-areas").getKeys(false)) {
      Location location = (Location) practice.get("practice-areas." + locationName);
      if (location == null) {
        continue;
      }
      practiceAreas.add(new PracticeArea(location, 100));
    }
  }

  public void clearCubes() {
    this.empty = true;
    this.amount = 0;

    if (practiceAreas.isEmpty()) {
      return;
    }
    if (physicsData == null) {
      return;
    }
    if (physicsData.getCubes() == null) {
      return;
    }
    if (physicsData.getCubes().isEmpty()) {
      return;
    }

    for (Slime cube : physicsData.getCubes()) {
      if (cube == null) {
        continue;
      }
      if (cube.isDead()) {
        continue;
      }

      Location cubeLocation = cube.getLocation();
      if (cubeLocation == null) {
        continue;
      }

      for (PracticeArea area : practiceAreas) {
        if (area.contains(cubeLocation)) {
          this.amount++;
          this.empty = false;
          cube.setHealth(0);
          break;
        }
      }
    }
  }

  public boolean noPracticeAreasSet() {
    return !practice.contains("practice-areas");
  }

  private static class PracticeArea {

    private final Location center;
    private final double radiusSquared;

    PracticeArea(Location center, double radius) {
      this.center = center;
      this.radiusSquared = radius * radius;
    }

    boolean contains(Location location) {
      if (location.getWorld() == null || center.getWorld() == null) {
        return false;
      }
      if (!location.getWorld().equals(center.getWorld())) {
        return false;
      }

      double radius = Math.sqrt(radiusSquared);
      if (Math.abs(location.getX() - center.getX()) > radius) {
        return false;
      }
      if (Math.abs(location.getY() - center.getY()) > radius) {
        return false;
      }
      if (Math.abs(location.getZ() - center.getZ()) > radius) {
        return false;
      }

      double distanceSquared = location.distanceSquared(center);
      return distanceSquared <= radiusSquared;
    }
  }
}
