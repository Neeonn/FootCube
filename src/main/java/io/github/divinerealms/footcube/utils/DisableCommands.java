package io.github.divinerealms.footcube.utils;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.ConfigManager;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DisableCommands {
  private final ConfigManager configManager;
  private final FileConfiguration config;

  @Getter private final Set<String> commands = new HashSet<>();
  private final String configPath = "enabledCommands";

  public DisableCommands(FCManager fcManager) {
    this.configManager = fcManager.getConfigManager();
    this.config = configManager.getConfig("config.yml");

    String cfgCommands = config.getString(configPath, "").toLowerCase().trim();
    if (!cfgCommands.isEmpty()) Collections.addAll(commands, cfgCommands.split("\\s+"));
  }

  public boolean addCommand(String cmd) {
    boolean added = commands.add(cmd.toLowerCase());
    saveConfig();
    return added;
  }

  public boolean removeCommand(String cmd) {
    boolean removed = commands.remove(cmd.toLowerCase());
    saveConfig();
    return removed;
  }

  private void saveConfig() {
    config.set(configPath, String.join(" ", commands));
    configManager.saveConfig("config.yml");
  }
}
