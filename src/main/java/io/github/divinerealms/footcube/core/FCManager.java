package io.github.divinerealms.footcube.core;

import static io.github.divinerealms.footcube.configs.Lang.HELP_FOOTER;
import static io.github.divinerealms.footcube.configs.Lang.HELP_FORMAT;
import static io.github.divinerealms.footcube.configs.Lang.HELP_HEADER;
import static io.github.divinerealms.footcube.configs.Lang.HELP_USAGE;
import static io.github.divinerealms.footcube.configs.Lang.INGAME_ONLY;
import static io.github.divinerealms.footcube.configs.Lang.NO_PERM;
import static io.github.divinerealms.footcube.configs.Lang.NO_PERM_PARAMETERS;
import static io.github.divinerealms.footcube.configs.Lang.PLAYER_NOT_FOUND;
import static io.github.divinerealms.footcube.configs.Lang.UNKNOWN_COMMAND;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.DEBUG_ON_MS;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_ADMIN;

import co.aikar.commands.BukkitCommandCompletionContext;
import co.aikar.commands.CommandCompletions;
import co.aikar.commands.MessageKeys;
import co.aikar.commands.PaperCommandManager;
import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.commands.FCAdminArenaCommands;
import io.github.divinerealms.footcube.commands.FCAdminBanCommands;
import io.github.divinerealms.footcube.commands.FCAdminCommand;
import io.github.divinerealms.footcube.commands.FCAdminDebugCommands;
import io.github.divinerealms.footcube.commands.MatchManCommands;
import io.github.divinerealms.footcube.commands.FCAdminPlayerCommands;
import io.github.divinerealms.footcube.commands.FCAdminSystemCommands;
import io.github.divinerealms.footcube.commands.FCBuildCommand;
import io.github.divinerealms.footcube.commands.FCCommand;
import io.github.divinerealms.footcube.commands.FCCubeCommands;
import io.github.divinerealms.footcube.commands.FCGameCommands;
import io.github.divinerealms.footcube.commands.FCMatchesCommand;
import io.github.divinerealms.footcube.commands.FCSettingsCommands;
import io.github.divinerealms.footcube.commands.FCTeamCommands;
import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.managers.ListenerManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.managers.TaskManager;
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
import io.github.divinerealms.footcube.physics.utilities.PhysicsFormulae;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.CubeCleaner;
import io.github.divinerealms.footcube.utils.DisableCommands;
import io.github.divinerealms.footcube.utils.FCPlaceholders;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
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

@Getter
public class FCManager {

  private static final String CONFIG_SOUNDS_KICK_BASE = "sounds.kick";
  private static final String CONFIG_SOUNDS_GOAL_BASE = "sounds.goal";
  private static final String CONFIG_PARTICLES_BASE = "particles.";

  @Getter
  private static FCManager instance;

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
  private final DisableCommands disableCommands;
  private final BukkitScheduler scheduler;
  private final PhysicsData physicsData;
  private final PhysicsSystem physicsSystem;
  private final PhysicsFormulae physicsFormulae;
  private final CubeCleaner cubeCleaner;
  private final ListenerManager listenerManager;
  private final TaskManager taskManager;
  private final Set<Player> cachedPlayers = ConcurrentHashMap.newKeySet();
  private final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();
  private final Map<UUID, String> cachedPrefixedNames = new ConcurrentHashMap<>();

  private PaperCommandManager commandManager;

  private Economy economy;
  private LuckPerms luckPerms;
  private TabAPI tabAPI;

  @Setter
  private boolean enabling = false, disabling = false;

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
    this.banManager = new BanManager(this);
    this.highscoreManager = new HighScoreManager(this);
    this.matchManager = new MatchManager(this);

    this.disableCommands = new DisableCommands(this);
    this.scheduler = plugin.getServer().getScheduler();

    this.physicsData = new PhysicsData();
    this.physicsSystem = new PhysicsSystem(physicsData, logger, scheduler, plugin);
    this.physicsFormulae = new PhysicsFormulae(logger);

    this.cubeCleaner = new CubeCleaner(this);
    this.listenerManager = new ListenerManager(this);
    this.taskManager = new TaskManager(this);

