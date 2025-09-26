package io.github.divinerealms.footcube.utils;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.ConfigManager;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DisableCommands implements Listener {
  private final ConfigManager configManager;
  private final FileConfiguration config;

  @Getter private final Set<String> commands = new HashSet<>();
  @Getter private final String configPath = "enabledCommands";

  public DisableCommands(FCManager fcManager) {
    Plugin plugin = fcManager.getPlugin();
    this.configManager = fcManager.getConfigManager();
    this.config = configManager.getConfig("config.yml");
    plugin.getServer().getPluginManager().registerEvents(this, plugin);

    String cfgCommands = plugin.getConfig().getString(getConfigPath(), "").toLowerCase().trim();
    if (!cfgCommands.isEmpty()) {
      Collections.addAll(getCommands(), cfgCommands.split("\\s+"));
    }
  }

  public boolean addCommand(String cmd) {
    boolean added = getCommands().add(cmd.toLowerCase());
    saveConfig();
    return added;
  }

  public boolean removeCommand(String cmd) {
    boolean removed = getCommands().remove(cmd.toLowerCase());
    saveConfig();
    return removed;
  }

  private void saveConfig() {
    config.set(getConfigPath(), String.join(" ", getCommands()));
    configManager.saveConfig("config.yml");
  }
}
