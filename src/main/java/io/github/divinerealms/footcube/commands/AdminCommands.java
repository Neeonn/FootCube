package io.github.divinerealms.footcube.commands;

import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Match;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.core.Physics;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.utils.DisableCommands;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.MatchHelper;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AdminCommands implements CommandExecutor, TabCompleter {
  private final FCManager fcManager;
  private final FootCube plugin;
  private final Physics physics;
  private final Logger logger;
  private final Organization org;
  private final DisableCommands disableCommands;
  private final ConfigManager configManager;
  private final PlayerDataManager dataManager;
  private final FileConfiguration arenas, config, practice;

  private static final String PERM_MAIN = "footcube.admin";
  private static final String PERM_TOGGLE = PERM_MAIN + ".toggle";
  private static final String PERM_BAN = PERM_MAIN + ".ban";
  private static final String PERM_UNBAN = PERM_MAIN + ".unban";
  private static final String PERM_STAT_SET = PERM_MAIN + ".statset";
  private static final String PERM_SETUP_ARENA = PERM_MAIN + ".setuparena";
  private static final String PERM_CLEAR_ARENAS = PERM_MAIN + ".cleararenas";
  private static final String PERM_SET_LOBBY = PERM_MAIN + ".setlobby";
  private static final String PERM_SET_PRACTICE_AREA = PERM_MAIN + ".setpracticearea";
  private static final String PERM_HIT_DEBUG = PERM_MAIN + ".hitdebug";
  private static final String PERM_COMMAND_DISABLER = PERM_MAIN + ".commanddisabler";
  private static final String PERM_MATCHMAN = PERM_MAIN + ".matchman";
  private static final String PERM_FORCE_LEAVE = PERM_MAIN + ".forceleave";

  public AdminCommands(FCManager fcManager, DisableCommands disableCommands) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.physics = fcManager.getPhysics();
    this.logger = fcManager.getLogger();
    this.org = fcManager.getOrg();
    this.disableCommands = disableCommands;
    this.configManager = fcManager.getConfigManager();
    this.dataManager = fcManager.getDataManager();
    this.arenas = configManager.getConfig("arenas.yml");
    this.config = configManager.getConfig("config.yml");
    this.practice = configManager.getConfig("practice.yml");
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) { logger.send(sender, Lang.UNKNOWN_COMMAND.replace(new String[]{label})); return true; }

    String sub = args[0].toLowerCase(), formattedTime;
    Player player, target;
    Match match;
    PlayerData playerData;
    long banUntil;

    switch (sub) {
      case "reload":
        if (args.length == 1) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <configs|all>"})); return true; }
        if (!sender.hasPermission(PERM_MAIN)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_MAIN, label + " " + sub})); return true; }

        switch (args[1].toLowerCase()) {
          case "configs":
            fcManager.getConfigManager().reloadAllConfigs();
            logger.send(sender, Lang.RELOAD.replace(new String[]{"configs"}));
            return true;

          case "all":
            fcManager.reload();
            logger.send(sender, Lang.RELOAD.replace(new String[]{"all"}));
            return true;

          default:
            logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <configs|all>"}));
            return true;
        }

      case "toggle":
        if (!sender.hasPermission(PERM_TOGGLE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_TOGGLE, label + " " + sub})); return true; }
        boolean state = physics.isMatchesEnabled();
        physics.setMatchesEnabled(!state);
        fcManager.getLuckPerms().getGroupManager().modifyGroup("vip", group -> {
          if (state) {
            group.data().add(PermissionNode.builder("essentials.gamemode.spectator").build());
            group.data().add(PermissionNode.builder("essentials.gamemode.survival").build());
          } else {
            group.data().remove(PermissionNode.builder("essentials.gamemode.spectator").build());
            group.data().remove(PermissionNode.builder("essentials.gamemode.survival").build());
          }
        });
        logger.send(sender, Lang.FC_TOGGLE.replace(new String[]{state ? Lang.OFF.replace(null) : Lang.ON.replace(null)}));
        break;

      case "ban":
        if (!sender.hasPermission(PERM_BAN)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_BAN, label + " " + sub})); return true; }
        if (args.length < 3) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player> <time>"})); return true; }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

        int seconds;
        try {
          seconds = Utilities.parseTime(args[2]);
        } catch (NumberFormatException exception) {
          logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player> <time>"}));
          return true;
        }

        if (seconds <= 0) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " ban <player> <time>"})); return true; }
        banUntil = System.currentTimeMillis() + (seconds * 1000L);
        org.getLeaveCooldowns().put(target.getUniqueId(), banUntil);

        playerData = dataManager.get(target);
        playerData.set("ban", banUntil);
        dataManager.savePlayerData(target.getName());

        formattedTime = Utilities.formatTime(seconds);
        logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + target.getDisplayName() + "&c je banovan iz FC na &e" + formattedTime + "&c.");
        break;

      case "unban":
        if (!sender.hasPermission(PERM_UNBAN)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_UNBAN, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player>"})); return true; }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

        if (org.getLeaveCooldowns().remove(target.getUniqueId()) != null) {
          playerData = dataManager.get(target);
          playerData.set("ban", null);
          dataManager.savePlayerData(target.getName());

          logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + target.getDisplayName() + "&a je unbanovan.");
        } else {
          logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + target.getDisplayName() + "&c nije banovan.");
        }
        break;

      case "checkban":
        if (!sender.hasPermission(PERM_BAN)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_BAN, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player>"})); return true; }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

        if (org.getLeaveCooldowns().containsKey(target.getUniqueId())) {
          banUntil = org.getLeaveCooldowns().get(target.getUniqueId());
          long now = System.currentTimeMillis();

          if (now >= banUntil) {
            org.getLeaveCooldowns().remove(target.getUniqueId());

            playerData = dataManager.get(target);
            if (playerData != null) {
              playerData.set("ban", null);
              dataManager.savePlayerData(target.getName());
            }

            logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + target.getDisplayName() + "&c nije banovan.");
          } else {
            long secondsLeft = (banUntil - now) / 1000L;
            formattedTime = Utilities.formatTime(secondsLeft);
            logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + target.getDisplayName() + "&c je banovan još &e" + formattedTime + "&c.");
          }
        } else {
          logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + target.getDisplayName() + "&c nije banovan.");
        }
        break;

      case "statset":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        if (!sender.hasPermission(PERM_STAT_SET)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_STAT_SET, label + " " + sub})); return true; }
        if (args.length < 4) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player> <stat> <amount|clear>"})); return true; }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

        playerData = dataManager.get(target);
        String stat = args[2].toLowerCase();
        boolean clear = args[3].equalsIgnoreCase("clear");
        int amount = 0;

        if (!clear) {
          try { amount = Integer.parseInt(args[3]); }
          catch (NumberFormatException e) {
            logger.send(sender, Lang.STATSSET_IS_NOT_A_NUMBER.replace(new String[]{args[3]}));
            return true;
          }
        }

        switch (stat) {
          case "wins": case "matches": case "ties": case "goals": case "assists": case "owngoals": case "winstreak": case "bestwinstreak":
            playerData.set(stat, clear ? 0 : amount);
            break;

          case "all":
            playerData.set("wins", clear ? 0 : amount);
            playerData.set("matches", clear ? 0 : amount);
            playerData.set("ties", clear ? 0 : amount);
            playerData.set("goals", clear ? 0 : amount);
            playerData.set("assists", clear ? 0 : amount);
            playerData.set("ownGoals", clear ? 0 : amount);
            playerData.set("winstreak", clear ? 0 : amount);
            playerData.set("bestwinstreak", clear ? 0 : amount);
            break;

          default:
            logger.send(sender, Lang.STATSSET_NOT_A_STAT.replace(new String[]{stat}));
            return true;
        }

        dataManager.savePlayerData(target.getName());
        logger.send(sender, Lang.ADMIN_STATSET.replace(new String[]{stat, target.getName(), String.valueOf(amount)}));
        break;

      case "setuparena":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        if (!sender.hasPermission(PERM_SETUP_ARENA)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_SETUP_ARENA, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <2v2|3v3|4v4>"})); return true; }

        player = (Player) sender;
        String type = args[1].toLowerCase();
        if (!Arrays.asList("2v2", "3v3", "4v4").contains(type)) { logger.send(sender, "&cInvalid type. Use 2v2, 3v3, or 4v4."); return true; }
        if (org.getSetupGuy() != null) { logger.send(sender, Lang.SETUP_ARENA_ALREADY_SOMEONE.replace(new String[]{org.getSetupGuy()})); return true; }

        MatchHelper.ArenaData setup = MatchHelper.getArenaData(org, type);
        if (setup == null) { logger.send(sender, Lang.JOIN_INVALIDTYPE.replace(new String[]{type, Lang.OR.replace(null)})); return true; }

        org.setSetupType(setup.size);
        org.setSetupGuy(player.getName());
        logger.send(sender, Lang.SETUP_ARENA_START.replace(null));
        break;

      case "set":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        if (!sender.hasPermission(PERM_SETUP_ARENA)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_SETUP_ARENA, label + " " + sub})); return true; }
        player = (Player) sender;

        if (!player.getName().equals(org.getSetupGuy())) { logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + "Not setting up an arena"); return true; }
        int arenaType = org.getSetupType();
        String typeString = arenaType + "v" + arenaType;

        if (org.getSetupLoc() == null) {
          org.setSetupLoc(player.getLocation());
          logger.send(sender, Lang.SETUP_ARENA_FIRST_SET.replace(null));
          return true;
        }

        int index = arenas.getInt("arenas." + typeString + ".amount", 0) + 1;
        Location blue = org.getSetupLoc();
        Location red = player.getLocation();

        arenas.set("arenas." + typeString + ".amount", index);
        arenas.set("arenas.world", player.getWorld().getName());

        String bluePath = "arenas." + typeString + "." + index + ".blue.";
        String redPath = "arenas." + typeString + "." + index + ".red.";

        arenas.set(bluePath + "x", blue.getX()); arenas.set(bluePath + "y", blue.getY()); arenas.set(bluePath + "z", blue.getZ());
        arenas.set(bluePath + "pitch", blue.getPitch()); arenas.set(bluePath + "yaw", blue.getYaw());

        arenas.set(redPath + "x", red.getX()); arenas.set(redPath + "y", red.getY()); arenas.set(redPath + "z", red.getZ());
        arenas.set(redPath + "pitch", red.getPitch()); arenas.set(redPath + "yaw", red.getYaw());

        configManager.saveConfig("arenas.yml");
        org.addArena(arenaType, blue, red);
        org.setSetupGuy(null); org.setSetupType(0); org.setSetupLoc(null);

        logger.send(sender, Lang.SETUP_ARENA_SUCCESS.replace(null));
        break;

      case "undo":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        if (!sender.hasPermission(PERM_SETUP_ARENA)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_SETUP_ARENA, label + " " + sub})); return true; }
        player = (Player) sender;

        if (!player.getName().equals(org.getSetupGuy())) { logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + "Not setting up an arena"); return true; }

        org.setSetupGuy(null); org.setSetupType(0); org.setSetupLoc(null);
        logger.send(sender, Lang.UNDO.replace(null));
        break;

      case "cleararenas":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        if (!sender.hasPermission(PERM_CLEAR_ARENAS)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_CLEAR_ARENAS, label + " " + sub})); return true; }

        arenas.set("arenas", null);
        for (String t : Arrays.asList("2v2", "3v3", "4v4")) {
          arenas.addDefault("arenas." + t + ".amount", 0);
          MatchHelper.ArenaData arenaData = MatchHelper.getArenaData(org, t);
          if (arenaData != null) arenaData.matches = new Match[0];
        }

        configManager.saveConfig("arenas.yml");
        logger.send(sender, Lang.CLEAR_ARENAS_SUCCESS.replace(null));
        break;

      case "setlobby":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        if (!sender.hasPermission(PERM_SET_LOBBY)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_SET_LOBBY, label + " " + sub})); return true; }

        player = (Player) sender;
        config.set("lobby", player.getLocation());
        configManager.saveConfig("config.yml");
        logger.send(sender, Lang.PRACTICE_AREA_SET.replace(new String[]{"lobby",
            String.valueOf(player.getLocation().getX()),
            String.valueOf(player.getLocation().getY()),
            String.valueOf(player.getLocation().getZ())}));
        break;

      case "setpracticearea":
      case "spa":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        if (!sender.hasPermission(PERM_SET_PRACTICE_AREA)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_SET_PRACTICE_AREA, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <name>"})); return true; }

        player = (Player) sender;
        String name = args[1];
        practice.set("practice-areas." + name, player.getLocation());
        configManager.saveConfig("practice.yml");

        logger.send(sender, Lang.PRACTICE_AREA_SET.replace(new String[]{name,
            String.valueOf(player.getLocation().getX()),
            String.valueOf(player.getLocation().getY()),
            String.valueOf(player.getLocation().getZ())}));
        break;

      case "hitsdebug":
      case "hits":
        if (!sender.hasPermission(PERM_HIT_DEBUG)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_HIT_DEBUG, label + " " + sub})); return true; }
        boolean status = physics.isHitDebugEnabled();
        physics.hitDebugEnabled = !status;
        logger.send(sender, Lang.TOGGLES_HIT_DEBUG.replace(new String[]{status ? Lang.OFF.replace(null) : Lang.ON.replace(null)}));
        break;

      case "commanddisabler":
      case "cd":
        if (!sender.hasPermission(PERM_COMMAND_DISABLER)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_COMMAND_DISABLER, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <add|remove|list> [command]"})); return true; }

        String action = args[1].toLowerCase();
        switch (action) {
          case "add":
            if (args.length < 3) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " add <command>"})); return true; }
            if (disableCommands.addCommand(args[2])) logger.send(sender, Lang.COMMAND_DISABLER_SUCCESS.replace(new String[]{args[2]}));
            else logger.send(sender, Lang.COMMAND_DISABLER_ALREADY_ADDED.replace(null));
            break;

          case "remove":
            if (args.length < 3) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " remove <command>"})); return true; }
            if (disableCommands.removeCommand(args[2])) logger.send(sender, Lang.COMMAND_DISABLER_SUCCESS_REMOVE.replace(new String[]{args[2]}));
            else logger.send(sender, Lang.COMMAND_DISABLER_WASNT_ADDED.replace(null));
            break;

          case "list":
            logger.send(sender, Lang.COMMAND_DISABLER_LIST.replace(null));
            disableCommands.getCommands().forEach(c -> logger.send(sender, "&7" + c));
            break;

          default:
            logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <add|remove|list> [command]"}));
            break;
        }
        break;

      case "matchman":
      case "mm":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        if (!sender.hasPermission(PERM_MATCHMAN)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_MATCHMAN, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <start|end|speed>"})); return true; }

        player = (Player) sender;
        match = MatchHelper.getMatch(org, player);
        if (match == null) { logger.send(sender, Lang.MATCHES_LIST_NO_MATCHES.replace(null)); return true; }
        int matchSize = MatchHelper.getMatchSize(org, match);
        String matchType = matchSize + "v" + matchSize;

        switch (args[1].toLowerCase()) {
          case "start":
            for (int i = 1; i <= (match.type * 2) - 1; i++) { match.join(player, false); org.getWaitingPlayers().put(player.getName(), match.type); }
            match.countdown = 2;
            logger.send(player, Lang.MATCHMAN_FORCE_START.replace(new String[]{matchType}));
            break;

          case "end":
            match.phase = 1;

            for (Player p : fcManager.getCachedPlayers()) {
              if (match.isInMatch(p)) {
                MatchHelper.leaveMatch(org, p, match, logger, false);
                p.teleport(config.get("lobby") != null ? (Location) config.get("lobby") : p.getWorld().getSpawnLocation());
                p.setScoreboard(plugin.getServer().getScoreboardManager().getNewScoreboard());
              }
            }

            match.time.setScore(-1);

            if (match.cube != null) { match.cube.setHealth(0); match.cube.remove(); match.cube = null; }

            match.scoreRed = 0;
            match.scoreBlue = 0;

            match.redPlayers.clear();
            match.bluePlayers.clear();
            match.teamers.clear();
            match.isRed.clear();
            match.takePlace.clear();
            match.goals.clear();
            match.sugarCooldown.clear();

            org.undoTakePlace(match);
            org.endMatch(player);

            logger.send(sender, Lang.MATCHMAN_FORCE_END.replace(new String[]{matchType}));
            break;

          case "speed":
            player.getInventory().addItem(match.sugar);
            logger.send(sender, Lang.MATCHMAN_SPEED.replace(null));
            break;

          default:
            logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <start|end|speed>"}));
        }
        break;

      case "forceleave":
      case "fl":
        if (!sender.hasPermission(PERM_FORCE_LEAVE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_FORCE_LEAVE, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player>"})); return true; }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

        match = MatchHelper.getMatch(org, target);
        MatchHelper.leaveMatch(org, target, match, logger, true);
        logger.send(sender, Lang.FORCE_LEAVE.replace(new String[]{target.getDisplayName()}));
        break;

      case "help":
      case "h": sendHelp(sender); break;
      default: logger.send(sender, Lang.UNKNOWN_COMMAND.replace(new String[]{label})); break;
    }

    return true;
  }

  private void sendHelp(CommandSender sender) {
    if (sender.hasPermission(PERM_MAIN)) logger.send(sender, Lang.HELP_ADMIN.replace(null));
  }

  private boolean inGameOnly(CommandSender sender) {
    logger.send(sender, Lang.INGAME_ONLY.replace(null));
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.hasPermission(PERM_MAIN)) return Collections.emptyList();

    List<String> completions = new ArrayList<>();

    if (args.length == 1) {
      completions.addAll(Arrays.asList(
          "reload", "toggle", "statset", "setuparena", "set", "undo", "cleararenas", "setlobby",
          "setpracticearea", "spa", "matchman", "mm", "hitdebug", "hits", "commanddisabler", "cd", "forceleave",
          "fl", "ban", "unban", "checkban", "help"
      ));
    } else if (args.length == 2) {
      switch (args[0].toLowerCase()) {
        case "reload": completions.addAll(Arrays.asList("configs", "all")); break;
        case "statset":
        case "forceleave":
        case "fl":
        case "ban":
        case "unban":
        case "checkban": fcManager.getCachedPlayers().forEach(p -> completions.add(p.getName())); break;
        case "setuparena":
        case "set": completions.addAll(Arrays.asList("2v2", "3v3", "4v4")); break;
        case "matchman":
        case "mm": completions.addAll(Arrays.asList("start", "end", "speed")); break;
        case "commanddisabler":
        case "cd": completions.addAll(Arrays.asList("add", "remove", "list")); break;
      }
    } else if (args.length == 3) {
      if (args[0].equalsIgnoreCase("ban")) completions.addAll(Arrays.asList("10s", "30s", "5min", "10min"));
      else if (args[0].equalsIgnoreCase("statset")) completions.addAll(Arrays.asList("wins", "matches", "goals", "assists", "ownGoals", "winstreak", "bestwinstreak"));
    }

    if (!completions.isEmpty()) {
      String lastWord = args[args.length - 1].toLowerCase();
      completions.removeIf(s -> !s.toLowerCase().startsWith(lastWord));
      completions.sort(String.CASE_INSENSITIVE_ORDER);
    }

    return completions;
  }
}
