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
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.arena.ArenaManager;
import io.github.divinerealms.footcube.matchmaking.ban.BanManager;
import io.github.divinerealms.footcube.matchmaking.highscore.HighScoreManager;
import io.github.divinerealms.footcube.matchmaking.logic.MatchData;
import io.github.divinerealms.footcube.matchmaking.logic.MatchSystem;
import io.github.divinerealms.footcube.matchmaking.scoreboard.ScoreManager;
import io.github.divinerealms.footcube.matchmaking.team.TeamManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.managers.TaskManager;
import io.github.divinerealms.footcube.physics.utilities.PhysicsFormulae;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.*;
import lombok.Getter;
import lombok.Setter;
import me.neznamy.tab.api.TabAPI;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_ADMIN;

@Getter
public class FCManager {
  @Getter private static FCManager instance;

  private final FootCube plugin;

  private final Logger logger;
  private final Utilities utilities;
  private final ConfigManager configManager;
  private final PlayerDataManager dataManager;

  private final ArenaManager arenaManager;
  private final ScoreManager scoreboardManager;
  private final MatchData matchData;
  private final TeamManager teamManager;
  private final MatchSystem matchSystem;
  private final BanManager banManager;
  private final HighScoreManager highscoreManager;
  private final MatchManager matchManager;

  private final CubeCleaner cubeCleaner;
  private final DisableCommands disableCommands;
  private final BukkitScheduler scheduler;

  private final PhysicsData physicsData;
  private final PhysicsSystem physicsSystem;
  private final PhysicsFormulae physicsFormulae;

  private final ListenerManager listenerManager;
  private final TaskManager taskManager;

  private final Set<Player> cachedPlayers = ConcurrentHashMap.newKeySet();

  private Economy economy;
  private LuckPerms luckPerms;
  private TabAPI tabAPI;

  private static final String CONFIG_SOUNDS_KICK_BASE = "sounds.kick";
  private static final String CONFIG_SOUNDS_GOAL_BASE = "sounds.goal";
  private static final String CONFIG_PARTICLES_BASE = "particles.";

  private final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();

  @Setter private boolean enabling = false, disabling = false;

  public FCManager(FootCube plugin) throws IllegalStateException {
    instance = this;

    this.plugin = plugin;
    this.configManager = new ConfigManager(plugin, "");
    this.logger = new Logger(this);
    this.sendBanner();

    this.dataManager = new PlayerDataManager(this);

    this.setupConfig();
    this.setupMessages();
    this.setupDependencies();

    this.utilities = new Utilities(this);

    this.arenaManager = new ArenaManager(this);
    this.scoreboardManager = new ScoreManager(this);
    this.matchData = new MatchData();
    this.teamManager = new TeamManager(this);
    this.matchSystem = new MatchSystem(this);
    this.banManager = new BanManager();
    this.highscoreManager = new HighScoreManager(this);
    this.matchManager = new MatchManager(this);

    this.cubeCleaner = new CubeCleaner(this);
    this.disableCommands = new DisableCommands(this);
    this.scheduler = plugin.getServer().getScheduler();

    this.physicsData = new PhysicsData();
    this.physicsSystem = new PhysicsSystem(physicsData, logger, scheduler, plugin);
    this.physicsFormulae = new PhysicsFormulae(logger);

    this.listenerManager = new ListenerManager(this);
    this.taskManager = new TaskManager(this);

    new FCPlaceholders(this).register();
    enabling = false;
    this.reload();
  }

  public void reload() {
    initializeCachedPlayers();
    if (!enabling) configManager.reloadAllConfigs();
    arenaManager.reloadArenas();
    setupConfig();
    setupMessages();
    registerCommands();
    listenerManager.registerAll();

    taskManager.restart();
    scheduler.runTaskLaterAsynchronously(plugin, () -> {
      logger.info("&a✔ &2Updating &eHighScores&2...");
      highscoreManager.update();
    }, 20L);

    List<UUID> onlinePlayers = new ArrayList<>(cachedPlayers.size());
    for (Player p : cachedPlayers) {
      if (p == null) continue;
      onlinePlayers.add(p.getUniqueId());
    }

    scheduler.runTaskAsynchronously(plugin, () -> onlinePlayers.forEach(uuid -> {
      Player asyncPlayer = plugin.getServer().getPlayer(uuid);
      if (asyncPlayer == null || !asyncPlayer.isOnline()) return;

      PlayerData playerData = dataManager.get(asyncPlayer);
      if (playerData != null) preloadSettings(asyncPlayer, playerData);
    }));
  }

  private void initializeCachedPlayers() {
    Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
    cachedPlayers.clear();
    cachedPlayers.addAll(onlinePlayers);
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

    if (plugin.getServer().getPluginManager().isPluginEnabled("TAB")) {
      this.tabAPI = TabAPI.getInstance();
    } else {
      this.tabAPI = null;
      logger.info("&eTAB plugin not found. Scoreboard features will be disabled.");
    }

    logger.info("&a✔ &2Hooked into &dLuckPerms&2, &dTAB &2and &dVault &2successfully!");
  }

  public void reloadTabAPI() {
    if (plugin.getServer().getPluginManager().isPluginEnabled("TAB")) {
      plugin.getServer().getScheduler().runTask(plugin, () -> {
        this.tabAPI = TabAPI.getInstance();
        logger.info("&a✔ &2Re-hooked into &dTAB &2successfully!");
        if (scoreboardManager != null) scoreboardManager.refreshTabAPI();
        if (matchManager != null) matchManager.recreateScoreboards();
      });
    } else {
      this.tabAPI = null;
      logger.info("&eTAB plugin not found. Scoreboard features will be disabled.");
    }
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
    if (playerData.has("ban")) matchManager.getBanManager().getBannedPlayers().put(player.getUniqueId(), (Long) playerData.get("ban"));
  }

  public void saveAll() {
    configManager.saveAll();
    dataManager.saveAll();
  }

  public void sendBanner() {
    StringJoiner joiner = new StringJoiner(", ");
    List<String> authors = plugin.getDescription().getAuthors();
    for (String author : authors) joiner.add(author);

    String[] banner = new String[]{"&2┏┓┏┓" + "&8 -+-------------------------------------------+-", "&2┣ ┃ " + "&7  Created by &b" + joiner + "&7, version &f" + plugin.getDescription().getVersion(), "&2┻ ┗┛" + "&8 -+-------------------------------------------+-",};

    for (String line : banner) {
      plugin.getServer().getConsoleSender().sendMessage(logger.getConsolePrefix() + logger.color(line));
    }
  }

  public void cleanup() {
    long start = System.nanoTime();
    try {
      // Reset entity and state tracking structures.
      physicsData.cleanup();
      playerSettings.clear();
    } catch (Exception exception) {
      Bukkit.getLogger().log(Level.SEVERE, "Error in cleanup: " + exception.getMessage(), exception);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send(PERM_ADMIN, "{prefix-admin}&dCleanup &ftook &e" + ms + "ms &f(threshold: " + DEBUG_ON_MS + "ms)");
    }
  }
}
