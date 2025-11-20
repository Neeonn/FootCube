package io.github.divinerealms.footcube.commands;

import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
import io.github.divinerealms.footcube.matchmaking.team.TeamManager;
import io.github.divinerealms.footcube.matchmaking.util.MatchConstants;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.Logger;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

import static io.github.divinerealms.footcube.utils.Permissions.*;

public class FCCommand implements CommandExecutor, TabCompleter {
  private final FCManager fcManager;
  private final FootCube plugin;
  private final Logger logger;
  private final MatchManager matchManager;
  private final TeamManager teamManager;
  private final PlayerDataManager dataManager;
  private final PhysicsData physicsData;
  private final PhysicsSystem system;

  public FCCommand(FCManager fcManager) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.logger = fcManager.getLogger();
    this.matchManager = fcManager.getMatchManager();
    this.teamManager = matchManager.getTeamManager();
    this.dataManager = fcManager.getDataManager();
    this.physicsData = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) { sendBanner(sender); return true; }

    String sub = args[0].toLowerCase();
    Player player;
    PlayerData playerData;
    PlayerSettings settings;

    switch (sub) {
      case "join":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_PLAY, label + " " + sub})); return true; }
        if (!matchManager.getData().isMatchesEnabled()) { logger.send(player, Lang.FC_DISABLED.replace(null)); return true; }
        if (matchManager.getBanManager().isBanned(player)) return true;
        if (args.length < 2 || args[1] == null) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <2v2|3v3|4v4>"})); return true; }
        String matchType = args[1].toLowerCase();
        if (matchManager.getMatch(player).isPresent()) { logger.send(player, Lang.JOIN_ALREADYINGAME.replace(null)); return true; }

        int type;
        switch (matchType) {
          case "2v2":
            type = MatchConstants.TWO_V_TWO;
            break;

          case "3v3":
            type = MatchConstants.THREE_V_THREE;
            break;

          case "4v4":
            type = MatchConstants.FOUR_V_FOUR;
            break;

          default:
            logger.send(player, Lang.JOIN_INVALIDTYPE.replace(new String[]{matchType, Lang.OR.replace(null)}));
            return true;
        }

        matchManager.joinQueue(player, type);
        break;

      case "leave":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_PLAY, label + " " + sub})); return true; }

        Optional<Match> matchOpt = matchManager.getMatch(player);
        if (matchOpt.isPresent()) {
          Match match = matchOpt.get();
          MatchPhase matchPhase = match.getPhase();
          if (matchPhase == MatchPhase.IN_PROGRESS) {
            Optional<MatchPlayer> matchPlayerOpt = match.getPlayers().stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getPlayer() != null && p.getPlayer().equals(player))
                .findFirst();

            if (matchPlayerOpt.isPresent()) {
              MatchPlayer matchPlayer = matchPlayerOpt.get();

              int playerScore = matchPlayer.getTeamColor() == TeamColor.RED ? match.getScoreRed() : match.getScoreBlue();
              int opponentScore = matchPlayer.getTeamColor() == TeamColor.RED ? match.getScoreBlue() : match.getScoreRed();

              if (playerScore < opponentScore) {
                fcManager.getEconomy().withdrawPlayer(player, 200);
                matchManager.getBanManager().banPlayer(player, 30 * 60 * 1000);
                logger.send(player, Lang.LEAVE_LOSING.replace(null));
              }
            }
          }

          matchManager.leaveMatch(player);
          logger.send(player, Lang.LEFT.replace(null));

          teamManager.forceDisbandTeam(player);
        } else {
          boolean leftQueue = false;
          for (int queueType : matchManager.getData().getPlayerQueues().keySet()) {
            Queue<Player> queue = matchManager.getData().getPlayerQueues().get(queueType);
            if (queue.contains(player)) {
              matchManager.leaveQueue(player, queueType);
              leftQueue = true;
              teamManager.disbandTeamIfInLobby(player);
            }
          }

          if (leftQueue) logger.send(player, Lang.LEFT.replace(null));
          else logger.send(player, Lang.LEAVE_NOT_INGAME.replace(null));
        }
        break;

      case "takeplace":
      case "tkp":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_PLAY, label + " " + sub})); return true; }
        if (!matchManager.getData().isMatchesEnabled()) { logger.send(player, Lang.FC_DISABLED.replace(null)); return true; }
        if (matchManager.getBanManager().isBanned(player)) return true;
        if (matchManager.getMatch(player).isPresent()) { logger.send(player, Lang.TAKEPLACE_INGAME.replace(null)); return true; }
        if (matchManager.getData().getOpenMatches().isEmpty()) { logger.send(player, Lang.TAKEPLACE_NOPLACE.replace(null)); return true; }

        if (args.length < 2) {
          Match openMatch = matchManager.getData().getOpenMatches().stream().findFirst().get();
          matchManager.takePlace(player, openMatch.getArena().getId());
          return true;
        }

        if (args[1].equalsIgnoreCase("list")) {
          logger.send(player, Lang.TAKEPLACE_AVAILABLE_HEADER.replace(null));

          for (Match openMatch : matchManager.getData().getOpenMatches()) {
            long emptySlots = openMatch.getPlayers().stream().filter(Objects::isNull).count();

            logger.send(player, Lang.TAKEPLACE_AVAILABLE_ENTRY.replace(new String[]{
                String.valueOf(openMatch.getArena().getId()),
                openMatch.getArena().getType() + "v" + openMatch.getArena().getType(),
                String.valueOf(emptySlots)
            }));
          }
          return true;
        }

        try {
          int matchId = Integer.parseInt(args[1]);
          matchManager.takePlace(player, matchId);
        } catch (NumberFormatException exception) {
          logger.send(player, Lang.STATSSET_IS_NOT_A_NUMBER.replace(new String[]{args[1]}));
        }
        break;

      case "teamchat":
      case "tc":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (args.length < 2) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <message>"})); return true; }

        matchManager.teamChat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        break;

      case "team":
      case "t":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_PLAY, label + " " + sub})); return true; }
        if (!matchManager.getData().isMatchesEnabled()) { logger.send(player, Lang.FC_DISABLED.replace(null)); return true; }
        if (args.length < 2) { logger.send(player, Lang.TEAM_USAGE.replace(new String[]{Lang.OR.replace(null)})); return true; }

        String action = args[1].toLowerCase(), targetName;
        Player target;
        switch (action) {
          case "accept":
            if (teamManager.isInTeam(player)) { logger.send(player, Lang.TEAM_ALREADY_IN_TEAM.replace(null)); return true; }
            if (teamManager.noInvite(player)) { logger.send(player, Lang.TEAM_NO_REQUEST.replace(null)); return true; }

            target = teamManager.getInviter(player);
            targetName = target != null && target.isOnline() ? target.getName() : "";
            if (target == null || !target.isOnline()) { logger.send(player, Lang.TEAM_NOT_ONLINE.replace(new String[]{targetName})); return true; }

            if (teamManager.isInTeam(target)) {
              logger.send(player, Lang.TEAM_ALREADY_IN_TEAM_2.replace(new String[]{target.getName()}));
              teamManager.removeInvite(player);
              return true;
            }

            int mType = teamManager.getInviteMatchType(player);
            teamManager.createTeam(target, player, mType);
            logger.send(player, Lang.TEAM_ACCEPT_SELF.replace(new String[]{target.getName()}));
            logger.send(target, Lang.TEAM_ACCEPT_OTHER.replace(new String[]{player.getName()}));

            matchManager.joinQueue(player, mType);
            teamManager.removeInvite(player);
            break;

          case "decline":
            if (teamManager.noInvite(player)) { logger.send(player, Lang.TEAM_NO_REQUEST.replace(null)); return true; }

            target = teamManager.getInviter(player);
            if (target != null && target.isOnline()) logger.send(target, Lang.TEAM_DECLINE_OTHER.replace(new String[]{player.getName()}));

            logger.send(player, Lang.TEAM_DECLINE_SELF.replace(null));
            teamManager.removeInvite(player);
            break;

          default:
            if (args.length < 3) { logger.send(player, Lang.TEAM_USAGE.replace(new String[]{Lang.OR.replace(null)})); return true; }
            if (teamManager.isInTeam(player)) { logger.send(player, Lang.TEAM_ALREADY_IN_TEAM.replace(null)); return true; }

            targetName = args[2];
            target = plugin.getServer().getPlayer(targetName);
            if (target == null) { logger.send(player, Lang.TEAM_NOT_ONLINE.replace(new String[]{targetName})); return true; }
            if (teamManager.isInTeam(target)) { logger.send(player, Lang.TEAM_ALREADY_IN_TEAM_2.replace(new String[]{target.getName()})); return true; }
            if (fcManager.getMatchSystem().isInAnyQueue(player)) { logger.send(player, Lang.JOIN_ALREADYINGAME.replace(null)); return true; }
            if (fcManager.getMatchSystem().isInAnyQueue(target)) { logger.send(player, Lang.TEAM_ALREADY_IN_GAME.replace(new String[]{targetName})); return true; }
            if (matchManager.getMatch(player).isPresent()) { logger.send(player, Lang.JOIN_ALREADYINGAME.replace(null)); return true; }
            if (matchManager.getMatch(target).isPresent()) { logger.send(player, Lang.TEAM_ALREADY_IN_GAME.replace(new String[]{targetName})); return true; }

            String inviteMatchType = args[1].toLowerCase();
            int inviteType;
            switch (inviteMatchType) {
              case "2v2": inviteType = MatchConstants.TWO_V_TWO; break;
              case "3v3": inviteType = MatchConstants.THREE_V_THREE; break;
              case "4v4": inviteType = MatchConstants.FOUR_V_FOUR; break;
              default:
                logger.send(player, Lang.JOIN_INVALIDTYPE.replace(new String[]{inviteMatchType, Lang.OR.replace(null)}));
                return true;
            }

            teamManager.invite(player, target, inviteType);
            logger.send(player, Lang.TEAM_WANTS_TO_TEAM_SELF.replace(new String[]{target.getName(), inviteMatchType}));
            logger.send(target, Lang.TEAM_WANTS_TO_TEAM_OTHER.replace(new String[]{player.getName(), inviteMatchType}));
            break;
        }
        break;

      case "stats":
        if (sender instanceof Player) {
          Player p = (Player) sender;
          fcManager.getMatchSystem().checkStats(args.length > 1 ? args[1] : p.getName(), sender);
        } else {
          if (args.length < 2) logger.send(sender, "&cYou need to specify a player.");
          else fcManager.getMatchSystem().checkStats(args[1], sender);

        }
        break;

      case "highscores":
      case "best":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        if (fcManager.getHighscoreManager().needsUpdate()) fcManager.getHighscoreManager().playerUpdate((Player) sender);
        else fcManager.getHighscoreManager().addWaitingPlayer((Player) sender);
        break;

      case "cube":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_CUBE)) { logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_CUBE, label + " " + sub})); return true; }
        if (player.getWorld().getDifficulty() == Difficulty.PEACEFUL) { logger.send(player, "{prefix-admin}&cDifficulty ne sme biti na peaceful."); return true; }
        if (matchManager.getMatch(player).isPresent()) { logger.send(player, Lang.COMMAND_DISABLER_CANT_USE.replace(null)); return true; }
        if (system.cantSpawnYet(player)) return true;

        Location loc = player.getLocation();
        Vector dir = loc.getDirection().normalize();
        Location spawnLoc;
        if (player.getGameMode() != GameMode.CREATIVE && !player.isFlying()) {
          spawnLoc = loc.add(dir.multiply(2.0));
          spawnLoc.setY(loc.getY() + 2.5);
        } else spawnLoc = loc;

        system.spawnCube(spawnLoc);
        system.setButtonCooldown(player);
        logger.send(player, Lang.CUBE_SPAWN.replace(null));
        break;

      case "clearcube":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_CLEAR_CUBE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_CLEAR_CUBE, label + " " + sub})); return true; }
        if (matchManager.getMatch(player).isPresent()) { logger.send(player, Lang.COMMAND_DISABLER_CANT_USE.replace(null)); return true; }

        double closestDistance = 200;
        Slime closest = null;
        for (Slime cube : physicsData.getCubes()) {
          double distance = cube.getLocation().distance(player.getLocation());
          if (distance < closestDistance) { closestDistance = distance; closest = cube; }
        }

        if (closest != null) { closest.setHealth(0); logger.send(player, Lang.CUBE_CLEAR.replace(null));
        } else logger.send(player, Lang.CUBE_NO_CUBES.replace(null));
        break;

      case "clearcubes":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (!player.hasPermission(PERM_CLEAR_CUBE)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_CLEAR_CUBE, label + " " + sub})); return true; }
        if (matchManager.getMatch(player).isPresent()) { logger.send(player, Lang.COMMAND_DISABLER_CANT_USE.replace(null)); return true; }

        int count = 0;
        for (Slime cube : physicsData.getCubes()) { cube.setHealth(0); count++; }
        logger.send(player, Lang.CUBE_CLEAR_ALL.replace(new String[]{String.valueOf(count)}));
        break;

      case "toggles":
      case "ts":
        if (!(sender instanceof Player)) return inGameOnly(sender);
        player = (Player) sender;
        if (args.length < 2) { logger.send(player, Lang.USAGE.replace(new String[]{label + " " + sub + " <kick|goal|particles|hits> <value|list>"})); return true; }

        String toggleType = args[1].toLowerCase();
        playerData = dataManager.get(player);
        if (playerData == null) return true;
        settings = fcManager.getPlayerSettings(player);
        switch (toggleType) {
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
            boolean wasEnabled = physicsData.getCubeHits().contains(player.getUniqueId());

            if (wasEnabled) physicsData.getCubeHits().remove(player.getUniqueId());
            else physicsData.getCubeHits().add(player.getUniqueId());

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
    logger.send(sender, Lang.HELP.replace(null));
  }

  private void sendBanner(CommandSender sender) {
    if (sender instanceof Player) {
      logger.send(sender, Lang.BANNER_PLAYER.replace(new String[]{
          plugin.getName(), plugin.getDescription().getVersion(),
          plugin.getDescription().getAuthors().stream().map(String::valueOf).collect(Collectors.joining(", "))
      }));
    } else {
      fcManager.sendBanner();
      logger.send(sender, "&aKucajte &e/fc help &aza listu dostupnih komandi.");
    }
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
          "join", "leave", "takeplace", "tkp", "team", "t",
          "stats", "best", "highscores", "toggles", "ts", "cube", "clearcube",
          "clearcubes", "help", "h", "setsound", "setparticle"
      ));
    } else if (args.length == 2) {
      String sub = args[0].toLowerCase();
      switch (sub) {
        case "join":
          completions.addAll(Arrays.asList("2v2", "3v3", "4v4"));
          break;

        case "team":
        case "t":
          completions.addAll(Arrays.asList("accept", "decline", "cancel", "2v2", "3v3", "4v4"));
          break;

        case "toggles":
        case "ts":
          completions.addAll(Arrays.asList("kick", "goal", "particles", "hits"));
          break;

        case "setparticle":
          completions.add("list");
          completions.addAll(PlayerSettings.getAllowedParticles());
          break;

        case "setsound":
          completions.addAll(Arrays.asList("kick", "goal"));
          break;
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
        if (args[1].equalsIgnoreCase("list")) {
          completions.addAll(PlayerSettings.getAllowedParticles());
        } else if (args[1].equalsIgnoreCase("redstone")) {
          completions.addAll(PlayerSettings.getAllowedColorNames());
        }
      } else if (sub.equalsIgnoreCase("team") || sub.equalsIgnoreCase("t")) {
        fcManager.getCachedPlayers().forEach(p -> completions.add(p.getName()));
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
