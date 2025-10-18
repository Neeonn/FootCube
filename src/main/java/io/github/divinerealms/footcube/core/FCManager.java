package io.github.divinerealms.footcube.core;

import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.commands.AdminCommands;
import io.github.divinerealms.footcube.commands.BuildCommand;
import io.github.divinerealms.footcube.commands.FCCommand;
import io.github.divinerealms.footcube.commands.MatchesCommand;
import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.managers.ListenerManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.utils.*;
import lombok.Getter;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Getter
public class FCManager {
  private final FootCube plugin;

  private final Logger logger;
  private final Utilities utilManager;
  private final ConfigManager configManager;
  private final PlayerDataManager dataManager;
  private final Organization org;
  private final ListenerManager listenerManager;

  private final Physics physics;
  private final CubeCleaner cubeCleaner;

  private final List<String> registeredCommands = new ArrayList<>();

  private Economy economy;
  private Chat chat;
  private LuckPerms luckPerms;

  private boolean physicsRunning = false;
  private int physicsTaskID;
  private int glowTaskID;

  private boolean cubeCleanerRunning = false;
  private int cubeCleanerTaskID;

  private static final String CONFIG_SOUNDS_KICK_BASE = "sounds.kick";
  private static final String CONFIG_SOUNDS_GOAL_BASE = "sounds.goal";
  private static final String CONFIG_PARTICLES_BASE = "particles.";

  private final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();

  public FCManager(FootCube plugin) throws IllegalStateException {
    this.plugin = plugin;
    this.configManager = new ConfigManager(plugin, "");
    this.logger = new Logger(plugin);
    this.sendBanner();

    this.dataManager = new PlayerDataManager(this);

    this.setupConfig();
    this.setupMessages();
    this.setupDependencies();

    this.utilManager = new Utilities(this);
    this.org = new Organization(this);
    this.listenerManager = new ListenerManager(this);

    this.physics = new Physics(this);
    this.cubeCleaner = new CubeCleaner(this);

    new FCPlaceholders(this).register();
    this.reload();
  }

  public void reload() {
    configManager.reloadAllConfigs();
    setupConfig();
    setupMessages();
    registerCommands();
    initTasks();
    org.loadArenas();
    getListenerManager().registerAll();

    plugin.getServer().getOnlinePlayers().forEach(player -> {
      PlayerData playerData = dataManager.get(player);
      if (playerData != null) preloadSettings(player, playerData);
    });
  }

