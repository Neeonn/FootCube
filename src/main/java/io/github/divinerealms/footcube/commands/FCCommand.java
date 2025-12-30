package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.AVAILABLE_TYPE;
import static io.github.divinerealms.footcube.configs.Lang.BANNER_PLAYER;
import static io.github.divinerealms.footcube.configs.Lang.COLOR;
import static io.github.divinerealms.footcube.configs.Lang.COMMAND_DISABLER_CANT_USE;
import static io.github.divinerealms.footcube.configs.Lang.CUBE_CLEAR;
import static io.github.divinerealms.footcube.configs.Lang.CUBE_CLEAR_ALL;
import static io.github.divinerealms.footcube.configs.Lang.CUBE_NO_CUBES;
import static io.github.divinerealms.footcube.configs.Lang.CUBE_SPAWN;
import static io.github.divinerealms.footcube.configs.Lang.FC_DISABLED;
import static io.github.divinerealms.footcube.configs.Lang.HELP;
import static io.github.divinerealms.footcube.configs.Lang.INGAME_ONLY;
import static io.github.divinerealms.footcube.configs.Lang.INVALID_COLOR;
import static io.github.divinerealms.footcube.configs.Lang.INVALID_TYPE;
import static io.github.divinerealms.footcube.configs.Lang.JOIN_ALREADYINGAME;
import static io.github.divinerealms.footcube.configs.Lang.JOIN_INVALIDTYPE;
import static io.github.divinerealms.footcube.configs.Lang.LEAVE_LOSING;
import static io.github.divinerealms.footcube.configs.Lang.LEAVE_NOT_INGAME;
import static io.github.divinerealms.footcube.configs.Lang.LEAVE_QUEUE_ACTIONBAR;
import static io.github.divinerealms.footcube.configs.Lang.LEFT;
import static io.github.divinerealms.footcube.configs.Lang.NO_PERM;
import static io.github.divinerealms.footcube.configs.Lang.OFF;
import static io.github.divinerealms.footcube.configs.Lang.ON;
import static io.github.divinerealms.footcube.configs.Lang.OR;
import static io.github.divinerealms.footcube.configs.Lang.PARTICLE;
import static io.github.divinerealms.footcube.configs.Lang.SET_GOAL_CELEBRATION;
import static io.github.divinerealms.footcube.configs.Lang.SET_PARTICLE;
import static io.github.divinerealms.footcube.configs.Lang.SET_PARTICLE_REDSTONE;
import static io.github.divinerealms.footcube.configs.Lang.SET_SOUND_GOAL;
import static io.github.divinerealms.footcube.configs.Lang.SET_SOUND_KICK;
import static io.github.divinerealms.footcube.configs.Lang.SOUND;
import static io.github.divinerealms.footcube.configs.Lang.STATSSET_IS_NOT_A_NUMBER;
import static io.github.divinerealms.footcube.configs.Lang.TAKEPLACE_AVAILABLE_ENTRY;
import static io.github.divinerealms.footcube.configs.Lang.TAKEPLACE_AVAILABLE_HEADER;
import static io.github.divinerealms.footcube.configs.Lang.TAKEPLACE_INGAME;
import static io.github.divinerealms.footcube.configs.Lang.TAKEPLACE_NOPLACE;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ACCEPT_OTHER;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ACCEPT_SELF;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ALREADY_IN_GAME;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ALREADY_IN_TEAM;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ALREADY_IN_TEAM_2;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_DECLINE_OTHER;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_DECLINE_SELF;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_NOT_ONLINE;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_NO_REQUEST;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_USAGE;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_WANTS_TO_TEAM_OTHER;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_WANTS_TO_TEAM_SELF;
import static io.github.divinerealms.footcube.configs.Lang.TOGGLES_GOAL;
import static io.github.divinerealms.footcube.configs.Lang.TOGGLES_HIT_DEBUG;
import static io.github.divinerealms.footcube.configs.Lang.TOGGLES_KICK;
import static io.github.divinerealms.footcube.configs.Lang.TOGGLES_PARTICLES;
import static io.github.divinerealms.footcube.configs.Lang.UNKNOWN_COMMAND;
import static io.github.divinerealms.footcube.configs.Lang.USAGE;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.FOUR_V_FOUR;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.THREE_V_THREE;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.TWO_V_TWO;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_CLEAR_CUBE;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_CUBE;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_PLAY;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_SET_GOAL_CELEBRATION;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_SET_PARTICLE;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_SET_SOUND;

