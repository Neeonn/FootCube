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
import io.github.divinerealms.footcube.utils.PlayerSettings;
import net.minecraft.server.v1_8_R3.EnumParticle;
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
import java.util.stream.Collectors;

public class FCCommand implements CommandExecutor, TabCompleter {
  private final FCManager fcManager;
  private final FootCube plugin;
  private final Physics physics;
  private final Logger logger;
  private final Organization org;
  private final FileConfiguration arenas;
  private final PlayerDataManager dataManager;

  private static final String PERM_MAIN = "footcube";
  private static final String PERM_PLAY = PERM_MAIN + ".play";
  private static final String PERM_CUBE = PERM_MAIN + ".cube";
  private static final String PERM_CLEAR_CUBE = PERM_MAIN + ".clearcube";
  private static final String PERM_SET_SOUND = PERM_MAIN + ".sound";
  private static final String PERM_SET_PARTICLE = PERM_MAIN + ".particle";

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
    if (args.length == 0) { sendHelp(sender); return true; }

    String sub = args[0].toLowerCase();
    Player player;
    Match match;
    PlayerData playerData;
    PlayerSettings settings;

    switch (sub) {
      case "join":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_PLAY, label + " " + sub})); return true; }
        if (!physics.isMatchesEnabled()) { logger.send(player, Lang.FC_DISABLED.replace(null)); return true; }
        if (org.isBanned(player)) return true;

        if (args.length < 2 || args[1] == null) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <2v2|3v3|4v4>"})); return true; }
        String matchType = args[1].toLowerCase();
        if (org.isInGame(player) || org.getWaitingPlayers().containsKey(player.getName())) {
          logger.send(player, Lang.JOIN_ALREADYINGAME.replace(null));
          return true;
        }

        MatchHelper.ArenaData data = MatchHelper.getArenaData(org, matchType);
        if (data == null || data.lobby < 0 || data.lobby >= data.matches.length) {
          logger.send(player, Lang.JOIN_INVALIDTYPE.replace(new String[]{matchType, Lang.OR.replace(null)}));
          return true;
        }

        if (arenas.getInt("arenas." + matchType + ".amount", 0) == 0) { logger.send(player, Lang.JOIN_NOARENA.replace(null)); return true; }

        match = data.matches[data.lobby];
        if (match == null) { logger.send(player, Lang.JOIN_NOARENA.replace(null)); return true; }

        org.removeTeam(player);
        match.join(player, false);
        org.getWaitingPlayers().put(player.getName(), data.size);

        if (player.getAllowFlight()) { player.setFlying(false); player.setAllowFlight(false); }
        if (player.getGameMode() != GameMode.SURVIVAL) player.setGameMode(GameMode.SURVIVAL);
        break;

      case "leave":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_PLAY, label + " " + sub})); return true; }
        if (!org.isInGame(player)) { logger.send(player, Lang.LEAVE_NOT_INGAME.replace(null)); return true; }

        match = MatchHelper.getMatch(org, player);
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
        break;

      case "takeplace":
      case "tkp":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_PLAY, label + " " + sub})); return true; }
        if (!physics.isMatchesEnabled()) { logger.send(player, Lang.FC_DISABLED.replace(null)); return true; }
        if (org.isBanned(player)) return true;
        if (org.getLeftMatches().length == 0) { logger.send(player, Lang.TAKEPLACE_NOPLACE.replace(null)); return true; }
        if (org.isInGame(player)) { logger.send(player, Lang.TAKEPLACE_INGAME.replace(null)); return true; }

        org.getPlayingPlayers().add(player.getName());
        org.getLeftMatches()[0].takePlace(player);

        org.setLeftMatches(Arrays.copyOfRange(org.getLeftMatches(), 1, org.getLeftMatches().length));
        org.setLeftPlayerIsRed(Arrays.copyOfRange(org.getLeftPlayerIsRed(), 1, org.getLeftPlayerIsRed().length));
        break;

      case "teamchat":
      case "tc":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;

        if (args.length < 2) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <message>"})); return true; }

        match = MatchHelper.getMatch(org, player);
        if (match == null) { logger.send(player, Lang.LEAVE_NOT_INGAME.replace(null)); return true; }
        if (!match.canUseTeamChat()) { logger.send(player, Lang.TEAMS_NOT_SETUP.replace(null)); return true; }

        match.teamChat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        break;

      case "stats":
        if (sender instanceof Player) {
          Player p = (Player) sender;
          org.checkStats(args.length > 1 ? args[1] : p.getName(), sender);
        } else {
          if (args.length < 2) { logger.send(sender, "&cYou need to specify a player."); }
          else { org.checkStats(args[1], sender); }
        }
        break;

      case "team":
      case "t":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_PLAY, label + " " + sub})); return true; }
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
            boolean placedInMatch = false;
            if (dataTeam != null) placedInMatch = dataTeam.matches[dataTeam.lobby].team(player, target);
            if (!placedInMatch) {
              org.getWaitingTeamPlayers().add(player);
              org.getWaitingTeamPlayers().add(target);
              org.setWaitingTeams(org.extendArray(org.getWaitingTeams(), new Player[]{player, target, null}));
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
            if (args.length < 3) { logger.send(player, Lang.TEAM_USAGE.replace(new String[]{Lang.OR.replace(null)})); return true; }
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
        break;

      case "highscores":
      case "best":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        org.updateHighScores((Player) sender);
        break;

      case "cube":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_CUBE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_CUBE, label + " " + sub})); return true; }
        if (player.getWorld().getDifficulty() == Difficulty.PEACEFUL) { logger.send(player, Lang.PREFIX.replace(null) + "&cDifficulty ne sme biti na peaceful."); return true; }
        if (org.isInGame(player)) { logger.send(player, Lang.COMMAND_DISABLER_CANT_USE.replace(null)); return true; }

        Location loc = player.getLocation();
        Vector dir = loc.getDirection().normalize();
        Location spawnLoc;
        if (player.getGameMode() != GameMode.CREATIVE) {
          spawnLoc = loc.add(dir.multiply(2.0));
          spawnLoc.setY(loc.getY() + 2.5);
        } else spawnLoc = loc;

        physics.spawnCube(spawnLoc);
        logger.send(player, Lang.CUBE_SPAWN.replace(null));
        break;

      case "clearcube":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_CLEAR_CUBE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_CLEAR_CUBE, label + " " + sub})); return true; }
        if (org.isInGame(player)) { logger.send(player, Lang.COMMAND_DISABLER_CANT_USE.replace(null)); return true; }

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
          closest.setHealth(0);
          logger.send(player, Lang.CUBE_CLEAR.replace(null));
        } else logger.send(player, Lang.CUBE_NO_CUBES.replace(null));
        break;

      case "clearcubes":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_CLEAR_CUBE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_CLEAR_CUBE, label + " " + sub})); return true; }
        if (org.isInGame(player)) { logger.send(player, Lang.COMMAND_DISABLER_CANT_USE.replace(null)); return true; }

        int count = 0;
        for (Slime cube : physics.getCubes()) {
          cube.setHealth(0);
          count++;
        }
        logger.send(player, Lang.CUBE_CLEAR_ALL.replace(new String[]{String.valueOf(count)}));
        break;

      case "toggles":
      case "ts":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (args.length < 2) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <kick|goal|particles|hits> <value|list>"})); return true; }

        String type = args[1].toLowerCase();
        playerData = dataManager.get(player);
        if (playerData == null) return true;
        settings = fcManager.getPlayerSettings(player);
        switch (type) {
          case "kick":
            settings.setKickSoundEnabled(!settings.isKickSoundEnabled());
            playerData.set("sounds.kick.enabled", settings.isKickSoundEnabled());
            logger.send(player, Lang.TOGGLES_KICK.replace(new String[]{settings.isKickSoundEnabled() ? Lang.ON.replace(null) : Lang.OFF.replace(null)}));
            break;

          case "goal":
            settings.setGoalSoundEnabled(!settings.isGoalSoundEnabled());
            playerData.set("sounds.goal.enabled", settings.isGoalSoundEnabled());
            logger.send(player, Lang.TOGGLES_GOAL.replace(new String[]{settings.isGoalSoundEnabled() ? Lang.ON.replace(null) : Lang.OFF.replace(null)}));
            break;

          case "particles":
            settings.setParticlesEnabled(!settings.isParticlesEnabled());
            playerData.set("particles.enabled", settings.isParticlesEnabled());
            logger.send(player, Lang.TOGGLES_PARTICLES.replace(new String[]{settings.isParticlesEnabled() ? Lang.ON.replace(null) : Lang.OFF.replace(null)}));
            break;

          case "hits":
            boolean wasEnabled = physics.getCubeHits().contains(player.getUniqueId());

            if (wasEnabled) physics.getCubeHits().remove(player.getUniqueId());
            else physics.getCubeHits().add(player.getUniqueId());

            logger.send(player, Lang.TOGGLES_HIT_DEBUG.replace(new String[]{!wasEnabled ? Lang.ON.replace(null) : Lang.OFF.replace(null)}));
            break;

          default:
            logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <kick|goal|particles|hits> <value|list>"}));
        }
        break;

      case "setsound":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_SET_SOUND)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_SET_SOUND, label + " " + sub})); return true; }
        if (args.length <= 2) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <kick|goal> <soundName|list>"})); return true; }

        playerData = dataManager.get(player);
        if (playerData == null) return true;

        settings = fcManager.getPlayerSettings(player);
        Sound sound = Sound.SUCCESSFUL_HIT;
        try {
          if (!args[2].equalsIgnoreCase("list")) sound = Sound.valueOf(args[2].toUpperCase());
        } catch (Exception exception) {
          logger.send(player, Lang.INVALID_TYPE.replace(new String[]{Lang.SOUND.replace(null)}));
          return true;
        }

        switch (args[1].toLowerCase()) {
          case "kick":
            if (args[2].equalsIgnoreCase("list")) {
              logger.send(player, Lang.AVAILABLE_TYPE.replace(new String[]{Lang.SOUND.replace(null), PlayerSettings.ALLOWED_KICK_SOUNDS.stream().map(Enum::name).collect(Collectors.joining(", "))}));
              return true;
            }

            if (!PlayerSettings.ALLOWED_KICK_SOUNDS.contains(sound)) {
              logger.send(player, Lang.INVALID_TYPE.replace(new String[]{Lang.SOUND.replace(null)}));
              logger.send(player, Lang.AVAILABLE_TYPE.replace(new String[]{Lang.SOUND.replace(null), PlayerSettings.ALLOWED_KICK_SOUNDS.stream().map(Enum::name).collect(Collectors.joining(", "))}));
              return true;
            }

            settings.setKickSound(sound);
            playerData.set("sounds.kick.sound", sound.toString());
            logger.send(player, Lang.SET_SOUND_KICK.replace(new String[]{sound.name()}));
            break;

          case "goal":
            if (args[2].equalsIgnoreCase("list")) {
              logger.send(player, Lang.AVAILABLE_TYPE.replace(new String[]{Lang.SOUND.replace(null), PlayerSettings.ALLOWED_GOAL_SOUNDS.stream().map(Enum::name).collect(Collectors.joining(", "))}));
              return true;
            }

            if (!PlayerSettings.ALLOWED_GOAL_SOUNDS.contains(sound)) {
              logger.send(player, Lang.INVALID_TYPE.replace(new String[]{Lang.SOUND.replace(null)}));
              logger.send(player, Lang.AVAILABLE_TYPE.replace(new String[]{Lang.SOUND.replace(null), PlayerSettings.ALLOWED_GOAL_SOUNDS.stream().map(Enum::name).collect(Collectors.joining(", "))}));
              return true;
            }

            settings.setGoalSound(sound);
            playerData.set("sounds.goal.sound", sound.toString());
            logger.send(player, Lang.SET_SOUND_GOAL.replace(new String[]{sound.name()}));
            break;

          default:
            logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <kick|goal> <soundName|list>"}));
            break;
        }
        break;

      case "setparticle":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_SET_PARTICLE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_SET_PARTICLE, label + " " + sub})); return true; }
        if (args.length < 2) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <particleName|list> [color]"})); return true; }

        if (args[1].equalsIgnoreCase("list")) {
          logger.send(player, Lang.AVAILABLE_TYPE.replace(new String[]{Lang.PARTICLE.replace(null), String.join(", ", PlayerSettings.getAllowedParticles())}));
          return true;
        }

        playerData = dataManager.get(player);
        if (playerData == null) return true;

        settings = fcManager.getPlayerSettings(player);
        EnumParticle particle;
        try {
          particle = EnumParticle.valueOf(args[1].toUpperCase());
        } catch (Exception exception) {
          logger.send(player, Lang.INVALID_TYPE.replace(new String[]{Lang.PARTICLE.replace(null)}));
          logger.send(player, Lang.AVAILABLE_TYPE.replace(new String[]{Lang.PARTICLE.replace(null), String.join(", ", PlayerSettings.getAllowedParticles())}));
          return true;
        }

        if (PlayerSettings.DISALLOWED_PARTICLES.contains(particle)) {
          logger.send(player, Lang.INVALID_TYPE.replace(new String[]{Lang.PARTICLE.replace(null)}));
          logger.send(player, Lang.AVAILABLE_TYPE.replace(new String[]{Lang.PARTICLE.replace(null), String.join(", ", PlayerSettings.getAllowedParticles())}));
          return true;
        }

        if (particle == EnumParticle.REDSTONE) {
          if (args.length >= 3) {
            try {
              String colorName = args[2].toUpperCase();
              settings.setCustomRedstoneColor(colorName);
              playerData.set("particles.effect", "REDSTONE:" + colorName);
              logger.send(player, Lang.SET_PARTICLE_REDSTONE.replace(new String[]{particle.name(), colorName}));
              return true;
            } catch (IllegalArgumentException exception) {
              logger.send(player, Lang.INVALID_COLOR.replace(new String[]{args[2].toUpperCase()}));
              logger.send(player, Lang.AVAILABLE_TYPE.replace(new String[]{Lang.COLOR.replace(null), String.join(", ", PlayerSettings.getAllowedColorNames())}));
              return true;
            }
          } else {
            settings.setCustomRedstoneColor("WHITE");
            playerData.set("particles.effect", "REDSTONE:WHITE");
            logger.send(player, Lang.SET_PARTICLE_REDSTONE.replace(new String[]{particle.name(), "WHITE"}));
          }

          settings.setParticle(EnumParticle.REDSTONE);
          return true;
        }

        settings.setParticle(particle);
        playerData.set("particles.effect", particle.toString());
        logger.send(player, Lang.SET_PARTICLE.replace(new String[]{particle.name()}));
        break;

      default: sendHelp(sender); break;
    }

    return true;
  }

  private void sendHelp(CommandSender sender) {
    logger.send(sender, Lang.HELP.replace(null));
  }

  private boolean inGameOnly(CommandSender sender) {
    logger.send(sender, Lang.INGAME_ONLY.replace(null));
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    List<String> completions = new ArrayList<>();

    if (args.length == 1) {
      completions.addAll(Arrays.asList(
          "join", "leave", "takeplace", "tkp", "teamchat", "tc", "team", "t",
          "stats", "best", "highscores", "toggles", "ts", "cube", "clearcube",
          "clearcubes", "help", "h", "setsound", "setparticle"
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
          completions.addAll(Arrays.asList("kick", "goal", "particles", "hits")); break;
        case "setparticle":
          completions.add("list");
          completions.addAll(PlayerSettings.getAllowedParticles());
          break;
        case "setsound": completions.addAll(Arrays.asList("kick", "goal")); break;
      }
    } else if (args.length == 3) {
      String sub = args[0].toLowerCase();
      if (sub.equalsIgnoreCase("setsound")) {
        if (args[1].equalsIgnoreCase("kick")) {
          completions.add("list");
          completions.addAll(PlayerSettings.ALLOWED_KICK_SOUNDS.stream()
              .map(Enum::name)
              .collect(Collectors.toList()));
        } else if (args[1].equalsIgnoreCase("goal")) {
          completions.add("list");
          completions.addAll(PlayerSettings.ALLOWED_GOAL_SOUNDS.stream()
              .map(Enum::name)
              .collect(Collectors.toList()));
        }
      } else if (sub.equalsIgnoreCase("setparticle")) {
        if (args[1].equalsIgnoreCase("list")) completions.addAll(PlayerSettings.getAllowedParticles());
        else if (args[1].equalsIgnoreCase("redstone")) completions.addAll(PlayerSettings.getAllowedColorNames());
      }
    }

    if (!completions.isEmpty()) {
      String lastWord = args[args.length - 1].toLowerCase();
      completions.removeIf(s -> !s.toLowerCase().startsWith(lastWord));
      completions.sort(String.CASE_INSENSITIVE_ORDER);
    }

    return completions;
  }
}