  public void initTasks() {
    shutdownTasks();

    this.physicsRunning = false;
    this.cubeCleanerRunning = false;

    physics.reload();
    this.physicsRunning = true;
    this.physicsTaskID = plugin.getServer().getScheduler().runTaskTimer(plugin, physics::tick, 1L, 1L).getTaskId();
    this.glowTaskID = plugin.getServer().getScheduler().runTaskTimer(plugin, physics::showCubeParticles, 5L, 5L).getTaskId();

    if (cubeCleaner.practiceAreasSet()) {
      this.cubeCleanerRunning = true;
      this.cubeCleanerTaskID = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
        cubeCleaner.clearCubes();
        if (!cubeCleaner.isEmpty()) logger.broadcast(Lang.CLEARED_CUBES.replace(new String[]{String.valueOf(cubeCleaner.getAmount())}));
      }, 20L, cubeCleaner.getRemoveInterval()).getTaskId();
    }

    logger.info("&a✔ &2Restarted all plugin tasks.");

    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      logger.info("&a✔ &2Updating &eHighScores&2...");
      org.getHighscores().update();
    }, 20L);
  }

  public void shutdownTasks() {
    if (physicsRunning) {
      plugin.getServer().getScheduler().cancelTask(physicsTaskID);
      physics.removeCubes();
      this.physicsRunning = false;
    }

    if (cubeCleanerRunning) {
      plugin.getServer().getScheduler().cancelTask(cubeCleanerTaskID);
      this.cubeCleanerRunning = false;
    }

    plugin.getServer().getScheduler().cancelTask(glowTaskID);
  }

  public void registerCommands() {
    FCCommand fcCommand = new FCCommand(this);
    AdminCommands adminCommands = new AdminCommands(this, new DisableCommands(this));
    BuildCommand buildCommand = new BuildCommand(this);

    plugin.getCommand("footcube").setExecutor(fcCommand);
    plugin.getCommand("footcube").setTabCompleter(fcCommand);

    plugin.getCommand("matches").setExecutor(new MatchesCommand(this));

    plugin.getCommand("fcadmin").setExecutor(adminCommands);
    plugin.getCommand("fcadmin").setTabCompleter(adminCommands);

    plugin.getCommand("build").setExecutor(buildCommand);
    plugin.getCommand("build").setTabCompleter(buildCommand);

    logger.info("&a✔ &2Registered commands via plugin.yml successfully.");
  }

  private void setupConfig() {
    configManager.createNewFile("config.yml", "FootCubeOG Main Configuration");
  }

  private void setupMessages() {
    FileConfiguration file = configManager.getConfig("messages.yml");
    Lang.setFile(file);

    for (Lang value : Lang.values()) {
      setDefaultIfMissing(file, value.getPath(), value.getDefault());
    }

    file.options().copyDefaults(true);
    configManager.saveConfig("messages.yml");
  }

  private void setupDependencies() throws IllegalStateException {
    RegisteredServiceProvider<LuckPerms> luckPermsRsp = plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
    this.luckPerms = luckPermsRsp == null ? null : luckPermsRsp.getProvider();
    if (luckPerms == null) throw new IllegalStateException("LuckPerms not found!");

    RegisteredServiceProvider<Economy> economyRsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
    this.economy = economyRsp == null ? null : economyRsp.getProvider();
    if (economy == null) throw new IllegalStateException("Vault not found!");

    RegisteredServiceProvider<Chat> chatRsp = plugin.getServer().getServicesManager().getRegistration(Chat.class);
    this.chat = chatRsp == null ? null : chatRsp.getProvider();
    if (chat == null) throw new IllegalStateException("Vault not found!");

    logger.info("&a✔ &2Hooked into &dLuckPerms &2and &dVault &2successfully!");
  }

  private void setDefaultIfMissing(FileConfiguration file, String path, Object value) {
    if (!file.isSet(path)) file.set(path, value);
  }

  public PlayerSettings getPlayerSettings(Player player) {
    return playerSettings.get(player.getUniqueId());
  }

  public void preloadSettings(Player player, PlayerData playerData) {
    PlayerSettings settings = getPlayerSettings(player);
    if (settings == null) {
      settings = new PlayerSettings();
      playerSettings.put(player.getUniqueId(), settings);
    }

    if (playerData.has(CONFIG_PARTICLES_BASE + ".effect")) {
      String effect = (String) playerData.get(CONFIG_PARTICLES_BASE + ".effect");
      try {
        EnumParticle particle = EnumParticle.valueOf(effect.split(":")[0]);
        settings.setParticle(particle);

        if (particle == EnumParticle.REDSTONE && effect.contains(":")) {
          String colorName = effect.split(":")[1];
          try {
            settings.setCustomRedstoneColor(colorName);
          } catch (IllegalArgumentException ignored) {}
        }
      } catch (IllegalArgumentException exception) {
        plugin.getLogger().log(Level.WARNING, "Invalid particle effect found for player " + player.getName() + ": " + effect);
      }
    }

    if (playerData.has(CONFIG_SOUNDS_KICK_BASE + ".enabled")) settings.setKickSoundEnabled((Boolean) playerData.get(CONFIG_SOUNDS_KICK_BASE + ".enabled"));
    if (playerData.has(CONFIG_SOUNDS_KICK_BASE + ".sound")) settings.setKickSound(Sound.valueOf((String) playerData.get(CONFIG_SOUNDS_KICK_BASE + ".sound")));
    if (playerData.has(CONFIG_SOUNDS_GOAL_BASE + ".enabled")) settings.setGoalSoundEnabled((Boolean) playerData.get(CONFIG_SOUNDS_GOAL_BASE + ".enabled"));
    if (playerData.has(CONFIG_SOUNDS_GOAL_BASE + ".sound")) settings.setGoalSound(Sound.valueOf((String) playerData.get(CONFIG_SOUNDS_GOAL_BASE + ".sound")));
    if (playerData.has(CONFIG_PARTICLES_BASE + ".enabled")) settings.setParticlesEnabled((Boolean) playerData.get(CONFIG_PARTICLES_BASE + ".enabled"));
    if (playerData.has("ban")) org.getLeaveCooldowns().put(player.getUniqueId(), (Long) playerData.get("ban"));
  }

  public void saveAll() {
    configManager.saveAll();
    dataManager.saveAll();
  }

  private void sendBanner() {
    String[] banner = new String[]{"&2┏┓┏┓" + "&8 -+-------------------------------------------+-", "&2┣ ┃ " + "&7  Created by &b" + plugin.getDescription().getAuthors().stream().map(String::valueOf).collect(Collectors.joining(", ")) + "&7, version &f" + plugin.getDescription().getVersion(), "&2┻ ┗┛" + "&8 -+-------------------------------------------+-",};

    for (String line : banner) {
      plugin.getServer().getConsoleSender().sendMessage(logger.getConsolePrefix() + logger.color(line));
    }
  }
}
