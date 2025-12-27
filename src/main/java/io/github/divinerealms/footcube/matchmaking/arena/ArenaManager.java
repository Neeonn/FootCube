package io.github.divinerealms.footcube.matchmaking.arena;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.utils.Logger;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ArenaManager {
  private final FCManager fcManager;
  private final Logger logger;
  private final Server server;
  private final List<Arena> arenas = new ArrayList<>();
  private final ConfigManager configManager;
  private final FileConfiguration arenaConfig;

  @Setter private Map<Player, ArenaSetup> setupWizards = new HashMap<>();

  public ArenaManager(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.server = fcManager.getPlugin().getServer();
    this.configManager = fcManager.getConfigManager();
    this.arenaConfig = configManager.getConfig("arenas.yml");
    loadArenas();
  }

  public void reloadArenas() {
    arenas.clear();
    configManager.reloadConfig("arenas.yml");
    loadArenas();
    logger.info("&a✔ &2Arenas reloaded successfully! Total arenas: &e" + arenas.size());
  }

  private void loadArenas() {
    if (arenaConfig == null) { logger.info("&carenas.yml not found!"); return; }

    World world = server.getWorld(arenaConfig.getString("arenas.world", "world"));
    if (world == null) { logger.info("&cWorld for arenas not found!"); return; }

    for (String type : new String[]{"2v2", "3v3", "4v4"}) {
      int amount = arenaConfig.getInt("arenas." + type + ".amount", 0);
      for (int i = 1; i <= amount; i++) {
        String bluePath = "arenas." + type + "." + i + ".blue.";
        String redPath = "arenas." + type + "." + i + ".red.";

        Location blueSpawn = getLocation(arenaConfig, world, bluePath);
        Location redSpawn = getLocation(arenaConfig, world, redPath);
        addArena(Integer.parseInt(type.substring(0, 1)), blueSpawn, redSpawn);
      }
    }
  }

  private Location getLocation(FileConfiguration config, World world, String path) {
    Location location = new Location(world, config.getDouble(path + "x"), config.getDouble(path + "y"), config.getDouble(path + "z"));
    location.setPitch((float) config.getDouble(path + "pitch"));
    location.setYaw((float) config.getDouble(path + "yaw"));
    return location;
  }

  public void createArena(int type, Location blueSpawn, Location redSpawn) {
    blueSpawn = normalizeLocation(blueSpawn);
    redSpawn = normalizeLocation(redSpawn);

    boolean isXAxis = Math.abs(blueSpawn.getX() - redSpawn.getX()) > Math.abs(blueSpawn.getZ() - redSpawn.getZ());
    boolean blueIsLess = isXAxis ? blueSpawn.getX() < redSpawn.getX() : blueSpawn.getZ() < redSpawn.getZ();

    blueSpawn.setYaw(isXAxis ? (blueIsLess ? 90.0F : -90.0F) : (blueIsLess ? 180.0F : 0.0F));
    redSpawn.setYaw(isXAxis ? (blueIsLess ? -90.0F : 90.0F) : (blueIsLess ? 0.0F : 180.0F));

    blueSpawn.setPitch(0.0F);
    redSpawn.setPitch(0.0F);

    String typeString = type + "v" + type;
    int index = arenaConfig.getInt("arenas." + typeString + ".amount", 0) + 1;

    arenaConfig.set("arenas." + typeString + ".amount", index);
    arenaConfig.set("arenas.world", blueSpawn.getWorld().getName());

    String bluePath = "arenas." + typeString + "." + index + ".blue.";
    String redPath = "arenas." + typeString + "." + index + ".red.";

    saveLocation(bluePath, blueSpawn);
    saveLocation(redPath, redSpawn);

    configManager.saveConfig("arenas.yml");
    addArena(type, blueSpawn, redSpawn);
  }

  private Location normalizeLocation(Location location) {
    Location normalized = location.clone();
    normalized.setX(Math.round(location.getX() - 0.5) + 0.5);
    normalized.setY(Math.floor(location.getY()));
    normalized.setZ(Math.round(location.getZ() - 0.5) + 0.5);
    return normalized;
  }

  private void addArena(int type, Location blue, Location red) {
    Location center = new Location(blue.getWorld(), (blue.getX() + red.getX()) / 2.0, (blue.getY() + red.getY()) / 2.0 + 2.0, (blue.getZ() + red.getZ()) / 2.0);
    boolean isXAxis = Math.abs(blue.getX() - red.getX()) > Math.abs(blue.getZ() - red.getZ());
    boolean redIsGreater = isXAxis ? red.getX() > blue.getX() : red.getZ() > blue.getZ();
    int id = arenas.size() + 1;
    arenas.add(new Arena(id, type, blue, red, center, isXAxis, redIsGreater));
  }

  private void saveLocation(String path, Location location) {
    arenaConfig.set(path + "x", location.getX());
    arenaConfig.set(path + "y", location.getY());
    arenaConfig.set(path + "z", location.getZ());
    arenaConfig.set(path + "pitch", location.getPitch());
    arenaConfig.set(path + "yaw", location.getYaw());
  }

  public void clearArenas() {
    arenaConfig.set("arenas", null);
    configManager.saveConfig("arenas.yml");
    arenas.clear();
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