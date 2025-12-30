package io.github.divinerealms.footcube.configs;

import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import java.io.File;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

public class PlayerData {

  private final String playerName;
  private final ConfigManager configManager;
  private final PlayerDataManager dataManager;
  @Getter
  private final FileConfiguration config;

  public PlayerData(String playerName, ConfigManager configManager, PlayerDataManager dataManager) {
    this.configManager = configManager;
    this.playerName = playerName;
    this.dataManager = dataManager;

    this.configManager.createNewFile("players" + File.separator + playerName + ".yml",
        "Player data for " + playerName);
    this.config = configManager.getConfig("players" + File.separator + playerName + ".yml");
  }

  public boolean has(String path) {
    return config.isSet(path);
  }

  public Object get(String path) {
    return config == null
        ? 0
        : config.get(path, 0);
  }

  public void set(String path, Object value) {
    Object current = config.get(path);

    if ((current == null && value != null)
        || (current != null && !current.equals(value))) {
      config.set(path, value);
      dataManager.queueAdd(playerName);
    }
  }

  public void add(String key) {
    int current = (int) get(key);
    set(key, current + 1);
  }

  public void remove(String key) {
    int current = (int) get(key);
    set(key, current > 0
        ? current - 1
        : 0);
  }

  public void save() {
    configManager.saveConfig("players" + File.separator + playerName + ".yml");
  }
}
