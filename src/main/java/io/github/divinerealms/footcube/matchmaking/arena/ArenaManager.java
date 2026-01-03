package io.github.divinerealms.footcube.matchmaking.arena;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.utils.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

@Getter
public class ArenaManager {

  private final FCManager fcManager;
  private final Logger logger;
  private final Server server;
  private final List<Arena> arenas = new ArrayList<>();
  private final ConfigManager configManager;

  @Setter
  private Map<Player, ArenaSetup> setupWizards = new HashMap<>();

  public ArenaManager(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.server = fcManager.getPlugin().getServer();
    this.configManager = fcManager.getConfigManager();
    loadArenas();
  }

  public void reloadArenas() {
    arenas.clear();
    configManager.reloadConfig("arenas.yml");
    loadArenas();
    logger.info("&a✔ &2Arenas reloaded successfully! Total arenas: &e" + arenas.size());
  }

  private void loadArenas() {
    FileConfiguration config = configManager.getConfig("arenas.yml");

    if (config == null) {
      logger.info("&carenas.yml not found!");
      return;
    }

    World world = server.getWorld(config.getString("arenas.world", "world"));
    if (world == null) {
      logger.info("&cWorld for arenas not found!");
      return;
    }

    for (String type : new String[]{"2v2", "3v3", "4v4"}) {
      int amount = config.getInt("arenas." + type + ".amount", 0);
      for (int i = 1; i <= amount; i++) {
        String bluePath = "arenas." + type + "." + i + ".blue.";
        String redPath = "arenas." + type + "." + i + ".red.";

        Location blueSpawn = getLocation(config, world, bluePath);
        Location redSpawn = getLocation(config, world, redPath);
        addArena(Integer.parseInt(type.substring(0, 1)), blueSpawn, redSpawn);
      }
    }
  }

  private Location getLocation(FileConfiguration config, World world, String path) {
    Location location = new Location(world, config.getDouble(path + "x"),
        config.getDouble(path + "y"), config.getDouble(path + "z"));
    location.setPitch((float) config.getDouble(path + "pitch"));
    location.setYaw((float) config.getDouble(path + "yaw"));
    return location;
  }

  public void createArena(int type, Location blueSpawn, Location redSpawn) {
    blueSpawn = normalizeLocation(blueSpawn);
    redSpawn = normalizeLocation(redSpawn);

    blueSpawn.setYaw(normalizeYaw(blueSpawn.getYaw()));
    blueSpawn.setPitch(0.0F);

    redSpawn.setYaw(normalizeYaw(redSpawn.getYaw()));
    redSpawn.setPitch(0.0F);

    FileConfiguration config = configManager.getConfig("arenas.yml");

    String typeString = type + "v" + type;
    int index = config.getInt("arenas." + typeString + ".amount", 0) + 1;

    config.set("arenas." + typeString + ".amount", index);
    config.set("arenas.world", blueSpawn.getWorld().getName());

    String bluePath = "arenas." + typeString + "." + index + ".blue.";
    String redPath = "arenas." + typeString + "." + index + ".red.";

    saveLocation(config, bluePath, blueSpawn);
    saveLocation(config, redPath, redSpawn);

    configManager.saveConfig("arenas.yml");
    addArena(type, blueSpawn, redSpawn);

    Arena newArena = arenas.get(arenas.size() - 1);

    logger.info("&a✔ &2Created " + typeString + " arena (ID: " + newArena.getId() + ") at " +
        formatLocation(blueSpawn) + " and " + formatLocation(redSpawn));
  }

  private Location normalizeLocation(Location location) {
    Location normalized = location.clone();
    normalized.setX(Math.round(location.getX() - 0.5) + 0.5);
    normalized.setY(Math.floor(location.getY()));
    normalized.setZ(Math.round(location.getZ() - 0.5) + 0.5);
    return normalized;
  }

  private float normalizeYaw(float yaw) {
    while (yaw > 180) {
      yaw -= 360;
    }

    while (yaw < -180) {
      yaw += 360;
    }

    // Round to nearest cardinal direction
    if (yaw >= -45 && yaw < 45) {
      return 0.0F; // South
    } else if (yaw >= 45 && yaw < 135) {
      return 90.0F; // West
    } else if (yaw >= 135 || yaw < -135) {
      return 180.0F; // North
    } else {
      return -90.0F; // East
    }
  }

  private void addArena(int type, Location blue, Location red) {
    Location center = new Location(blue.getWorld(), (blue.getX() + red.getX()) / 2.0,
        (blue.getY() + red.getY()) / 2.0 + 2.0, (blue.getZ() + red.getZ()) / 2.0);

    boolean isXAxis = Math.abs(blue.getX() - red.getX()) > Math.abs(blue.getZ() - red.getZ());
    boolean redIsGreater = isXAxis ? red.getX() > blue.getX() : red.getZ() > blue.getZ();

    int id = arenas.size() + 1;
    arenas.add(new Arena(id, type, blue, red, center, isXAxis, redIsGreater));
  }

  private void saveLocation(FileConfiguration config, String path, Location location) {
    config.set(path + "x", location.getX());
    config.set(path + "y", location.getY());
    config.set(path + "z", location.getZ());
    config.set(path + "pitch", location.getPitch());
    config.set(path + "yaw", location.getYaw());
  }

  public void clearArenas() {
    FileConfiguration config = configManager.getConfig("arenas.yml");

    config.set("arenas", null);
    configManager.saveConfig("arenas.yml");

    arenas.clear();
    logger.info("&a✔ &2All arenas cleared!");
  }

  public void clearArenaType(int type) {
    String typeString = type + "v" + type;
    FileConfiguration config = configManager.getConfig("arenas.yml");

    config.set("arenas." + typeString, null);
    configManager.saveConfig("arenas.yml");

    arenas.removeIf(arena -> arena.getType() == type);

    reassignArenaIds();

    logger.info("&a✔ &2Cleared all " + typeString + " arenas!");
  }

  private void reassignArenaIds() {
    List<Arena> newArenas = new ArrayList<>();
    for (int i = 0; i < arenas.size(); i++) {
      Arena oldArena = arenas.get(i);
      Arena newArena = new Arena(
          i + 1,
          oldArena.getType(),
          oldArena.getBlueSpawn(),
          oldArena.getRedSpawn(),
          oldArena.getCenter(),
          oldArena.isXAxis(),
          oldArena.isRedIsGreater()
      );
      newArenas.add(newArena);
    }
    arenas.clear();
    arenas.addAll(newArenas);
  }

  private String formatLocation(Location location) {
    return String.format("%.1f, %.1f, %.1f (yaw: %.0f, pitch: %.0f)", location.getX(),
        location.getY(), location.getZ(), location.getYaw(), location.getPitch());
  }

  @Getter
  @Setter
  public static class ArenaSetup {

    private int type;
    private Location blueSpawn;

    public ArenaSetup(int type) {
      this.type = type;
    }
  }
}