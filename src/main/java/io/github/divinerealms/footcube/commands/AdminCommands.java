package io.github.divinerealms.footcube.commands;

import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.arena.ArenaManager;
import io.github.divinerealms.footcube.matchmaking.ban.BanManager;
import io.github.divinerealms.footcube.matchmaking.util.MatchConstants;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.DisableCommands;
import io.github.divinerealms.footcube.utils.Logger;
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
    if (args.length == 0) { logger.send(sender, Lang.UNKNOWN_COMMAND.replace(new String[]{label})); return true; }

    String sub = args[0].toLowerCase();
    Player player;
    Player target;
    PlayerData playerData;

    switch (sub) {
      case "reload":
        if (args.length == 1) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <configs|all>"})); return true; }
        if (!sender.hasPermission(PERM_ADMIN)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_ADMIN, label + " " + sub})); return true; }

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
        boolean state = matchManager.getData().isMatchesEnabled();
        matchManager.getData().setMatchesEnabled(!state);
        if (!matchManager.getData().isMatchesEnabled()) matchManager.clearLobbiesAndQueues();
        logger.send(sender, Lang.FC_TOGGLE.replace(new String[]{state ? Lang.OFF.replace(null) : Lang.ON.replace(null)}));
        break;

      case "ban":
        if (!sender.hasPermission(PERM_BAN)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_BAN, label + " " + sub})); return true; }
        if (args.length < 3) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player> <time>"})); return true; }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

        try {
          int seconds = Utilities.parseTime(args[2]);
          if (seconds <= 0) {
            logger.send(sender, Lang.USAGE.replace(new String[]{label + " ban <player> <time>"}));
            return true;
          }

          banManager.banPlayer(target, seconds * 1000L);
          logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + target.getDisplayName() + "&c je banovan iz FC na &e" + Utilities.formatTime(seconds) + "&c.");
        } catch (NumberFormatException exception) {
          logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player> <time>"}));
        }
        break;

      case "unban":
        if (!sender.hasPermission(PERM_UNBAN)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_UNBAN, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player>"})); return true; }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

        banManager.unbanPlayer(target);
        logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + target.getDisplayName() + "&a je unbanovan.");
        break;

      case "checkban":
        if (!sender.hasPermission(PERM_BAN)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_BAN, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player>"})); return true; }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

        if (banManager.isBanned(target)) {
          long banTime = banManager.getBannedPlayers().get(target.getUniqueId());
          long secondsLeft = (banTime - System.currentTimeMillis()) / 1000L;
          logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + target.getDisplayName() + "&c je banovan još &e" + Utilities.formatTime(secondsLeft) + "&c.");
        } else logger.send(sender, Lang.PREFIX_ADMIN.replace(null) + target.getDisplayName() + "&c nije banovan.");
        break;

      case "statset":
        if (!sender.hasPermission(PERM_STAT_SET)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_STAT_SET, label + " " + sub})); return true; }
        if (args.length < 4) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player> <stat> <amount|clear>"})); return true; }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

        playerData = dataManager.get(target);
        String stat = args[2].toLowerCase();
        boolean clear = args[3].equalsIgnoreCase("clear");
        int amount = 0;

        if (!clear) {
          try {
            amount = Integer.parseInt(args[3]);
          } catch (NumberFormatException e) {
            logger.send(sender, Lang.STATSSET_IS_NOT_A_NUMBER.replace(new String[]{args[3]}));
            return true;
          }
        }

        if (Arrays.asList("wins", "matches", "ties", "goals", "assists", "owngoals", "winstreak", "bestwinstreak").contains(stat)) {
          playerData.set(stat, clear ? 0 : amount);
        } else if (stat.equals("all")) {
          int finalAmount = amount;
          Arrays.asList("wins", "matches", "ties", "goals", "assists", "owngoals", "winstreak", "bestwinstreak")
              .forEach(s -> playerData.set(s, clear ? 0 : finalAmount));
        } else {
          logger.send(sender, Lang.STATSSET_NOT_A_STAT.replace(new String[]{stat}));
          return true;
        }

        dataManager.savePlayerData(target.getName());
        logger.send(sender, Lang.ADMIN_STATSET.replace(new String[]{stat, target.getName(), String.valueOf(amount)}));
        break;

      case "setuparena":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_SETUP_ARENA)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_SETUP_ARENA, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <2v2|3v3|4v4>"})); return true; }

        String type = args[1].toLowerCase();
        int arenaType;
        switch (type) {
          case "2v2":
            arenaType = MatchConstants.TWO_V_TWO;
            break;

          case "3v3":
            arenaType = MatchConstants.THREE_V_THREE;
            break;

          case "4v4":
            arenaType = MatchConstants.FOUR_V_FOUR;
            break;

          default:
            logger.send(player, "&cInvalid type. Use 2v2, 3v3, or 4v4.");
            return true;
        }

        arenaManager.getSetupWizards().put(player, new ArenaManager.ArenaSetup(arenaType));
        logger.send(player, Lang.SETUP_ARENA_START.replace(null));
        break;

      case "set":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_SETUP_ARENA)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_SETUP_ARENA, label + " " + sub})); return true; }

        ArenaManager.ArenaSetup setup = arenaManager.getSetupWizards().get(player);
        if (setup == null) { logger.send(player, Lang.PREFIX_ADMIN.replace(null) + "You are not setting up an arena."); return true; }

        if (setup.getBlueSpawn() == null) {
          setup.setBlueSpawn(player.getLocation());
          logger.send(player, Lang.SETUP_ARENA_FIRST_SET.replace(null));
        } else {
          arenaManager.createArena(setup.getType(), setup.getBlueSpawn(), player.getLocation());
          arenaManager.getSetupWizards().remove(player);
          logger.send(player, Lang.SETUP_ARENA_SUCCESS.replace(null));
        }

        break;

      case "undo":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_SETUP_ARENA)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_SETUP_ARENA, label + " " + sub})); return true; }

        if (arenaManager.getSetupWizards().remove(player) != null) logger.send(player, Lang.UNDO.replace(null));
        else logger.send(player, Lang.PREFIX_ADMIN.replace(null) + "You are not setting up an arena.");
        break;

      case "cleararenas":
        if (!sender.hasPermission(PERM_CLEAR_ARENAS)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_CLEAR_ARENAS, label + " " + sub})); return true; }

        arenaManager.clearArenas();
        logger.send(sender, Lang.CLEAR_ARENAS_SUCCESS.replace(null));
        break;

      case "setlobby":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        if (!sender.hasPermission(PERM_SET_LOBBY)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_SET_LOBBY, label + " " + sub})); return true; }

        player = (Player) sender;
        config.set("lobby", player.getLocation());
        configManager.saveConfig("config.yml");
        logger.send(sender, Lang.PRACTICE_AREA_SET.replace(new String[]{"lobby", String.valueOf(player.getLocation().getX()), String.valueOf(player.getLocation().getY()), String.valueOf(player.getLocation().getZ())}));
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
        logger.send(sender, Lang.PRACTICE_AREA_SET.replace(new String[]{
            name, String.valueOf(player.getLocation().getX()), String.valueOf(player.getLocation().getY()), String.valueOf(player.getLocation().getZ())
        }));
        break;

      case "hitsdebug":
      case "hits":
        if (!sender.hasPermission(PERM_HIT_DEBUG)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_HIT_DEBUG, label + " " + sub})); return true; }

        boolean status = data.isHitDebugEnabled();
        data.hitDebugEnabled = !status;
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
        player = (Player) sender;
        if (!sender.hasPermission(PERM_MATCHMAN)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_MATCHMAN, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <start|end>"})); return true; }
        switch (args[1].toLowerCase()) {
          case "start":
            matchManager.forceStartMatch(player);
            break;

          case "end":
            Optional<Match> match = matchManager.getMatch(player);
            if (match.isPresent()) {
              matchManager.endMatch(match.get());
              logger.send(sender, Lang.MATCHMAN_FORCE_END.replace(new String[]{match.get().getArena().getType() + "v" + match.get().getArena().getType()}));
            } else logger.send(sender, Lang.MATCHES_LIST_NO_MATCHES.replace(null));
            break;

          default:
            logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <start|end>"}));
        }
        break;

      case "forceleave":
      case "fl":
        if (!sender.hasPermission(PERM_FORCE_LEAVE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_FORCE_LEAVE, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <player>"})); return true; }

        target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

        matchManager.leaveMatch(target);
        logger.send(sender, Lang.FORCE_LEAVE.replace(new String[]{target.getDisplayName()}));
        break;

      case "setbutton":
      case "sb":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!sender.hasPermission(PERM_SETBUTON)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_SETBUTON, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " " + sub + " <spawn|clearcube>"})); return true; }

        Block targetBlock = player.getTargetBlock((Set<Material>) null, 5);
        if (targetBlock == null || targetBlock.getType() == Material.AIR) { logger.send(player, Lang.SET_BLOCK_TOO_FAR.replace(null)); return true; }

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
            logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <spawn|clearcube>"}));
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
        logger.send(player, Lang.SET_BLOCK_SUCCESS.replace(new String[]{buttonType}));
        break;

      case "help":
      case "h":
        sendHelp(sender);
        break;

      default:
        logger.send(sender, Lang.UNKNOWN_COMMAND.replace(new String[]{label}));
        break;
    }

    if (sender instanceof Player) system.recordPlayerAction((Player) sender);
    return true;
  }

  private void sendHelp(CommandSender sender) {
    if (sender.hasPermission(PERM_ADMIN)) logger.send(sender, Lang.HELP_ADMIN.replace(null));
  }

  private boolean inGameOnly(CommandSender sender) {
    logger.send(sender, Lang.INGAME_ONLY.replace(null));
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.hasPermission(PERM_ADMIN)) return Collections.emptyList();

    List<String> completions = new ArrayList<>();
    if (args.length == 1) {
      completions.addAll(Arrays.asList(
          "reload", "reloadtab", "toggle", "statset", "setuparena", "set", "undo", "cleararenas", "setlobby",
          "setpracticearea", "spa", "matchman", "mm", "hitdebug", "hits", "commanddisabler", "cd", "forceleave",
          "fl", "ban", "unban", "checkban", "setbutton", "sb", "help", "h"
      ));
    } else if (args.length == 2) {
      switch (args[0].toLowerCase()) {
        case "reload":
          completions.addAll(Arrays.asList("configs", "all"));
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
      }
    } else if (args.length == 3) {
      if (args[0].equalsIgnoreCase("ban")) {
        completions.addAll(Arrays.asList("10s", "30s", "5min", "10min"));
      } else if (args[0].equalsIgnoreCase("statset")) {
        completions.addAll(Arrays.asList("wins", "matches", "goals", "assists", "owngoals", "winstreak", "bestwinstreak"));
      }
    }

    if (!completions.isEmpty()) {
      String lastWord = args[args.length - 1].toLowerCase();
      completions.removeIf(s -> !s.toLowerCase().startsWith(lastWord));
      completions.sort(String.CASE_INSENSITIVE_ORDER);
    }

    if (sender instanceof Player) system.recordPlayerAction((Player) sender);
    return completions;
  }
}
