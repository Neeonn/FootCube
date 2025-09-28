package io.github.divinerealms.footcube.commands;

import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Match;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.core.Physics;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.MatchHelper;
import io.github.divinerealms.footcube.utils.PlayerSoundSettings;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FCCommand implements CommandExecutor, TabCompleter {
  private final FCManager fcManager;
  private final FootCube plugin;
  private final Physics physics;
  private final Logger logger;
  private final Organization org;
  private final FileConfiguration arenas;
  private final PlayerDataManager dataManager;

  private static final String PERM_MAIN = "footcube";
  private static final String PERM_CUBE = PERM_MAIN + ".cube";
  private static final String PERM_CLEAR_CUBE = PERM_MAIN + ".clearcube";
  private static final String PERM_SET_SOUND = PERM_MAIN + ".sound";
  private static final String PERM_ADMIN = PERM_MAIN + ".admin";

  public FCCommand(FCManager fcManager) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.physics = fcManager.getPhysics();
    this.logger = fcManager.getLogger();
    this.org = fcManager.getOrg();
    this.arenas = fcManager.getConfigManager().getConfig("arenas.yml");
    this.dataManager = fcManager.getDataManager();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      logger.send(sender, Lang.HELP.replace(null));
      if (sender.hasPermission("footcube.admin")) logger.send(sender, Lang.HELP_ADMIN.replace(null));
      return true;
    }

    String sub = args[0].toLowerCase();
    if (sub.equalsIgnoreCase("join")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;
      if (!physics.isMatchesEnabled()) { logger.send(player, Lang.FC_DISABLED.replace(null)); return true; }
      if (org.isBanned(player)) return true;

      String matchType = args[1];
      if (matchType == null) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <2v2|3v3|4v4>"})); return true; }
      if (org.isInGame(player)) { logger.send(player, Lang.JOIN_ALREADYINGAME.replace(null)); return true; }

      MatchHelper.ArenaData data = MatchHelper.getArenaData(org, matchType);
      if (data == null) { logger.send(player, Lang.JOIN_INVALIDTYPE.replace(new String[]{matchType, Lang.OR.replace(null)})); return true; }
      if (arenas.getInt("arenas." + matchType + ".amount", 0) == 0) { logger.send(player, Lang.JOIN_NOARENA.replace(null)); return true; }

      org.removeTeam(player);
      data.matches[data.lobby].join(player, false);
      org.getWaitingPlayers().put(player.getName(), data.size);

      if (player.getAllowFlight()) { player.setFlying(false); player.setAllowFlight(false); }
      if (player.getGameMode() != GameMode.SURVIVAL) player.setGameMode(GameMode.SURVIVAL);
      return true;
    } else if (sub.equalsIgnoreCase("leave")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;
      if (!org.isInGame(player)) { logger.send(player, Lang.LEAVE_NOT_INGAME.replace(null)); return true; }

      Match match = MatchHelper.getMatch(org, player);
      if (match != null) {
        int playerScore, opponentScore;

        if (match.redPlayers.contains(player)) {
          playerScore = match.scoreRed;
          opponentScore = match.scoreBlue;
        } else {
          playerScore = match.scoreBlue;
          opponentScore = match.scoreRed;
        }

        boolean freeLeave = match.phase == 2 || playerScore == opponentScore || playerScore > opponentScore;
        if (!freeLeave) {
          fcManager.getEconomy().withdrawPlayer(player, 200);
          long banUntil = System.currentTimeMillis() + (30 * 60 * 1000);
          org.getLeaveCooldowns().put(player.getUniqueId(), banUntil);
          logger.send(player, Lang.LEAVE_LOSING.replace(null));
        }
      }

      MatchHelper.leaveMatch(org, player, match, logger, false);
      return true;
    } else if (sub.equalsIgnoreCase("takeplace") || sub.equalsIgnoreCase("tkp")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;

      if (!physics.isMatchesEnabled()) { logger.send(player, Lang.FC_DISABLED.replace(null)); return true; }
      if (org.isBanned(player)) return true;
      if (org.getLeftMatches().length == 0) { logger.send(player, Lang.TAKEPLACE_NOPLACE.replace(null)); return true; }
      if (org.isInGame(player)) { logger.send(player, Lang.TAKEPLACE_INGAME.replace(null)); return true; }

      org.getPlayingPlayers().add(player.getName());
      org.getLeftMatches()[0].takePlace(player);

      org.setLeftMatches(Arrays.copyOfRange(org.getLeftMatches(), 1, org.getLeftMatches().length));
      org.setLeftPlayerIsRed(Arrays.copyOfRange(org.getLeftPlayerIsRed(), 1, org.getLeftPlayerIsRed().length));

      return true;
    } else if (sub.equalsIgnoreCase("teamchat") || sub.equalsIgnoreCase("tc")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;

      if (args.length < 2) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <message>"})); return true; }

      Match match = MatchHelper.getMatch(org, player);
      if (match == null) { logger.send(player, Lang.LEAVE_NOT_INGAME.replace(null)); return true; }
      if (!match.canUseTeamChat()) { logger.send(player, Lang.TEAMS_NOT_SETUP.replace(null)); return true; }

      match.teamChat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
      return true;
    } else if (sub.equalsIgnoreCase("stats")) {
      if (sender instanceof Player) {
        Player p = (Player) sender;
        org.checkStats(args.length > 1 ? args[1] : p.getName(), sender);
      } else {
        if (args.length < 2) { logger.send(sender, "&cYou need to specify a player."); }
        else { org.checkStats(args[1], sender); }
      }
      return true;
    } else if (sub.equalsIgnoreCase("team")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;

      if (!physics.isMatchesEnabled()) { logger.send(player, Lang.FC_DISABLED.replace(null)); return true; }
      if (args.length < 2) { logger.send(player, Lang.TEAM_USAGE.replace(new String[]{Lang.OR.replace(null)})); return true; }

      String action = args[1].toLowerCase();
      String targetName = args.length > 2 ? args[2] : null;
      Player target;
      MatchHelper.ArenaData dataTeam;

      switch (action) {
        case "cancel":
          if (!org.getTeamReverse().containsKey(player)) { logger.send(player, Lang.TEAM_NO_REQUEST_SENT.replace(null)); break; }
          target = org.getTeamReverse().get(player);
          logger.send(target, Lang.TEAM_CANCEL_OTHER.replace(new String[]{player.getName()}));
          logger.send(player, Lang.TEAM_CANCEL.replace(null));
          org.getTeamType().remove(player);
          org.getTeamReverse().remove(player);
          org.getTeam().remove(target);
          break;

        case "accept":
          if (!org.getTeam().containsKey(player)) { logger.send(player, Lang.TEAM_NO_REQUEST.replace(null)); break; }
          target = org.getTeam().get(player);
          int sizeTeam = org.getTeamType().get(target);
          dataTeam = MatchHelper.getArenaData(org, sizeTeam + "v" + sizeTeam);
          if (dataTeam == null || dataTeam.matches[dataTeam.lobby].team(player, target)) {
            org.getWaitingTeamPlayers().add(player);
            org.getWaitingTeamPlayers().add(target);
            org.setWaitingTeams(org.extendArray(org.getWaitingTeams(), new Player[]{player, target, null}));
          } else {
            org.getWaitingPlayers().put(player.getName(), sizeTeam);
            org.getWaitingPlayers().put(target.getName(), sizeTeam);
          }
          logger.send(player, Lang.TEAM_ACCEPT_OTHER.replace(new String[]{target.getName()}));
          logger.send(target, Lang.TEAM_ACCEPT_SELF.replace(new String[]{player.getName()}));
          org.getTeam().remove(player);
          org.getTeamReverse().remove(target);
          org.getTeamType().remove(target);
          break;

        case "decline":
          if (!org.getTeam().containsKey(player)) { logger.send(player, Lang.TEAM_NO_REQUEST.replace(null)); break; }
          target = org.getTeam().get(player);
          logger.send(target, Lang.TEAM_DECLINE_OTHER.replace(new String[]{player.getName()}));
          logger.send(player, Lang.TEAM_DECLINE_SELF.replace(null));
          org.getTeamType().remove(target);
          org.getTeamReverse().remove(target);
          org.getTeam().remove(player);
          break;

        case "2v2":
        case "3v3":
        case "4v4":
          if (targetName == null || !org.isOnlinePlayer(targetName)) { logger.send(player, Lang.TEAM_NOT_ONLINE.replace(new String[]{targetName})); break; }
          target = plugin.getServer().getPlayer(targetName);
          if (player.equals(target)) { logger.send(player, Lang.TEAM_YOURSELF.replace(null)); break; }
          if (org.isInGame(player) || org.isInGame(target)) { logger.send(player, Lang.TEAM_CANT_SEND_INGAME.replace(null)); break; }
          if (org.getTeam().containsKey(target) || org.getTeamReverse().containsKey(player)) { logger.send(player, Lang.TEAM_ALREADY_SENT.replace(new String[]{targetName})); break; }

          dataTeam = MatchHelper.getArenaData(org, action);
          if (dataTeam == null) { logger.send(player, Lang.TEAM_USAGE.replace(new String[]{Lang.OR.replace(null)})); break; }

          org.getTeam().put(target, player);
          org.getTeamReverse().put(player, target);
          org.getTeamType().put(player, dataTeam.size);
          logger.send(target, Lang.TEAM_WANTS_TO_TEAM_OTHER.replace(new String[]{player.getName(), String.valueOf(dataTeam.size)}));
          logger.send(player, Lang.TEAM_WANTS_TO_TEAM_SELF.replace(new String[]{target.getName(), String.valueOf(dataTeam.size)}));
          break;

        default:
          logger.send(player, Lang.TEAM_USAGE.replace(new String[]{Lang.OR.replace(null)}));
      }

      return true;
    } else if (sub.equalsIgnoreCase("highscores") || sub.equalsIgnoreCase("best")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      org.updateHighScores((Player) sender);
      return true;
    } else if (sub.equalsIgnoreCase("cube")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;
      if (!player.hasPermission(PERM_CUBE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_CUBE, label + " " + sub})); return true; }
      if (player.getWorld().getDifficulty() == Difficulty.PEACEFUL) { logger.send(player, Lang.PREFIX.replace(null) + "&cDifficulty ne sme biti na peaceful."); return true; }
      if (org.isInGame(player)) { logger.send(player, Lang.PREFIX.replace(null) + "&cNe možete stvarati lopte dok ste u igri."); return true; }

      Location loc = player.getLocation();
      Vector dir = loc.getDirection().normalize();
      Location spawnLoc = loc.add(dir.multiply(2.0));
      spawnLoc.setY(loc.getY() + 2.5);
      physics.spawnCube(spawnLoc);
      logger.send(player, Lang.PREFIX.replace(null) + "&aCube spawned!");

      return true;
    } else if (sub.equalsIgnoreCase("clearcube")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;
      if (!player.hasPermission(PERM_CLEAR_CUBE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_CLEAR_CUBE, label + " " + sub})); return true; }
      if (org.isInGame(player)) { logger.send(player, Lang.PREFIX.replace(null) + "&cNe možete brisati lopte dok ste u igri."); return true; }

      double closestDistance = 100.0;
      Slime closest = null;
      for (Slime cube : physics.getCubes()) {
        double distance = cube.getLocation().distance(player.getLocation());
        if (distance < closestDistance) {
          closestDistance = distance;
          closest = cube;
        }
      }

      if (closest != null) {
        physics.getCubes().remove(closest);
        if (physics.getPracticeCubes() != null) physics.getPracticeCubes().remove(closest);
        closest.remove();
        logger.send(player, Lang.CUBE_CLEAR.replace(null));
      } else logger.send(player, Lang.CUBE_NO_CUBES.replace(null));

      return true;
    } else if (sub.equalsIgnoreCase("clearcubes")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;
      if (!player.hasPermission(PERM_CLEAR_CUBE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_CLEAR_CUBE, label + " " + sub})); return true; }
      if (org.isInGame(player)) { logger.send(player, Lang.PREFIX.replace(null) + "&cNe možete brisati lopte dok ste u igri."); return true; }

      int count = 0;
      for (Slime cube : physics.getCubes()) {
        cube.setHealth(0.0);
        count++;
      }
      physics.getCubes().clear();
      if (physics.getPracticeCubes() != null) physics.getPracticeCubes().clear();
      logger.send(player, Lang.CUBE_CLEAR_ALL.replace(new String[]{String.valueOf(count)}));

      return true;
    } else if (sub.equalsIgnoreCase("toggles") || sub.equalsIgnoreCase("ts")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;
      if (args.length < 2) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <kick|goal>"})); return true; }

      String type = args[1].toLowerCase();
      PlayerData playerData = dataManager.get(player);
      if (playerData == null) return true;
      PlayerSoundSettings settings = physics.getSettings(player);
      switch (type) {
        case "kick":
          settings.setKickEnabled(!settings.isKickEnabled());
          playerData.set("sounds.kick.enabled", settings.isKickEnabled());
          logger.send(player, Lang.BETA_FEATURE.replace(null) + (settings.isKickEnabled() ? "§aEnabled §9" : "§cDisabled §9") + "kick sound.");
          break;
        case "goal":
          settings.setGoalEnabled(!settings.isGoalEnabled());
          playerData.set("sounds.goal.enabled", settings.isGoalEnabled());
          logger.send(player, Lang.BETA_FEATURE.replace(null) + (settings.isGoalEnabled() ? "§aEnabled §9" : "§cDisabled §9") + "goal sound.");
          break;
        default:
          logger.send(player, Lang.BETA_FEATURE.replace(null) + "Invalid type. Use: goal|kick");
      }

      return true;
    } else if (sub.equalsIgnoreCase("setsound")) {
      if (!(sender instanceof Player)) return inGameOnly(sender);
      Player player = (Player) sender;
      if (!player.hasPermission(PERM_SET_SOUND)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_SET_SOUND, label + " " + sub})); return true; }
      if (args.length <= 2) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <kick|goal> <soundName>"})); return true; }

      PlayerData playerData = dataManager.get(player);
      if (playerData == null) return true;

      PlayerSoundSettings settings = physics.getSettings(player);
      Sound sound;
      try {
        sound = Sound.valueOf(args[2].toUpperCase());
      } catch (Exception exception) {
        logger.send(player, "&cInvalid sound.");
        String sounds = String.join("&c, &e",
            Arrays.stream(Sound.values()).map(Sound::name).toArray(String[]::new));
        logger.send(player, "&fList of available sounds: &e" + sounds.toLowerCase());
        return true;
      }

      switch (args[1].toLowerCase()) {
        case "kick":
          settings.setKickSound(sound);
          playerData.set("sounds.kick.sound", sound.toString());
          logger.send(player, "&aKick sound set to: &e" + sound);
          break;

        case "goal":
          settings.setGoalSound(sound);
          playerData.set("sounds.goal.sound", sound.toString());
          logger.send(player, "&aGoal sound set to: &e" + sound);
          break;

        default:
          logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <kick|goal> <soundName>"}));
          String sounds = String.join("&c, &e", Arrays.stream(Sound.values()).map(Sound::name).toArray(String[]::new));
          logger.send(player, "&fList of available sounds: &e" + sounds.toLowerCase());
          break;
      }

      return true;
    } else sendHelp(sender);
    return true;
  }

  private void sendHelp(CommandSender sender) {
    logger.send(sender, Lang.HELP.replace(new String[]{Lang.OR.replace(null)}));
    if (sender.hasPermission(PERM_ADMIN)) logger.send(sender, Lang.HELP_ADMIN.replace(new String[]{Lang.OR.replace(null)}));
  }

  private boolean inGameOnly(CommandSender sender) {
    logger.send(sender, "&cThis command can only be used by players.");
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    List<String> completions = new ArrayList<>();

    if (args.length == 1) {
      completions.addAll(Arrays.asList(
          "join", "leave", "takeplace", "tkp", "teamchat", "tc", "team", "t",
          "stats", "best", "highscores", "toggles", "ts", "cube", "clearcube",
          "clearcubes", "help", "h", "setsound"
      ));
    } else if (args.length == 2) {
      String sub = args[0].toLowerCase();
      switch (sub) {
        case "join":
          for (String type : Arrays.asList("2v2", "3v3", "4v4")) if (arenas.getInt("arenas." + type + ".amount", 0) > 0) completions.add(type);
          break;
        case "team":
        case "t":
          for (Player p : plugin.getServer().getOnlinePlayers())
            if (!p.equals(sender) && !org.isInGame(p) && !org.getTeam().containsKey(p) && !org.getTeamReverse().containsKey(p)) completions.add(p.getName());
          completions.addAll(Arrays.asList("accept", "decline", "cancel", "2v2", "3v3", "4v4"));
          break;
        case "toggles":
        case "ts":
        case "setsound": completions.addAll(Arrays.asList("kick", "goal")); break;
      }
    } else if (args.length == 3) {
      String sub = args[0].toLowerCase();
      if (sub.equals("setsound")) for (Sound sound : Sound.values()) completions.add(sound.name());
    }

    if (!completions.isEmpty()) {
      String lastWord = args[args.length - 1].toLowerCase();
      completions.removeIf(s -> !s.toLowerCase().startsWith(lastWord));
      completions.sort(String.CASE_INSENSITIVE_ORDER);
    }

    return completions;
  }
}