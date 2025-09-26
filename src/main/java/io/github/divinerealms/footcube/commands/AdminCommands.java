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
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
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
    if (args.length == 0) { sendHelp(sender); return true; }
    if (args[0].equalsIgnoreCase("reload")) {
      if (!sender.hasPermission("footcube.admin")) return noPerm(sender);
      fcManager.reload();
      logger.send(sender, Lang.RELOAD.replace(null));
      return true;
    } else if (args[0].equalsIgnoreCase("toggle")) {
      if (!sender.hasPermission("footcube.admin.toggle")) return noPerm(sender);
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
      return true;
    } else if (args[0].equalsIgnoreCase("ban")) {
      if (!sender.hasPermission("footcube.admin.ban")) return noPerm(sender);
      if (args.length < 3) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " ban <player> <time>"})); return true; }

      Player target = plugin.getServer().getPlayer(args[1]);
      if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

      int seconds;
      try {
        seconds = Utilities.parseTime(args[2]);
      } catch (NumberFormatException exception) {
        logger.send(sender, Lang.USAGE.replace(new String[]{label + " ban <player> <time>"}));
        return true;
      }

      if (seconds <= 0) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " ban <player> <time>"})); return true; }
      long banUntil = System.currentTimeMillis() + (seconds * 1000L);
      org.getLeaveCooldowns().put(target.getUniqueId(), banUntil);

      logger.send(sender, Lang.ADMIN_STRING.replace(null) + target.getDisplayName() + "&c je banovan iz FC na " + seconds + "s.");
      return true;
    } else if (args[0].equalsIgnoreCase("unban")) {
      if (!sender.hasPermission("footcube.admin.unban")) return noPerm(sender);
      if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " unban <player>"})); return true; }

      Player target = plugin.getServer().getPlayer(args[1]);
      if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

      if (org.getLeaveCooldowns().remove(target.getUniqueId()) != null) {
        logger.send(sender, Lang.ADMIN_STRING.replace(null) + target.getDisplayName() + "&a je unbanovan.");
      } else {
        logger.send(sender, Lang.ADMIN_STRING.replace(null) + target.getDisplayName() + "&c nije banovan.");
      }
      return true;
    } else if (args[0].equalsIgnoreCase("statset")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      if (!sender.hasPermission("footcube.admin.statset")) return noPerm(sender);
      if (args.length < 4) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " statset <player> <stat> <amount|clear>"})); return true; }

      Player target = plugin.getServer().getPlayer(args[1]);
      if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

      PlayerData data = dataManager.get(target);
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
        case "wins": case "matches": case "ties": case "goals": case "winstreak": case "bestwinstreak":
          data.set(stat, clear ? 0 : amount);
          break;

        case "all":
          data.set("wins", clear ? 0 : amount);
          data.set("matches", clear ? 0 : amount);
          data.set("ties", clear ? 0 : amount);
          data.set("goals", clear ? 0 : amount);
          data.set("winstreak", clear ? 0 : amount);
          data.set("bestwinstreak", clear ? 0 : amount);
          break;

        default:
          logger.send(sender, Lang.STATSSET_NOT_A_STAT.replace(new String[]{stat}));
          return true;
      }

      dataManager.savePlayerData(target.getName());
      logger.send(sender, Lang.ADMIN_STATSET.replace(new String[]{stat, target.getName(), String.valueOf(amount)}));
      return true;
    } else if (args[0].equalsIgnoreCase("setuparena")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      if (!sender.hasPermission("footcube.admin.setuparena")) return noPerm(sender);
      if (args.length < 2) { logger.send(sender, Lang.USAGE.replace(new String[]{label + " setuparena <2v2|3v3|4v4>"})); return true; }

      Player player = (Player) sender;
      String type = args[1].toLowerCase();
      if (!Arrays.asList("2v2","3v3","4v4").contains(type)) { logger.send(sender, "&cInvalid type. Use 2v2, 3v3, or 4v4."); return true; }
      if (org.getSetupGuy() != null) { logger.send(sender, Lang.SETUP_ARENA_ALREADY_SOMEONE.replace(new String[]{org.getSetupGuy()})); return true; }

      MatchHelper.ArenaData setup = MatchHelper.getArenaData(org, type);
      if (setup == null) { logger.send(sender, Lang.JOIN_INVALIDTYPE.replace(new String[]{type, Lang.OR.replace(null)})); return true; }

      org.setSetupType(setup.size);
      org.setSetupGuy(player.getName());
      logger.send(sender, Lang.SETUP_ARENA_START.replace(null));
      return true;
    } else if (args[0].equalsIgnoreCase("set")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;

      if (!player.getName().equals(org.getSetupGuy())) { logger.send(sender, Lang.ADMIN_STRING.replace(null) + "Not setting up an arena"); return true; }
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
      return true;
    } else if (args[0].equalsIgnoreCase("undo")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;

      if (!player.getName().equals(org.getSetupGuy())) { logger.send(sender, Lang.ADMIN_STRING.replace(null) + "Not setting up an arena"); return true; }

      org.setSetupGuy(null); org.setSetupType(0); org.setSetupLoc(null);
      logger.send(sender, Lang.UNDO.replace(null));
      return true;
    } else if (args[0].equalsIgnoreCase("cleararenas")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      if (!sender.hasPermission("footcube.admin.cleararenas")) return noPerm(sender);

      arenas.set("arenas", null);
      for (String t : Arrays.asList("2v2","3v3","4v4")) {
        arenas.addDefault("arenas." + t + ".amount", 0);
        MatchHelper.ArenaData arenaData = MatchHelper.getArenaData(org, t);
        if (arenaData != null) arenaData.matches = new Match[0];
      }

      configManager.saveConfig("arenas.yml");
      logger.send(sender, Lang.CLEAR_ARENAS_SUCCESS.replace(null));
      return true;
    } else if (args[0].equalsIgnoreCase("setlobby")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      if (!sender.hasPermission("footcube.admin.setlobby")) return noPerm(sender);

      Player player = (Player) sender;
      config.set("lobby", player.getLocation());
      configManager.saveConfig("config.yml");
      logger.send(sender, Lang.PRACTICE_AREA_SET.replace(new String[]{"lobby",
          String.valueOf(player.getLocation().getX()),
          String.valueOf(player.getLocation().getY()),
          String.valueOf(player.getLocation().getZ())}));

      return true;
    } else if (args[0].equalsIgnoreCase("setpracticearea") || args[0].equalsIgnoreCase("spa")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      if (!sender.hasPermission("footcube.admin.setpracticearea")) return noPerm(sender);
      if (args.length < 2) { logger.send(sender, "&cUsage: /fcadmin setpracticearea <name>"); return true; }

      Player player = (Player) sender;
      String name = args[1];
      practice.set("practice-areas." + name, player.getLocation());
      configManager.saveConfig("practice.yml");

      logger.send(sender, Lang.PRACTICE_AREA_SET.replace(new String[]{name,
          String.valueOf(player.getLocation().getX()),
          String.valueOf(player.getLocation().getY()),
          String.valueOf(player.getLocation().getZ())}));

      return true;
    } else if (args[0].equalsIgnoreCase("hitdebug") || args[0].equalsIgnoreCase("hits")) {
      if (!sender.hasPermission("footcube.admin.hitdebug")) return noPerm(sender);
      boolean status = physics.isHitDebug();
      physics.hitDebug = !status;
      logger.send(sender, Lang.BETA_FEATURE.replace(null) + (status ? "§cDisabled §9" : "§aEnabled §9") + "cube hit debug.");
      return true;
    } else if (args[0].equalsIgnoreCase("commanddisabler") || args[0].equalsIgnoreCase("cd")) {
      if (!sender.hasPermission("footcube.admin.commanddisabler")) return noPerm(sender);
      if (args.length < 2) { logger.send(sender, "&cUsage: /fcadmin cd <add|remove|list> [command]"); return true; }

      String action = args[1].toLowerCase();
      switch (action) {
        case "add":
          if (args.length < 3) { logger.send(sender, "&cUsage: /fcadmin cd add <command>"); return true; }
          if (disableCommands.addCommand(args[2])) logger.send(sender, Lang.COMMAND_DISABLER_SUCCESS.replace(new String[]{args[2]}));
          else logger.send(sender, Lang.COMMAND_DISABLER_ALREADY_ADDED.replace(null));
          break;

        case "remove":
          if (args.length < 3) { logger.send(sender, "&cUsage: /fcadmin cd remove <command>"); return true; }
          if (disableCommands.removeCommand(args[2])) logger.send(sender, Lang.COMMAND_DISABLER_SUCCESS_REMOVE.replace(new String[]{args[2]}));
          else logger.send(sender, "&cThis command wasn't even added");
          break;

        case "list":
          logger.send(sender, Lang.COMMAND_DISABLER_LIST.replace(null));
          disableCommands.getCommands().forEach(c -> logger.send(sender, "&7" + c));
          break;

        default:
          logger.send(sender, "&cUnknown command. Usage: add|remove|list");
          break;
      }
      return true;
    } else if (args[0].equalsIgnoreCase("matchman") || args[0].equalsIgnoreCase("mm")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      if (!sender.hasPermission("footcube.admin.matchman")) return noPerm(sender);
      if (args.length < 2) { logger.send(sender, "&cUsage: /fcadmin matchman <start|end|speed>"); return true; }

      Player player = (Player) sender;
      Match match = MatchHelper.getMatch(org, player);
      if (match == null) { logger.send(sender, Lang.BETA_FEATURE.replace(null) + "&cNo match, cancelling"); return true; }
      int matchSize = MatchHelper.getMatchSize(org, match);
      String matchType = matchSize + "v" + matchSize;

      switch (args[1].toLowerCase()) {
        case "start":
          for (int i = 1; i <= (match.type * 2) - 1; i++) { match.join(player, false); org.getWaitingPlayers().put(player.getName(), match.type); }
          match.countdown = 2;
          logger.send(player, Lang.BETA_FEATURE.replace(null) + "Force started a " + matchType + " match.");
          break;

        case "end":
          match.phase = 1;

          for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (match.isInMatch(p)) {
              org.clearInventory(p);
              p.teleport(config.get("lobby") != null ? (Location) config.get("lobby") : p.getWorld().getSpawnLocation());
              p.setScoreboard(plugin.getServer().getScoreboardManager().getNewScoreboard());
            }
          }

          match.time.setScore(-1);

          if (match.cube != null) { match.cube.setHealth(0.0F); match.cube.remove(); match.cube = null; }

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

          logger.send(sender, Lang.BETA_FEATURE.replace(null) + "Forcefully ended a " + matchType + " match.");
          break;

        case "speed":
          player.getInventory().addItem(match.sugar);
          logger.send(sender, Lang.BETA_FEATURE.replace(null) + "Spawned speed buff for you.");
          break;

        default:
          logger.send(sender, Lang.BETA_FEATURE.replace(null) + "Unknown usage.");
      }

      return true;
    } else if (args[0].equalsIgnoreCase("forceleave")) {
      if (!sender.hasPermission("footcube.admin.forceleave")) return noPerm(sender);
      if (args.length < 2) { logger.send(sender, "&cUsage: /fcadmin forceleave <player>"); return true; }

      Player target = plugin.getServer().getPlayer(args[1]);
      if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

      Match match = MatchHelper.getMatch(org, target);
      MatchHelper.leaveMatch(org, target, match, logger, true);
      logger.send(sender, Lang.BETA_FEATURE.replace(null) + "&aForce removed " + target.getDisplayName() + "&a from all matches/lobbies.");

      return true;
    } else sendHelp(sender);

    return true;
  }

  private void sendHelp(CommandSender sender) {
    logger.send(sender, Lang.HELP.replace(new String[]{Lang.OR.replace(null)}));
    if (sender.hasPermission("footcube.admin")) logger.send(sender, Lang.HELP_ADMIN.replace(new String[]{Lang.OR.replace(null)}));
  }

  private boolean inGameOnly(CommandSender sender) {
    logger.send(sender, ChatColor.RED + "This command can only be used by players.");
    return true;
  }

  private boolean noPerm(CommandSender sender) {
    logger.send(sender, ChatColor.RED + "You don't have permission.");
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    List<String> completions = new ArrayList<>();
    if (args.length == 1) {
      completions.addAll(Arrays.asList("reload", "toggle", "statset", "setuparena", "set", "undo", "cleararenas", "setlobby", "setpracticearea", "matchman", "hitdebug", "cd", "forceleave", "ban", "unban", "help"));
    } else if (args.length == 2) {
      switch (args[0].toLowerCase()) {
        case "statset":
        case "forceleave":
        case "ban":
        case "unban":
          plugin.getServer().getOnlinePlayers().forEach(p -> completions.add(p.getName()));
          break;
        case "setuparena":
        case "set": completions.addAll(Arrays.asList("2v2","3v3","4v4")); break;
        case "matchman": completions.addAll(Arrays.asList("start","end","speed")); break;
        case "cd": completions.addAll(Arrays.asList("add","remove","list")); break;
      }
    } else if (args.length == 3) {
      if (args[0].equalsIgnoreCase("ban")) completions.addAll(Arrays.asList("10s", "30s", "5min", "10min"));
    }

    String lastWord = args[args.length-1].toLowerCase();
    completions.removeIf(s -> !s.toLowerCase().startsWith(lastWord));
    return completions;
  }
}
