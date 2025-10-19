package io.github.divinerealms.footcube.managers;

import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

public class PlayerDataManager {
  private final Plugin plugin;
  private final ConfigManager configManager;
  private final Logger logger;
  private final Map<String, PlayerData> playerCache = new ConcurrentHashMap<>();

  private final FileConfiguration uuidConfig;
  private final Map<String, String> uuidCache = new ConcurrentHashMap<>();
  private volatile boolean uuidsChanged = false;

  private final Queue<String> dataQueue = new ConcurrentLinkedQueue<>();
  private final Set<String> dataQueueSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private volatile boolean saveScheduled = false;

  public PlayerDataManager(FCManager fcManager) {
    this.plugin = fcManager.getPlugin();
    this.configManager = fcManager.getConfigManager();
    this.logger = fcManager.getLogger();
    configManager.createNewFile("player_uuids.yml", "Cache of player UUIDs");
    this.uuidConfig = configManager.getConfig("player_uuids.yml");

    for (String key : uuidConfig.getKeys(false)) {
      uuidCache.put(key, uuidConfig.getString(key));
    }
  }

  public PlayerData get(Player player) {
    uuidCache.computeIfAbsent(player.getName(), name -> {
      String uuid = player.getUniqueId().toString();
      uuidConfig.set(name, uuid);
      uuidsChanged = true;
      queueAdd(name);
      return uuid;
    });

    return playerCache.computeIfAbsent(player.getName(), name -> new PlayerData(name, configManager, this));
  }

  public PlayerData get(String playerName) {
    String uuid = uuidCache.get(playerName);
    if (uuid == null) return null;
    return playerCache.computeIfAbsent(playerName, name -> new PlayerData(name, configManager, this));
  }

  public UUID getUUID(String playerName) {
    String uuidString = uuidCache.get(playerName);
    if (uuidString == null) return null;
    return UUID.fromString(uuidString);
  }

  public void queueAdd(String playerName) {
    if (dataQueueSet.add(playerName)) {
      dataQueue.add(playerName);
      scheduleSave();
    }
  }

  public void unload(Player player) {
    queueAdd(player.getName());
    playerCache.remove(player.getName());
  }

  public void addDefaults(PlayerData playerData) {
    if (!playerData.has("wins")) playerData.set("wins", 0);
    if (!playerData.has("matches")) playerData.set("matches", 0);
    if (!playerData.has("ties")) playerData.set("ties", 0);
    if (!playerData.has("goals")) playerData.set("goals", 0);
    if (!playerData.has("winstreak")) playerData.set("winstreak", 0);
    if (!playerData.has("bestwinstreak")) playerData.set("bestwinstreak", 0);
  }

  public void saveQueue() {
    if (dataQueue.isEmpty() && !uuidsChanged) return;

    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
      int chunkSize = 20;
      int processed = 0;
      int totalSaved = 0;

      while (processed < chunkSize) {
        String playerName = dataQueue.poll();
        if (playerName == null) break;

        try {
          savePlayerData(playerName);
          totalSaved++;
        } catch (Exception exception) {
          plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + playerName, exception);
        }
        processed++;
      }

      logger.info("Auto saved " + totalSaved + " player data file(s) this batch.");

      if (uuidsChanged) {
        uuidsChanged = false;
        try {
          configManager.saveConfig("player_uuids.yml");
          logger.info("Saved updated player UUIDs.");
        } catch (Exception exception) {
          plugin.getLogger().log(Level.SEVERE, "Failed to save UUID config", exception);
        }
      }

      if (!dataQueue.isEmpty()) {
        logger.info(dataQueue.size() + " player data file(s) remaining in queue, scheduling next batch...");
        scheduleSave();
      }
    });
  }

  public void saveAll() {
    playerCache.values().forEach(PlayerData::save);
    dataQueue.clear();

    if (uuidsChanged) {
      try {
        configManager.saveConfig("player_uuids.yml");
        uuidsChanged = false;
        logger.info("Saved all player UUIDs.");
      } catch (Exception exception) {
        plugin.getLogger().log(Level.SEVERE, "Failed to save UUID config.", exception);
      }
    }

    logger.info("Saved all player data.");
  }

  public void savePlayerData(String playerName) {
    PlayerData data = playerCache.get(playerName);
    if (data != null) data.save();
    dataQueueSet.remove(playerName);
  }

  private void scheduleSave() {
    if (saveScheduled) return;
    saveScheduled = true;

    plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
      saveQueue();
      saveScheduled = false;
    }, 6000L);
  }
}