import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
import io.github.divinerealms.footcube.matchmaking.team.TeamManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.StringJoiner;
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
    if (args.length == 0) {
      sendBanner(sender);
      return true;
    }

    String sub = args[0].toLowerCase();
    Player player;
    PlayerData playerData;
    PlayerSettings settings;

    switch (sub) {
      case "join":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) {
          logger.send(player, NO_PERM, PERM_PLAY, label + " " + sub);
          return true;
        }

        if (!matchManager.getData().isMatchesEnabled()) {
          logger.send(player, FC_DISABLED);
          return true;
        }

        if (matchManager.getBanManager().isBanned(player)) {
          return true;
        }

        if (args.length < 2 || args[1] == null) {
          logger.send(player, USAGE, label + " " + sub + " <2v2|3v3|4v4>");
          return true;
        }

        String matchType = args[1].toLowerCase();
        if (matchManager.getMatch(player).isPresent()) {
          logger.send(player, JOIN_ALREADYINGAME);
          return true;
        }

        int type;
        switch (matchType) {
          case "2v2":
            type = TWO_V_TWO;
            break;

          case "3v3":
            type = THREE_V_THREE;
            break;

          case "4v4":
            type = FOUR_V_FOUR;
            break;

          default:
            logger.send(player, JOIN_INVALIDTYPE, matchType, OR.toString());
            return true;
        }

        matchManager.joinQueue(player, type);
        break;

      case "leave":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) {
          logger.send(player, NO_PERM, PERM_PLAY, label + " " + sub);
          return true;
        }

        Optional<Match> matchOpt = matchManager.getMatch(player);
        if (matchOpt.isPresent()) {
          Match match = matchOpt.get();
          if (match.getPhase() == MatchPhase.IN_PROGRESS) {
            MatchPlayer matchPlayer = null;
            for (MatchPlayer mp : match.getPlayers()) {
              if (mp != null && mp.getPlayer() != null && mp.getPlayer().equals(player)) {
                matchPlayer = mp;
                break;
              }
            }

            if (matchPlayer != null) {
              int playerScore = matchPlayer.getTeamColor() == TeamColor.RED
                  ? match.getScoreRed()
                  : match.getScoreBlue();
              int opponentScore = matchPlayer.getTeamColor() == TeamColor.RED
                  ? match.getScoreBlue()
                  : match.getScoreRed();

              if (playerScore < opponentScore) {
                fcManager.getEconomy().withdrawPlayer(player, 200);
                matchManager.getBanManager().banPlayer(player, 30 * 60 * 1000);
                logger.send(player, LEAVE_LOSING);
              }
            }
          }

          matchManager.leaveMatch(player);
          logger.send(player, LEFT);
          logger.sendActionBar(player, LEAVE_QUEUE_ACTIONBAR,
              match.getArena().getType() + "v" + match.getArena().getType()
          );

          teamManager.forceDisbandTeam(player);
        } else {
          boolean leftQueue = false;
          for (int queueType : matchManager.getData().getPlayerQueues().keySet()) {
            Queue<Player> queue = matchManager.getData().getPlayerQueues().get(queueType);
            if (queue != null && queue.contains(player)) {
              matchManager.leaveQueue(player, queueType);
              leftQueue = true;
              teamManager.disbandTeamIfInLobby(player);
            }
          }

          if (leftQueue) {
            logger.send(player, LEFT);
          } else {
            logger.send(player, LEAVE_NOT_INGAME);
          }
        }
        break;

      case "takeplace":
      case "tkp":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) {
          logger.send(player, NO_PERM, PERM_PLAY, label + " " + sub);
          return true;
        }

        if (!matchManager.getData().isMatchesEnabled()) {
          logger.send(player, FC_DISABLED);
          return true;
        }

        if (matchManager.getBanManager().isBanned(player)) {
          return true;
        }

        if (matchManager.getMatch(player).isPresent()) {
          logger.send(player, TAKEPLACE_INGAME);
          return true;
        }

        if (matchManager.getData().getOpenMatches().isEmpty()) {
          logger.send(player, TAKEPLACE_NOPLACE);
          return true;
        }

        List<Match> openMatches = matchManager.getData().getOpenMatches();
        if (args.length < 2) {
          Match openMatch = openMatches.iterator().next();
          matchManager.takePlace(player, openMatch.getArena().getId());
          return true;
        }

        if (args[1].equalsIgnoreCase("list")) {
          logger.send(player, TAKEPLACE_AVAILABLE_HEADER);

          for (Match openMatch : openMatches) {
            int emptySlots = 0;
            for (MatchPlayer mp : openMatch.getPlayers()) {
              if (mp == null) {
                emptySlots++;
              }
            }

            logger.send(player, TAKEPLACE_AVAILABLE_ENTRY,
                String.valueOf(openMatch.getArena().getId()),
                openMatch.getArena().getType() + "v" + openMatch.getArena().getType(),
                String.valueOf(emptySlots)
            );
          }
          return true;
        }

        try {
          int matchId = Integer.parseInt(args[1]);
          matchManager.takePlace(player, matchId);
        } catch (NumberFormatException exception) {
          logger.send(player, STATSSET_IS_NOT_A_NUMBER, args[1]);
        }
        break;

      case "teamchat":
      case "tc":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (args.length < 2) {
          logger.send(player, USAGE, label + " " + sub + " <message>");
          return true;
        }

        matchManager.teamChat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        break;

      case "team":
      case "t":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_PLAY)) {
          logger.send(player, NO_PERM, PERM_PLAY, label + " " + sub);
          return true;
        }

        if (!matchManager.getData().isMatchesEnabled()) {
          logger.send(player, FC_DISABLED);
          return true;
        }

        if (args.length < 2) {
          logger.send(player, TEAM_USAGE, OR.toString());
          return true;
        }

        String action = args[1].toLowerCase(), targetName;
        Player target;
        switch (action) {
          case "accept":
            if (teamManager.isInTeam(player)) {
              logger.send(player, TEAM_ALREADY_IN_TEAM);
              return true;
            }

            if (teamManager.noInvite(player)) {
              logger.send(player, TEAM_NO_REQUEST);
              return true;
            }

            target = teamManager.getInviter(player);
            targetName = target != null && target.isOnline()
                ? target.getName()
                : "";
            if (target == null || !target.isOnline()) {
              logger.send(player, TEAM_NOT_ONLINE, targetName);
              return true;
            }

            if (teamManager.isInTeam(target)) {
              logger.send(player, TEAM_ALREADY_IN_TEAM_2, target.getName());
              teamManager.removeInvite(player);
              return true;
            }

            int mType = teamManager.getInviteMatchType(player);
            teamManager.createTeam(target, player, mType);
            logger.send(player, TEAM_ACCEPT_SELF, target.getName());
            logger.send(target, TEAM_ACCEPT_OTHER, player.getName());

            matchManager.joinQueue(player, mType);
            teamManager.removeInvite(player);
            break;

          case "decline":
            if (teamManager.noInvite(player)) {
              logger.send(player, TEAM_NO_REQUEST);
              return true;
            }

            target = teamManager.getInviter(player);
            if (target != null && target.isOnline()) {
              logger.send(target, TEAM_DECLINE_OTHER, player.getName());
            }

            logger.send(player, TEAM_DECLINE_SELF);
            teamManager.removeInvite(player);
            break;

          default:
            if (args.length < 3) {
              logger.send(player, TEAM_USAGE, OR.toString());
              return true;
            }

            if (teamManager.isInTeam(player)) {
              logger.send(player, TEAM_ALREADY_IN_TEAM);
              return true;
            }

            targetName = args[2];
            target = plugin.getServer().getPlayer(targetName);
            if (target == null) {
              logger.send(player, TEAM_NOT_ONLINE, targetName);
              return true;
            }

            if (teamManager.isInTeam(target)) {
              logger.send(player, TEAM_ALREADY_IN_TEAM_2, target.getName());
              return true;
            }

            if (fcManager.getMatchSystem().isInAnyQueue(player)) {
              logger.send(player, JOIN_ALREADYINGAME);
              return true;
            }

            if (fcManager.getMatchSystem().isInAnyQueue(target)) {
              logger.send(player, TEAM_ALREADY_IN_GAME, targetName);
              return true;
            }

            if (matchManager.getMatch(player).isPresent()) {
              logger.send(player, JOIN_ALREADYINGAME);
              return true;
            }

            if (matchManager.getMatch(target).isPresent()) {
              logger.send(player, TEAM_ALREADY_IN_GAME, targetName);
              return true;
            }

            String inviteMatchType = args[1].toLowerCase();
            int inviteType;
            switch (inviteMatchType) {
              case "2v2":
                inviteType = TWO_V_TWO;
                break;

              case "3v3":
                inviteType = THREE_V_THREE;
                break;

              case "4v4":
                inviteType = FOUR_V_FOUR;
                break;

              default:
                logger.send(player, JOIN_INVALIDTYPE, inviteMatchType, OR.toString());
                return true;
            }

            teamManager.invite(player, target, inviteType);
            logger.send(player, TEAM_WANTS_TO_TEAM_SELF, target.getName(), inviteMatchType);
            logger.send(target, TEAM_WANTS_TO_TEAM_OTHER, player.getName(), inviteMatchType);
            break;
        }
        break;

      case "stats":
        if (sender instanceof Player) {
          Player p = (Player) sender;
          fcManager.getMatchSystem().checkStats(args.length > 1
              ? args[1]
              : p.getName(), sender);
        } else {
          if (args.length < 2) {
            logger.send(sender, "&cYou need to specify a player.");
          } else {
            fcManager.getMatchSystem().checkStats(args[1], sender);
          }
        }
        break;

      case "highscores":
      case "best":
        fcManager.getHighscoreManager().showHighScores(sender);
        break;

      case "cube":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_CUBE)) {
          logger.send(player, NO_PERM, PERM_CUBE, label + " " + sub);
          return true;
        }

        if (player.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
          logger.send(player, "{prefix-admin}&cDifficulty ne sme biti na peaceful.");
          return true;
        }

        if (matchManager.getMatch(player).isPresent()) {
          logger.send(player, COMMAND_DISABLER_CANT_USE);
          return true;
        }

        if (system.cantSpawnYet(player)) {
          return true;
        }

        Location loc = player.getLocation();
        Vector dir = loc.getDirection().normalize();
        Location spawnLoc;
        if (player.getGameMode() != GameMode.CREATIVE && !player.isFlying()) {
          spawnLoc = loc.add(dir.multiply(2.0));
          spawnLoc.setY(loc.getY() + 2.5);
        } else {
          spawnLoc = loc;
        }

        system.spawnCube(spawnLoc);
        system.setButtonCooldown(player);
        logger.send(player, CUBE_SPAWN);
        break;

      case "clearcube":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_CLEAR_CUBE)) {
          logger.send(sender, NO_PERM, PERM_CLEAR_CUBE, label + " " + sub);
          return true;
        }

        if (matchManager.getMatch(player).isPresent()) {
          logger.send(player, COMMAND_DISABLER_CANT_USE);
          return true;
        }

        double closestDistance = 200;
        Slime closest = null;
        for (Slime cube : physicsData.getCubes()) {
          double distance = cube.getLocation().distance(player.getLocation());
          if (distance < closestDistance) {
            closestDistance = distance;
            closest = cube;
          }
        }

        if (closest != null) {
          closest.setHealth(0);
          logger.send(player, CUBE_CLEAR);
        } else {
          logger.send(player, CUBE_NO_CUBES);
        }
        break;

      case "clearcubes":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_CLEAR_CUBE)) {
          logger.send(sender, NO_PERM, PERM_CLEAR_CUBE, label + " " + sub);
          return true;
        }

        if (matchManager.getMatch(player).isPresent()) {
          logger.send(player, COMMAND_DISABLER_CANT_USE);
          return true;
        }

        int count = 0;
        for (Slime cube : physicsData.getCubes()) {
          cube.setHealth(0);
          count++;
        }

        logger.send(player, CUBE_CLEAR_ALL, String.valueOf(count));
        break;

      case "toggles":
      case "ts":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (args.length < 2) {
          logger.send(player, USAGE,
              label + " " + sub + " <kick|goal|particles|hits> <value|list>");
          return true;
        }

        String toggleType = args[1].toLowerCase();
        playerData = dataManager.get(player);
        if (playerData == null) {
          return true;
        }

        settings = fcManager.getPlayerSettings(player);
        switch (toggleType) {
          case "kick":
            settings.setKickSoundEnabled(!settings.isKickSoundEnabled());
            playerData.set("sounds.kick.enabled", settings.isKickSoundEnabled());
            logger.send(player, TOGGLES_KICK, settings.isKickSoundEnabled()
                ? ON.toString()
                : OFF.toString());
            break;

          case "goal":
            settings.setGoalSoundEnabled(!settings.isGoalSoundEnabled());
            playerData.set("sounds.goal.enabled", settings.isGoalSoundEnabled());
            logger.send(player, TOGGLES_GOAL, settings.isGoalSoundEnabled()
                ? ON.toString()
                : OFF.toString());
            break;

          case "particles":
            settings.setParticlesEnabled(!settings.isParticlesEnabled());
            playerData.set("particles.enabled", settings.isParticlesEnabled());
            logger.send(player, TOGGLES_PARTICLES, settings.isParticlesEnabled()
                ? ON.toString()
                : OFF.toString());
            break;

          case "hits":
            boolean wasEnabled = physicsData.getCubeHits().contains(player.getUniqueId());

            if (wasEnabled) {
              physicsData.getCubeHits().remove(player.getUniqueId());
            } else {
              physicsData.getCubeHits().add(player.getUniqueId());
            }

            logger.send(player, TOGGLES_HIT_DEBUG, !wasEnabled
                ? ON.toString()
                : OFF.toString());
            break;

          default:
            logger.send(player, USAGE,
                label + " " + sub + " <kick|goal|particles|hits> <value|list>");
        }
        break;

      case "setsound":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_SET_SOUND)) {
          logger.send(sender, NO_PERM, PERM_SET_SOUND, label + " " + sub);
          return true;
        }

        if (args.length <= 2) {
          logger.send(player, USAGE, label + " " + sub + " <kick|goal> <soundName|list>");
          return true;
        }

        playerData = dataManager.get(player);
        if (playerData == null) {
          return true;
        }

        settings = fcManager.getPlayerSettings(player);
        Sound sound = Sound.SUCCESSFUL_HIT;
        try {
          if (!args[2].equalsIgnoreCase("list")) {
            sound = Sound.valueOf(args[2].toUpperCase());
          }
        } catch (Exception exception) {
          logger.send(player, INVALID_TYPE, SOUND.toString());
          return true;
        }

        switch (args[1].toLowerCase()) {
          case "kick":
            if (args[2].equalsIgnoreCase("list")) {
              StringJoiner joiner = new StringJoiner(", ");
              for (Sound s : PlayerSettings.ALLOWED_KICK_SOUNDS) {
                joiner.add(s.name());
              }

              logger.send(player, AVAILABLE_TYPE, SOUND.toString(), joiner.toString());
              return true;
            }

            if (!PlayerSettings.ALLOWED_KICK_SOUNDS.contains(sound)) {
              logger.send(player, INVALID_TYPE, SOUND.toString());
              StringJoiner joiner = new StringJoiner(", ");
              for (Sound s : PlayerSettings.ALLOWED_KICK_SOUNDS) {
                joiner.add(s.name());
              }

              logger.send(player, AVAILABLE_TYPE, SOUND.toString(), joiner.toString());
              return true;
            }

            settings.setKickSound(sound);
            playerData.set("sounds.kick.sound", sound.toString());
            logger.send(player, SET_SOUND_KICK, sound.name());
            break;

          case "goal":
            if (args[2].equalsIgnoreCase("list")) {
              StringJoiner joiner = new StringJoiner(", ");
              for (Sound s : PlayerSettings.ALLOWED_GOAL_SOUNDS) {
                joiner.add(s.name());
              }

              logger.send(player, AVAILABLE_TYPE, SOUND.toString(), joiner.toString());
              return true;
            }

            if (!PlayerSettings.ALLOWED_GOAL_SOUNDS.contains(sound)) {
              logger.send(player, INVALID_TYPE, SOUND.toString());
              StringJoiner joiner = new StringJoiner(", ");
              for (Sound s : PlayerSettings.ALLOWED_GOAL_SOUNDS) {
                joiner.add(s.name());
              }

              logger.send(player, AVAILABLE_TYPE, SOUND.toString(), joiner.toString());
              return true;
            }

            settings.setGoalSound(sound);
            playerData.set("sounds.goal.sound", sound.toString());
            logger.send(player, SET_SOUND_GOAL, sound.name());
            break;

          default:
            logger.send(player, USAGE, label + " " + sub + " <kick|goal> <soundName|list>");
            break;
        }
        break;

      case "setparticle":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_SET_PARTICLE)) {
          logger.send(sender, NO_PERM, PERM_SET_PARTICLE, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(player, USAGE, label + " " + sub + " <particleName|list> [color]");
          return true;
        }

        if (args[1].equalsIgnoreCase("list")) {
          logger.send(player, AVAILABLE_TYPE, PARTICLE.toString(),
              String.join(", ", PlayerSettings.getAllowedParticles()));
          return true;
        }

        playerData = dataManager.get(player);
        if (playerData == null) {
          return true;
        }

        settings = fcManager.getPlayerSettings(player);
        EnumParticle particle;
        try {
          particle = EnumParticle.valueOf(args[1].toUpperCase());
        } catch (Exception exception) {
          logger.send(player, INVALID_TYPE, PARTICLE.toString());
          logger.send(player, AVAILABLE_TYPE, PARTICLE.toString(),
              String.join(", ", PlayerSettings.getAllowedParticles()));
          return true;
        }

        if (PlayerSettings.DISALLOWED_PARTICLES.contains(particle)) {
          logger.send(player, INVALID_TYPE, PARTICLE.toString());
          logger.send(player, AVAILABLE_TYPE, PARTICLE.toString(),
              String.join(", ", PlayerSettings.getAllowedParticles()));
          return true;
        }

        if (particle == EnumParticle.REDSTONE) {
          if (args.length >= 3) {
            try {
              String colorName = args[2].toUpperCase();
              settings.setCustomRedstoneColor(colorName);
              playerData.set("particles.effect", "REDSTONE:" + colorName);
              logger.send(player, SET_PARTICLE_REDSTONE, particle.name(), colorName);
              return true;
            } catch (IllegalArgumentException exception) {
              logger.send(player, INVALID_COLOR, args[2].toUpperCase());
              logger.send(player, AVAILABLE_TYPE, COLOR.toString(),
                  String.join(", ", PlayerSettings.getAllowedColorNames()));
              return true;
            }
          } else {
            settings.setCustomRedstoneColor("WHITE");
            playerData.set("particles.effect", "REDSTONE:WHITE");
            logger.send(player, SET_PARTICLE_REDSTONE, particle.name(), "WHITE");
          }

          settings.setParticle(EnumParticle.REDSTONE);
          return true;
        }

        settings.setParticle(particle);
        playerData.set("particles.effect", particle.toString());
        logger.send(player, SET_PARTICLE, particle.name());
        break;

      case "setgoalcelebration":
      case "sgc":
        if (!(sender instanceof Player)) {
          return inGameOnly(sender);
        }

        player = (Player) sender;
        if (!player.hasPermission(PERM_SET_GOAL_CELEBRATION)) {
          logger.send(sender, NO_PERM, PERM_SET_GOAL_CELEBRATION, label + " " + sub);
          return true;
        }

        if (args.length < 2) {
          logger.send(player, USAGE, label + " " + sub + " <default|simple|epic|minimal|list>");
          return true;
        }

        playerData = dataManager.get(player);
        if (playerData == null) {
          return true;
        }

        settings = fcManager.getPlayerSettings(player);
        String celebrationType = args[1].toLowerCase();
        String[] validTypes = new String[]{"default", "simple", "epic", "minimal"};

        if (celebrationType.equalsIgnoreCase("list")) {
          logger.send(sender, AVAILABLE_TYPE, "goal celebrations",
              String.join(", ", validTypes));
          return true;
        }

        if (!celebrationType.equalsIgnoreCase("default")
            && !celebrationType.equalsIgnoreCase("simple")
            && !celebrationType.equalsIgnoreCase("epic")
            && !celebrationType.equalsIgnoreCase("minimal")) {
          logger.send(player, INVALID_TYPE, "goal celebrations");
          logger.send(sender, AVAILABLE_TYPE, "goal celebrations",
              String.join(", ", validTypes));
          return true;
        }

        settings.setGoalMessage(celebrationType);
        playerData.set("goalcelebration", celebrationType);
        logger.send(player, SET_GOAL_CELEBRATION, celebrationType);
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
    logger.send(sender, HELP);
  }

  private void sendBanner(CommandSender sender) {
    if (sender instanceof Player) {
      StringJoiner joiner = new StringJoiner(", ");
      List<String> authors = plugin.getDescription().getAuthors();
      if (authors != null) {
        for (String author : authors) {
          joiner.add(author);
        }
      }

      logger.send(sender, BANNER_PLAYER, plugin.getName(),
          plugin.getDescription().getVersion(), joiner.toString());
    } else {
      fcManager.sendBanner();
      logger.send(sender, "&aKucajte &e/fc help &aza listu dostupnih komandi.");
    }
  }

  private boolean inGameOnly(CommandSender sender) {
    logger.send(sender, INGAME_ONLY);
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias,
      String[] args) {
    List<String> completions = new ArrayList<>();

    if (args.length == 1) {
      completions.addAll(Arrays.asList(
          "join", "leave", "takeplace", "tkp", "team", "t", "stats", "best",
          "highscores", "toggles", "ts", "cube", "clearcube", "clearcubes",
          "help", "h", "setsound", "setparticle", "setgoalcelebration", "sgc"
      ));
    } else {
      if (args.length == 2) {
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

          case "setgoalcelebration":
          case "sgc":
            completions.addAll(Arrays.asList("default", "simple", "epic", "minimal", "list"));
            break;
        }
      } else {
        if (args.length == 3) {
          String sub = args[0].toLowerCase();
          if (sub.equalsIgnoreCase("setsound")) {
            completions.add("list");
            List<Sound> sounds = args[1].equalsIgnoreCase("kick")
                ? PlayerSettings.ALLOWED_KICK_SOUNDS
                : PlayerSettings.ALLOWED_GOAL_SOUNDS;
            for (Sound s : sounds) {
              completions.add(s.name());
            }
          } else {
            if (sub.equalsIgnoreCase("setparticle")) {
              if (args[1].equalsIgnoreCase("list")) {
                completions.addAll(PlayerSettings.getAllowedParticles());
              } else {
                if (args[1].equalsIgnoreCase("redstone")) {
                  completions.addAll(PlayerSettings.getAllowedColorNames());
                }
              }
            } else {
              if (sub.equalsIgnoreCase("team") || sub.equalsIgnoreCase("t")) {
                fcManager.getCachedPlayers().forEach(p -> completions.add(p.getName()));
              }
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
