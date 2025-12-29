package io.github.divinerealms.footcube.commands;

import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.managers.TaskManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.arena.ArenaManager;
import io.github.divinerealms.footcube.matchmaking.ban.BanManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.tasks.BaseTask;
import io.github.divinerealms.footcube.utils.DisableCommands;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.TaskStats;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.material.Button;
import org.bukkit.material.Wool;

import java.util.*;

import static io.github.divinerealms.footcube.configs.Lang.*;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.*;
import static io.github.divinerealms.footcube.utils.Permissions.*;

public class AdminCommands implements CommandExecutor, TabCompleter {
  private final FCManager fcManager;
  private final FootCube plugin;
  private final Logger logger;
  private final MatchManager matchManager;
  private final ArenaManager arenaManager;
  private final BanManager banManager;
  private final DisableCommands disableCommands;
  private final ConfigManager configManager;
  private final PlayerDataManager dataManager;
  private final PhysicsData data;
  private final PhysicsSystem system;
  private final FileConfiguration config, practice;

  public AdminCommands(FCManager fcManager, DisableCommands disableCommands) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.logger = fcManager.getLogger();
    this.matchManager = fcManager.getMatchManager();
    this.arenaManager = matchManager.getArenaManager();
    this.banManager = matchManager.getBanManager();
    this.disableCommands = disableCommands;
    this.configManager = fcManager.getConfigManager();
    this.dataManager = fcManager.getDataManager();
    this.data = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
    this.config = configManager.getConfig("config.yml");
    this.practice = configManager.getConfig("practice.yml");
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      logger.send(sender, UNKNOWN_COMMAND, label);
      return true;
    }

    String sub = args[0].toLowerCase();
    Player player;
    Player target;
    PlayerData playerData;

    switch (sub) {
      case "reload":
        if (args.length == 1) {
          logger.send(sender, USAGE, label + " " + sub + " <configs|all>");
          return true;
        }

        if (!sender.hasPermission(PERM_ADMIN)) {
          logger.send(sender, NO_PERM, PERM_ADMIN, label + " " + sub);
          return true;
        }

        switch (args[1].toLowerCase()) {
          case "configs":
            fcManager.getConfigManager().reloadAllConfigs();
            logger.send(sender, RELOAD, "configs");
            return true;

          case "all":
            fcManager.reload();
            logger.send(sender, RELOAD, "all");
            return true;

          case "arenas":
            arenaManager.reloadArenas();
            logger.send(sender, RELOAD, "arenas");
            return true;

          default:
            logger.send(sender, USAGE, label + " " + sub + " <configs|all>");
            return true;
        }

      case "tasks":
        if (!sender.hasPermission(PERM_ADMIN)) {
          logger.send(sender, NO_PERM, PERM_ADMIN, label + " " + sub);
          return true;
        }

        TaskManager taskManager = fcManager.getTaskManager();

        if (args.length > 1) {
          if (args[1].equalsIgnoreCase("restart")) {
            taskManager.restart();
            logger.send(sender, TASKS_RESTART);
            return true;
          } else {
            if (args[1].equalsIgnoreCase("reset")) {
              taskManager.resetAllStats();
              logger.send(sender, TASKS_RESET_STATS);
              return true;
            }
          }
        }

        logger.send(sender, TASKS_REPORT_HEADER,
            String.valueOf(taskManager.getRunningTaskCount()),
            String.valueOf(taskManager.getTaskCount())
        );

        for (BaseTask task : taskManager.getTasks()) {
          double average = task.getAverageExecutionTime();
          String status = task.isRunning()
                          ? "&a✔"
                          : "&c✘";
          String timeColor = getColorForTime(average);

          logger.send(sender, TASKS_REPORT_ENTRY,
              status, task.getTaskName(),
              timeColor + String.format("%.3f", average),
              String.valueOf(task.getTotalExecutions())
          );
        }

        TaskStats stats = taskManager.getStats();
        double totalAverage = stats.getAveragePerTask();
        logger.send(sender, TASKS_REPORT_FOOTER,
            getColorForTime(totalAverage) + String.format("%.3f", totalAverage)
        );
        break;

      case "toggle":
        if (!sender.hasPermission(PERM_TOGGLE)) {
          logger.send(sender, NO_PERM, PERM_TOGGLE, label + " " + sub);
          return true;
        }

        boolean state = matchManager.getData().isMatchesEnabled();
        matchManager.getData().setMatchesEnabled(!state);
        if (!matchManager.getData().isMatchesEnabled()) {
          matchManager.clearLobbiesAndQueues();
        }

        logger.send(sender, FC_TOGGLE, state
                                       ? OFF.toString()
                                       : ON.toString());
        break;

      case "ban":
        if (!sender.hasPermission(PERM_BAN)) {
          logger.send(sender, NO_PERM, PERM_BAN, label + " " + sub);
          return true;
        }

        if (args.length < 3) {
          logger.send(sender, USAGE, label + " " + sub + " <player> <time>");
          return true;
        }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
          logger.send(sender, PLAYER_NOT_FOUND);
          return true;
        }

        try {
          int seconds = Utilities.parseTime(args[2]);
          if (seconds <= 0) {
            logger.send(sender, USAGE, label + " ban <player> <time>");
            return true;
          }

          banManager.banPlayer(target, seconds * 1000L);
          logger.send(sender, PREFIX_ADMIN + target.getDisplayName()
                              + "&c je banovan iz FC na &e" + Utilities.formatTime(seconds) + "&c.");
        } catch (NumberFormatException exception) {
          logger.send(sender, USAGE, label + " " + sub + " <player> <time>");
        }
        break;

      case "unban":
        if (!sender.hasPermission(PERM_UNBAN)) {
          logger.send(sender, NO_PERM, PERM_UNBAN, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(sender, USAGE, label + " " + sub + " <player>");
          return true;
        }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
          logger.send(sender, PLAYER_NOT_FOUND);
          return true;
        }

        banManager.unbanPlayer(target);
        logger.send(sender, PREFIX_ADMIN + target.getDisplayName() + "&a je unbanovan.");
        break;

      case "checkban":
        if (!sender.hasPermission(PERM_BAN)) {
          logger.send(sender, NO_PERM, PERM_BAN, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(sender, USAGE, label + " " + sub + " <player>");
          return true;
        }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
          logger.send(sender, PLAYER_NOT_FOUND);
          return true;
        }

        if (banManager.isBanned(target)) {
          long banTime = banManager.getBannedPlayers().get(target.getUniqueId());
          long secondsLeft = (banTime - System.currentTimeMillis()) / 1000L;
          logger.send(sender,
              PREFIX_ADMIN + target.getDisplayName() + "&c je banovan još &e" + Utilities.formatTime(secondsLeft) +
              "&c.");
        } else {
          logger.send(sender, PREFIX_ADMIN + target.getDisplayName() + "&c nije banovan.");
        }
        break;

      case "statset":
        if (!sender.hasPermission(PERM_STAT_SET)) {
          logger.send(sender, NO_PERM, PERM_STAT_SET, label + " " + sub);
          return true;
        }

        if (args.length < 4) {
          logger.send(sender, USAGE, label + " " + sub + " <player> <stat> <amount|clear>");
          return true;
        }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
          logger.send(sender, PLAYER_NOT_FOUND);
          return true;
        }

        playerData = dataManager.get(target);
        String stat = args[2].toLowerCase();
        boolean clear = args[3].equalsIgnoreCase("clear");
        int amount = 0;

        if (!clear) {
          try {
            amount = Integer.parseInt(args[3]);
          } catch (NumberFormatException e) {
            logger.send(sender, STATSSET_IS_NOT_A_NUMBER, args[3]);
            return true;
          }
        }

        if (Arrays.asList("wins", "matches", "ties", "goals", "assists", "owngoals", "winstreak",
            "bestwinstreak").contains(stat)) {
          playerData.set(stat, clear
                               ? 0
                               : amount);
        } else {
          if (stat.equals("all")) {
            int finalAmount = amount;
            Arrays.asList("wins", "matches", "ties", "goals", "assists", "owngoals", "winstreak", "bestwinstreak")
                .forEach(s -> playerData.set(s, clear
                                                ? 0
                                                : finalAmount));
          } else {
            logger.send(sender, STATSSET_NOT_A_STAT, stat);
            return true;
          }
        }

        dataManager.savePlayerData(target.getName());
        logger.send(sender, ADMIN_STATSET, stat, target.getName(), String.valueOf(amount));
        break;

      case "setuparena":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_SETUP_ARENA)) {
          logger.send(player, NO_PERM, PERM_SETUP_ARENA, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(player, USAGE, label + " " + sub + " <2v2|3v3|4v4>");
          return true;
        }

        String type = args[1].toLowerCase();
        int arenaType;
        switch (type) {
          case "2v2":
            arenaType = TWO_V_TWO;
            break;

          case "3v3":
            arenaType = THREE_V_THREE;
            break;

          case "4v4":
            arenaType = FOUR_V_FOUR;
            break;

          default:
            logger.send(player, "&cInvalid type. Use 2v2, 3v3, or 4v4.");
            return true;
        }

        arenaManager.getSetupWizards().put(player, new ArenaManager.ArenaSetup(arenaType));
        logger.send(player, SETUP_ARENA_START);
        break;

      case "set":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_SETUP_ARENA)) {
          logger.send(player, NO_PERM, PERM_SETUP_ARENA, label + " " + sub);
          return true;
        }

        ArenaManager.ArenaSetup setup = arenaManager.getSetupWizards().get(player);
        if (setup == null) {
          logger.send(player, PREFIX_ADMIN + "You are not setting up an arena.");
          return true;
        }

        if (setup.getBlueSpawn() == null) {
          setup.setBlueSpawn(player.getLocation());
          logger.send(player, SETUP_ARENA_FIRST_SET);
        } else {
          arenaManager.createArena(setup.getType(), setup.getBlueSpawn(), player.getLocation());
          arenaManager.getSetupWizards().remove(player);
          logger.send(player, SETUP_ARENA_SUCCESS);
        }
        break;

      case "undo":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_SETUP_ARENA)) {
          logger.send(player, NO_PERM, PERM_SETUP_ARENA, label + " " + sub);
          return true;
        }

        if (arenaManager.getSetupWizards().remove(player) != null) {
          logger.send(player, UNDO);
        } else {
          logger.send(player, PREFIX_ADMIN + "You are not setting up an arena.");
        }
        break;

      case "clear":
        if (!sender.hasPermission(PERM_CLEAR)) {
          logger.send(sender, NO_PERM, PERM_CLEAR, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(sender, USAGE, label + sub + " <arenas|stats>");
          return true;
        }

        switch (args[1].toLowerCase()) {
          case "arenas":
            if (!sender.hasPermission(PERM_CLEAR_ARENAS)) {
              logger.send(sender, NO_PERM, PERM_CLEAR_ARENAS, label + " " + sub);
              return true;
            }

            arenaManager.clearArenas();
            logger.send(sender, CLEAR_ARENAS_SUCCESS);
            return true;

          case "stats":
            if (!sender.hasPermission(PERM_CLEAR_STATS)) {
              logger.send(sender, NO_PERM, PERM_CLEAR_STATS, label + " " + sub);
              return true;
            }

            dataManager.clearAllStats();
            logger.send(sender, CLEAR_STATS_SUCCESS);
            return true;

          default:
            logger.send(sender, USAGE, label + sub + " <arenas|stats>");
            return true;
        }

      case "setlobby":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        if (!sender.hasPermission(PERM_SET_LOBBY)) {
          logger.send(sender, NO_PERM, PERM_SET_LOBBY, label + " " + sub);
          return true;
        }

        player = (Player) sender;
        config.set("lobby", player.getLocation());
        configManager.saveConfig("config.yml");
        logger.send(sender, PRACTICE_AREA_SET, "lobby",
            String.valueOf(player.getLocation().getX()),
            String.valueOf(player.getLocation().getY()),
            String.valueOf(player.getLocation().getZ())
        );
        break;

      case "setpracticearea":
      case "spa":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        if (!sender.hasPermission(PERM_SET_PRACTICE_AREA)) {
          logger.send(sender, NO_PERM, PERM_SET_PRACTICE_AREA, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(sender, USAGE, label + " " + sub + " <name>");
          return true;
        }

        player = (Player) sender;
        String name = args[1];
        practice.set("practice-areas." + name, player.getLocation());
        configManager.saveConfig("practice.yml");
        logger.send(sender, PRACTICE_AREA_SET, name,
            String.valueOf(player.getLocation().getX()),
            String.valueOf(player.getLocation().getY()),
            String.valueOf(player.getLocation().getZ())
        );
        break;

      case "hitsdebug":
      case "hits":
        if (!sender.hasPermission(PERM_HIT_DEBUG)) {
          logger.send(sender, NO_PERM, PERM_HIT_DEBUG, label + " " + sub);
          return true;
        }

        boolean status = data.isHitDebugEnabled();
        data.hitDebugEnabled = !status;
        logger.send(sender, TOGGLES_HIT_DEBUG, status
                                               ? OFF.toString()
                                               : ON.toString());
        break;

      case "commanddisabler":
      case "cd":
        if (!sender.hasPermission(PERM_COMMAND_DISABLER)) {
          logger.send(sender, NO_PERM, PERM_COMMAND_DISABLER, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(sender, USAGE, label + " " + sub + " <add|remove|list> [command]");
          return true;
        }

        String action = args[1].toLowerCase();
        switch (action) {
          case "add":
            if (args.length < 3) {
              logger.send(sender, USAGE, label + " " + sub + " add <command>");
              return true;
            }

            if (disableCommands.addCommand(args[2])) {
              logger.send(sender, COMMAND_DISABLER_SUCCESS, args[2]);
            } else {
              logger.send(sender, COMMAND_DISABLER_ALREADY_ADDED);
            }
            break;

          case "remove":
            if (args.length < 3) {
              logger.send(sender, USAGE, label + " " + sub + " remove <command>");
              return true;
            }

            if (disableCommands.removeCommand(args[2])) {
              logger.send(sender, COMMAND_DISABLER_SUCCESS_REMOVE, args[2]);
            } else {
              logger.send(sender, COMMAND_DISABLER_WASNT_ADDED);
            }
            break;

          case "list":
            logger.send(sender, COMMAND_DISABLER_LIST);
            disableCommands.getCommands().forEach(c -> logger.send(sender, "&7" + c));
            break;

          default:
            logger.send(sender, USAGE, label + " " + sub + " <add|remove|list> [command]");
            break;
        }
        break;

      case "matchman":
      case "mm":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!sender.hasPermission(PERM_MATCHMAN)) {
          logger.send(sender, NO_PERM, PERM_MATCHMAN, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(sender, USAGE, label + " " + sub + " <start|end>");
          return true;
        }

        switch (args[1].toLowerCase()) {
          case "start":
            matchManager.forceStartMatch(player);
            break;

          case "end":
            Optional<Match> matchOptional = matchManager.getMatch(player);
            if (matchOptional.isPresent()) {
              Match match = matchOptional.get();

              if (match.getPhase() == MatchPhase.LOBBY || match.getPhase() == MatchPhase.STARTING) {
                matchManager.endMatch(match);
              } else {
                match.setPhase(MatchPhase.ENDED);
              }

              logger.send(sender, MATCHMAN_FORCE_END, match.getArena().getType() + "v" + match.getArena().getType());
            } else {
              logger.send(sender, MATCHES_LIST_NO_MATCHES);
            }
            break;

          default:
            logger.send(sender, USAGE, label + " " + sub + " <start|end>");
        }
        break;

      case "forceleave":
      case "fl":
        if (!sender.hasPermission(PERM_FORCE_LEAVE)) {
          logger.send(sender, NO_PERM, PERM_FORCE_LEAVE, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(sender, USAGE, label + " " + sub + " <player>");
          return true;
        }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
          logger.send(sender, PLAYER_NOT_FOUND);
          return true;
        }

        matchManager.leaveMatch(target);
        logger.send(sender, FORCE_LEAVE, target.getDisplayName());
        break;

      case "setbutton":
      case "sb":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!sender.hasPermission(PERM_SETBUTON)) {
          logger.send(sender, NO_PERM, PERM_SETBUTON, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(sender, USAGE, label + " " + sub + " <spawn|clearcube>");
          return true;
        }

        Block targetBlock = player.getTargetBlock((Set<Material>) null, 5);
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
          logger.send(player, SET_BLOCK_TOO_FAR);
          return true;
        }

        targetBlock.setType(Material.WOOL);
        BlockState targetBlockState = targetBlock.getState();
        String buttonType = args[1].toLowerCase();
        switch (buttonType) {
          case "spawn":
            targetBlockState.setData(new Wool(DyeColor.LIME));
            break;

          case "clearcube":
            targetBlockState.setData(new Wool(DyeColor.RED));
            break;

          default:
            logger.send(player, USAGE, label + " " + sub + " <spawn|clearcube>");
            return true;
        }

        targetBlockState.update(true);
        Block aboveTargetBlock = targetBlock.getRelative(BlockFace.UP);
        aboveTargetBlock.setType(Material.STONE_BUTTON);
        BlockState aboveTargetBlockState = aboveTargetBlock.getState();
        Button buttonData = new Button();
        buttonData.setFacingDirection(BlockFace.UP);
        aboveTargetBlockState.setData(buttonData);
        aboveTargetBlockState.update(true);
        logger.send(player, SET_BLOCK_SUCCESS, buttonType);
        break;

      case "help":
      case "h":
        sendHelp(sender);
        break;

      default:
        logger.send(sender, UNKNOWN_COMMAND, label);
        break;
    }

    if (sender instanceof Player) {
      system.recordPlayerAction((Player) sender);
    }
    return true;
  }

  private void sendHelp(CommandSender sender) {
    if (sender.hasPermission(PERM_ADMIN)) {
      logger.send(sender, HELP_ADMIN);
    }
  }

  private boolean inGameOnly(CommandSender sender) {
    logger.send(sender, INGAME_ONLY);
    return true;
  }

  private String getColorForTime(double ms) {
    if (ms < 0.05) {
      return "&a";
    }
    if (ms < 0.15) {
      return "&e";
    }
    return "&c";
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.hasPermission(PERM_ADMIN)) {
      return Collections.emptyList();
    }

    List<String> completions = new ArrayList<>();
    if (args.length == 1) {
      completions.addAll(Arrays.asList(
          "reload", "tasks", "reloadtab", "toggle", "statset", "setuparena", "set", "undo", "clear",
          "setlobby", "setpracticearea", "spa", "matchman", "mm", "hitsdebug", "hits", "commanddisabler",
          "cd", "forceleave", "fl", "ban", "unban", "checkban", "setbutton", "sb", "help", "h"
      ));
    } else {
      if (args.length == 2) {
        switch (args[0].toLowerCase()) {
          case "reload":
            completions.addAll(Arrays.asList("configs", "all", "arenas"));
            break;

          case "tasks":
            completions.addAll(Arrays.asList("restart", "reset"));
            break;

          case "statset":
          case "forceleave":
          case "fl":
          case "ban":
          case "unban":
          case "checkban":
            fcManager.getCachedPlayers().forEach(p -> completions.add(p.getName()));
            break;

          case "setuparena":
            completions.addAll(Arrays.asList("2v2", "3v3", "4v4"));
            break;

          case "matchman":
          case "mm":
            completions.addAll(Arrays.asList("start", "end"));
            break;

          case "commanddisabler":
          case "cd":
            completions.addAll(Arrays.asList("add", "remove", "list"));
            break;

          case "setbutton":
          case "sb":
            completions.addAll(Arrays.asList("spawn", "clearcube"));
            break;

          case "clear":
            completions.addAll(Arrays.asList("arenas", "stats"));
            break;
        }
      } else {
        if (args.length == 3) {
          if (args[0].equalsIgnoreCase("ban")) {
            completions.addAll(Arrays.asList("10s", "30s", "5min", "10min"));
          } else {
            if (args[0].equalsIgnoreCase("statset")) {
              completions.addAll(
                  Arrays.asList("wins", "matches", "goals", "assists", "owngoals", "winstreak", "bestwinstreak"));
            }
          }
        }
      }
    }

    if (!completions.isEmpty()) {
      String lastWord = args[args.length - 1].toLowerCase();
      completions.removeIf(s -> !s.toLowerCase().startsWith(lastWord));
      completions.sort(String.CASE_INSENSITIVE_ORDER);
    }

    if (sender instanceof Player) {
      system.recordPlayerAction((Player) sender);
    }
    return completions;
  }
}