    new FCPlaceholders(this).register();
    this.reload();
  }

  public void reload() {
    initializeCachedPlayers();
    if (!enabling) {
      configManager.reloadAllConfigs();
    }

    arenaManager.reloadArenas();
    setupConfig();
    setupMessages();
    registerCommands();
    listenerManager.registerAll();

    taskManager.restart();

    List<UUID> onlinePlayers = new ArrayList<>(cachedPlayers.size());
    for (Player p : cachedPlayers) {
      if (p == null) {
        continue;
      }

      onlinePlayers.add(p.getUniqueId());
    }

    scheduler.runTaskAsynchronously(plugin, () -> onlinePlayers.forEach(uuid -> {
      Player asyncPlayer = plugin.getServer().getPlayer(uuid);
      if (asyncPlayer == null || !asyncPlayer.isOnline()) {
        return;
      }

      PlayerData playerData = dataManager.get(asyncPlayer);
      if (playerData != null) {
        preloadSettings(asyncPlayer, playerData);
      }
    }));
  }

  private void initializeCachedPlayers() {
    Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
    cachedPlayers.clear();
    cachedPlayers.addAll(onlinePlayers);
  }

  public void registerCommands() {
    if (commandManager != null) {
      try {
        commandManager.unregisterCommands();
        logger.info("&e⟳ &6Unregistered old ACF commands.");
      } catch (Exception exception) {
        Bukkit.getLogger()
            .log(Level.SEVERE, "&cFailed to unregister old commands: " + exception.getMessage(),
                exception);
      }
    }

    commandManager = new PaperCommandManager(plugin);
    configureACF();
    registerCustomCompletions();
    registerPlayerCommands();
    registerAdminCommands();

    logger.info("&a✔ &2Registered commands via &eACF &2successfully.");
  }

  private void configureACF() {
    commandManager.enableUnstableAPI("help");
    commandManager.getLocales().addMessage(Locale.ENGLISH,
        MessageKeys.PERMISSION_DENIED,
        NO_PERM.toString());
    commandManager.getLocales().addMessage(Locale.ENGLISH,
        MessageKeys.PERMISSION_DENIED_PARAMETER,
        NO_PERM_PARAMETERS.toString());
    commandManager.getLocales().addMessage(Locale.ENGLISH,
        MessageKeys.INVALID_SYNTAX,
        HELP_USAGE.toString());
    commandManager.getLocales().addMessage(Locale.ENGLISH,
        MessageKeys.COULD_NOT_FIND_PLAYER,
        PLAYER_NOT_FOUND.toString());
    commandManager.getLocales().addMessage(Locale.ENGLISH,
        MessageKeys.NOT_ALLOWED_ON_CONSOLE,
        INGAME_ONLY.toString());
    commandManager.getLocales().addMessage(Locale.ENGLISH,
        MessageKeys.UNKNOWN_COMMAND,
        UNKNOWN_COMMAND.toString());
    commandManager.getLocales().addMessage(Locale.ENGLISH,
        MessageKeys.HELP_FORMAT,
        HELP_FORMAT.toString());
    commandManager.getLocales().addMessage(Locale.ENGLISH,
        MessageKeys.HELP_HEADER,
        HELP_HEADER.toString());
    commandManager.getLocales().addMessage(Locale.ENGLISH,
        MessageKeys.HELP_PAGE_INFORMATION,
        HELP_FOOTER.toString());
  }

  private void registerCustomCompletions() {
    CommandCompletions<BukkitCommandCompletionContext> completions =
        commandManager.getCommandCompletions();
    completions.registerStaticCompletion("particles",
        PlayerSettings.getAllowedParticles());
    completions.registerStaticCompletion("colors",
        PlayerSettings.getAllowedColorNames());
  }

  private void registerPlayerCommands() {
    commandManager.registerCommand(new FCCommand(this));
    commandManager.registerCommand(new FCGameCommands(this));
    commandManager.registerCommand(new FCTeamCommands(this));
    commandManager.registerCommand(new FCCubeCommands(this));
    commandManager.registerCommand(new FCSettingsCommands(this));
    commandManager.registerCommand(new FCBuildCommand(this));
    commandManager.registerCommand(new FCMatchesCommand(this));
  }

  private void registerAdminCommands() {
    commandManager.registerCommand(new FCAdminCommand());
    commandManager.registerCommand(new FCAdminSystemCommands(this));
    commandManager.registerCommand(new FCAdminBanCommands(this));
    commandManager.registerCommand(new FCAdminArenaCommands(this));
    commandManager.registerCommand(new FCAdminPlayerCommands(this));
    commandManager.registerCommand(new MatchManCommands(this));
    commandManager.registerCommand(new FCAdminDebugCommands(this));
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
    RegisteredServiceProvider<LuckPerms> luckPermsRsp = plugin.getServer().getServicesManager()
        .getRegistration(
            LuckPerms.class);
    this.luckPerms = luckPermsRsp == null
        ? null
        : luckPermsRsp.getProvider();
    if (luckPerms == null) {
      throw new IllegalStateException("LuckPerms not found!");
    }

    RegisteredServiceProvider<Economy> economyRsp = plugin.getServer().getServicesManager()
        .getRegistration(
            Economy.class);
    this.economy = economyRsp == null
        ? null
        : economyRsp.getProvider();
    if (economy == null) {
      throw new IllegalStateException("Vault not found!");
    }

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
        if (scoreboardManager != null) {
          scoreboardManager.refreshTabAPI();
        }

        if (matchManager != null) {
          matchManager.recreateScoreboards();
        }
      });
    } else {
      this.tabAPI = null;
      logger.info("&eTAB plugin not found. Scoreboard features will be disabled.");
    }
  }

  private void setDefaultIfMissing(FileConfiguration file, String path, Object value) {
    if (!file.isSet(path)) {
      file.set(path, value);
    }
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
          } catch (IllegalArgumentException ignored) {
          }
        }
      } catch (IllegalArgumentException exception) {
        plugin.getLogger().log(Level.WARNING,
            "Invalid particle effect found for player " + player.getName() + ": " + effect);
      }
    }

    if (playerData.has(CONFIG_SOUNDS_KICK_BASE + ".enabled")) {
      settings.setKickSoundEnabled((Boolean) playerData.get(CONFIG_SOUNDS_KICK_BASE + ".enabled"));
    }
    if (playerData.has(CONFIG_SOUNDS_KICK_BASE + ".sound")) {
      settings.setKickSound(
          Sound.valueOf((String) playerData.get(CONFIG_SOUNDS_KICK_BASE + ".sound")));
    }
    if (playerData.has(CONFIG_SOUNDS_GOAL_BASE + ".enabled")) {
      settings.setGoalSoundEnabled((Boolean) playerData.get(CONFIG_SOUNDS_GOAL_BASE + ".enabled"));
    }
    if (playerData.has(CONFIG_SOUNDS_GOAL_BASE + ".sound")) {
      settings.setGoalSound(
          Sound.valueOf((String) playerData.get(CONFIG_SOUNDS_GOAL_BASE + ".sound")));
    }
    if (playerData.has(CONFIG_PARTICLES_BASE + ".enabled")) {
      settings.setParticlesEnabled((Boolean) playerData.get(CONFIG_PARTICLES_BASE + ".enabled"));
    }
    if (playerData.has("ban")) {
      matchManager.getBanManager().getBannedPlayers()
          .put(player.getUniqueId(), (Long) playerData.get("ban"));
    }

    String goalCelebration = "default";
    if (playerData.has("goalcelebration")) {
      goalCelebration = (String) playerData.get("goalcelebration");
    }
    settings.setGoalMessage(goalCelebration);
  }

  public void saveAll() {
    configManager.saveAll();
    dataManager.saveAll();
  }

  public void sendBanner() {
    StringJoiner joiner = new StringJoiner(", ");
    List<String> authors = plugin.getDescription().getAuthors();
    for (String author : authors) {
      joiner.add(author);
    }

    String[] banner = new String[]{
        "&2┏┓┏┓" + "&8 -+-------------------------------------------+-",
        "&2┣ ┃ " + "&7  Created by &b" + joiner + "&7, version &f" + plugin.getDescription()
            .getVersion(),
        "&2┻ ┗┛" + "&8 -+-------------------------------------------+-"
    };

    for (String line : banner) {
      plugin.getServer().getConsoleSender().sendMessage(
          logger.getConsolePrefix() + logger.color(line)
      );
    }
  }

  public String getPrefixedName(UUID uuid) {
    return cachedPrefixedNames.get(uuid);
  }

  public void cachePrefixedName(Player player) {
    UUID uuid = player.getUniqueId();
    String playerName = player.getName();

    utilities.getPrefixedName(uuid, playerName).thenAccept(prefixedName ->
        cachedPrefixedNames.put(uuid, prefixedName)
    );
  }

  public void cleanup() {
    long start = System.nanoTime();
    try {
      if (commandManager != null) {
        try {
          commandManager.unregisterCommands();
          logger.info("&e⟳ &6Unregistered ACF commands during cleanup.");
        } catch (Exception exception) {
          Bukkit.getLogger().log(Level.SEVERE,
              "&cFailed to unregister commands during cleanup: " + exception.getMessage(),
              exception);
        }
      }
      if (listenerManager != null) {
        listenerManager.unregisterAll();
      }
      if (physicsData != null) {
        physicsData.cleanup();
      }
      playerSettings.clear();
      cachedPrefixedNames.clear();
      cachedPlayers.clear();
      instance = null;
    } catch (Exception exception) {
      Bukkit.getLogger()
          .log(Level.SEVERE, "Error in cleanup: " + exception.getMessage(), exception);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) {
        logger.send(PERM_ADMIN,
            "{prefix-admin}&dCleanup &ftook &e" + ms + "ms &f(threshold: " + DEBUG_ON_MS + "ms)");
      }
    }
  }
}
