package io.github.divinerealms.footcube.matchmaking.logic;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.arena.Arena;
import io.github.divinerealms.footcube.matchmaking.arena.ArenaManager;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
import io.github.divinerealms.footcube.matchmaking.scoreboard.ScoreManager;
import io.github.divinerealms.footcube.matchmaking.team.Team;
import io.github.divinerealms.footcube.matchmaking.team.TeamManager;
import io.github.divinerealms.footcube.matchmaking.util.MatchConstants;
import io.github.divinerealms.footcube.matchmaking.util.MatchUtils;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MatchSystem {
  private final FCManager fcManager;
  private final Logger logger;
  private final ScoreManager scoreboardManager;
  private final MatchData data;
  private final ArenaManager arenaManager;
  private final TeamManager teamManager;

  public MatchSystem(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.scoreboardManager = fcManager.getScoreboardManager();
    this.data = fcManager.getMatchData();
    this.arenaManager = fcManager.getArenaManager();
    this.teamManager = fcManager.getTeamManager();
  }

  public void startMatch(Match match) {
    if (match == null || match.getPlayers() == null) return;

    scoreboardManager.createMatchScoreboard(match);

    for (MatchPlayer matchPlayer : match.getPlayers()) {
      if (matchPlayer == null) continue;
      Player player = matchPlayer.getPlayer();
      if (player == null || !player.isOnline()) continue;

      MatchUtils.giveArmor(player, matchPlayer.getTeamColor());
      scoreboardManager.showMatchScoreboard(match, player);
      logger.send(player, Lang.MATCH_STARTED.replace(null));
    }

    startRound(match);
  }

  public void startRound(Match match) {
    if (match == null || match.getPlayers() == null) return;

    for (MatchPlayer matchPlayer : match.getPlayers()) {
      if (matchPlayer == null) continue;
      Player player = matchPlayer.getPlayer();
      if (player == null || !player.isOnline()) continue;

      if (matchPlayer.getTeamColor() == TeamColor.RED) player.getPlayer().teleport(match.getArena().getRedSpawn());
      else player.getPlayer().teleport(match.getArena().getBlueSpawn());
      player.playSound(player.getLocation(), Sound.EXPLODE, 1, 1);
    }

    handleCubeSpawn(match);
  }

  public void handleCubeSpawn(Match match) {
    if (match.getCube() != null && !match.getCube().isDead()) return;

    Arena arena = match.getArena();
    Slime cube = fcManager.getPhysicsSystem().spawnCube(arena.getCenter());
    match.setCube(cube);

    Random random = new Random();
    double vertical = 0.3 * random.nextDouble() + 0.2;
    double horizontal = 0.3 * random.nextDouble() + 0.3;
    if (random.nextBoolean()) horizontal *= -1;

    boolean x = Math.abs(arena.getBlueSpawn().getX() - arena.getRedSpawn().getX()) > Math.abs(arena.getBlueSpawn().getZ() - arena.getRedSpawn().getZ());
    if (x) match.getCube().setVelocity(new Vector(0, vertical, horizontal));
    else match.getCube().setVelocity(new Vector(horizontal, vertical, 0));
  }

  public void handleCubeRespawn(Match match) {
    if (match.getCube() != null && !match.getCube().isDead()) return;
    if (match.getPhase() != MatchPhase.IN_PROGRESS) return;

    startRound(match);
    if (match.getPlayers() == null) return;
    for (MatchPlayer player : match.getPlayers()) {
      if (player == null || player.getPlayer() == null) continue;
      logger.send(player.getPlayer(), Lang.CLEARED_CUBE_INGAME.replace(null));
    }
  }

  public void handleMatchTimer(Match match) {
    if (match.getPhase() != MatchPhase.IN_PROGRESS) return;

    long matchDuration = match.getArena().getType() == MatchConstants.TWO_V_TWO ? 120 : 300;
    long totalActiveElapsedMillis = (System.currentTimeMillis() - match.getStartTime() - match.getTotalPausedTime());
    long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(totalActiveElapsedMillis);

    if (elapsedSeconds >= matchDuration) match.setPhase(MatchPhase.ENDED);
  }

  public void handleGoalDetection(Match match) {
    Slime cube = match.getCube();
    if (cube == null) return;

    Location cubeLocation = cube.getLocation();
    Arena arena = match.getArena();
    double cubeRadius = 0.26;

    if (arena.isXAxis()) {
      if (arena.isRedIsGreater() && cubeLocation.getX() + cubeRadius > arena.getRedSpawn().getX() || !arena.isRedIsGreater() && cubeLocation.getX() - cubeRadius < arena.getRedSpawn().getX()) {
        score(match, TeamColor.BLUE);
      } else if (arena.isRedIsGreater() && cubeLocation.getX() - cubeRadius < arena.getBlueSpawn().getX() || !arena.isRedIsGreater() && cubeLocation.getX() + cubeRadius > arena.getBlueSpawn().getX()) {
        score(match, TeamColor.RED);
      }
    } else {
      if (arena.isRedIsGreater() && cubeLocation.getZ() + cubeRadius > arena.getRedSpawn().getZ() || !arena.isRedIsGreater() && cubeLocation.getZ() - cubeRadius < arena.getRedSpawn().getZ()) {
        score(match, TeamColor.BLUE);
      } else if (arena.isRedIsGreater() && cubeLocation.getZ() - cubeRadius < arena.getBlueSpawn().getZ() || !arena.isRedIsGreater() && cubeLocation.getZ() + cubeRadius > arena.getBlueSpawn().getZ()) {
        score(match, TeamColor.RED);
      }
    }
  }

  private void score(Match match, TeamColor scoringTeam) {
    if (match.getPhase() != MatchPhase.IN_PROGRESS) return;
    if (scoringTeam == TeamColor.RED) match.setScoreRed(match.getScoreRed() + 1);
    else match.setScoreBlue(match.getScoreBlue() + 1);

    match.setPauseStartTime(System.currentTimeMillis());

    if (match.getCube() != null) match.getCube().setHealth(0);
    match.setCube(null);

    MatchPlayer scorer = match.getLastTouch();
    MatchPlayer assister = null;

    String scorerName = Lang.NOBODY.replace(null);
    boolean ownGoal = false;

    Location goalLoc = (scoringTeam == TeamColor.RED) ? match.getArena().getBlueSpawn() : match.getArena().getRedSpawn();
    if (scorer != null) {
      scorerName = fcManager.getChat().getPlayerPrefix(scorer.getPlayer()) + scorer.getPlayer().getName();
      MatchPlayer second = match.getSecondLastTouch();

      if (scorer.getTeamColor() == scoringTeam) {
        scorer.incrementGoals();
        if (second != null && second.getTeamColor() == scoringTeam) {
          if (!scorer.equals(second)) {
            assister = second;
            assister.incrementAssists();
          }
        }
      } else {
        if (second.getTeamColor() == scoringTeam) {
          scorer = second;
          scorer.incrementGoals();
        } else {
          ownGoal = true;
          scorer.incrementOwnGoals();
        }
      }
    }

    for (MatchPlayer p : match.getPlayers()) {
      if (p == null || p.getPlayer() == null || !p.getPlayer().isOnline()) continue;
      Player player = p.getPlayer();

      PlayerSettings settings = fcManager.getPlayerSettings(player);
      if (settings != null && settings.isGoalSoundEnabled()) player.playSound(player.getLocation(), settings.getGoalSound(), 1, 1);
      player.playEffect(goalLoc, Effect.EXPLOSION_HUGE, null);

      double distanceToGoal = player.getLocation().distance(goalLoc);
      if (distanceToGoal <= 30) {
        Vector launchDir = player.getLocation().toVector().subtract(goalLoc.toVector()).normalize();
        player.setVelocity(launchDir.setY(0.5).multiply(1.5));
      }

      String assisterName = (assister != null) ? fcManager.getChat().getPlayerPrefix(assister.getPlayer()) + assister.getPlayer().getName() : null;
      String goalMessage;
      if (ownGoal) {
        goalMessage = Lang.MATCH_SCORE_OWN_GOAL_ANNOUNCE.replace(new String[]{scorerName, scoringTeam == TeamColor.RED ? Lang.RED.replace(null) : Lang.BLUE.replace(null)});
      } else {
        goalMessage = Lang.MATCH_GOAL.replace(new String[]{
            scorerName,
            scoringTeam == TeamColor.RED ? Lang.RED.replace(null) : Lang.BLUE.replace(null),
            String.format("%.0f", scorer != null ? scorer.getPlayer().getLocation().distance(goalLoc) : 0),
            assisterName != null ? Lang.MATCH_GOAL_ASSIST.replace(new String[]{assisterName}) : ""
        });
      }

      logger.send(player, goalMessage);
      logger.title(player, (ownGoal ? Lang.MATCH_OWN_GOAL_TITLE.replace(null) : Lang.MATCH_GOAL_TITLE.replace(null)), Lang.MATCH_GOAL_SUBTITLE.replace(new String[]{scorerName}), 10, 30, 10);
      logger.send(player, Lang.MATCH_SCORE_STATS.replace(new String[]{String.valueOf(match.getScoreRed()), String.valueOf(match.getScoreBlue())}));
    }

    match.setPhase(MatchPhase.CONTINUING);
    match.setCountdown(5);
    match.setTick(0);
    scoreboardManager.updateScoreboard(match);
  }

  public void updateMatch(Match match) {
    match.setTick(match.getTick() + 1);

    long currentPlayers = match.getPlayers().stream().filter(Objects::nonNull).count();
    long requiredPlayers = match.getArena().getType() * 2L;

    switch (match.getPhase()) {
      case LOBBY:
        if (currentPlayers >= requiredPlayers) {
          List<MatchPlayer> playersToAssign = match.getPlayers().stream()
              .filter(Objects::nonNull).collect(Collectors.toList());

          Optional<Team> firstTeam = playersToAssign.stream()
              .map(p -> teamManager.getTeam(p.getPlayer()))
              .filter(Objects::nonNull)
              .findFirst();

          if (firstTeam.isPresent()) {
            Team team = firstTeam.get();
            List<Player> teamMembers = team.getMembers();
            List<MatchPlayer> teamMatchPlayers = new ArrayList<>();
            List<MatchPlayer> soloPlayers = new ArrayList<>();

            for (MatchPlayer matchPlayer : playersToAssign) {
              if (teamMembers.contains(matchPlayer.getPlayer())) teamMatchPlayers.add(matchPlayer);
              else soloPlayers.add(matchPlayer);
            }

            for (MatchPlayer teamPlayer : teamMatchPlayers) teamPlayer.setTeamColor(TeamColor.RED);
            Collections.shuffle(soloPlayers);

            long redTeamSize = teamMatchPlayers.size();
            for (MatchPlayer soloPlayer : soloPlayers) {
              if (redTeamSize < requiredPlayers / 2) {
                soloPlayer.setTeamColor(TeamColor.RED);
                redTeamSize++;
              } else soloPlayer.setTeamColor(TeamColor.BLUE);
            }
            teamManager.disbandTeam(team);
          } else {
            Collections.shuffle(playersToAssign);
            for (int i = 0; i < playersToAssign.size(); i++) {
              playersToAssign.get(i).setTeamColor(i < requiredPlayers / 2 ? TeamColor.RED : TeamColor.BLUE);
            }
          }

          match.getPlayers().stream().filter(Objects::nonNull).forEach(player -> logger.send(player.getPlayer(), Lang.STARTING.replace(null)));
          match.setPhase(MatchPhase.STARTING);
          match.setCountdown(15);
          match.setTick(0);
          scoreboardManager.updateScoreboard(match);
        }
        break;

      case STARTING:
        if (match.getTick() % 20 == 0) {
          match.setCountdown(match.getCountdown() - 1);
          scoreboardManager.updateScoreboard(match);
          match.getPlayers().stream().filter(Objects::nonNull).filter(p -> p.getPlayer() != null).forEach(p -> {
            p.getPlayer().setLevel(match.getCountdown());
            p.getPlayer().playSound(p.getPlayer().getLocation(), Sound.NOTE_STICKS, 1, 1);
            if (p.getPlayer().getGameMode() != GameMode.SURVIVAL) p.getPlayer().setGameMode(GameMode.SURVIVAL);
            if (p.getPlayer().isFlying()) { p.getPlayer().setFlying(false); p.getPlayer().setAllowFlight(false); }
            if (match.getCountdown() == 10) {
              if (p.getTeamColor() == TeamColor.RED) p.getPlayer().teleport(match.getArena().getRedSpawn());
              else p.getPlayer().teleport(match.getArena().getBlueSpawn());
            }
          });

          if (match.getCountdown() <= 0) {
            match.setPhase(MatchPhase.IN_PROGRESS);
            match.setStartTime(System.currentTimeMillis());
            startMatch(match);
          }
        }
        break;

      case IN_PROGRESS:
        if (match.getTick() % 20 == 0) scoreboardManager.updateScoreboard(match);
        handleGoalDetection(match);
        handleMatchTimer(match);
        handleCubeRespawn(match);
        if (currentPlayers < requiredPlayers && !match.isTakePlaceNeeded()) match.setTakePlaceNeeded(true);
        if (match.isTakePlaceNeeded()) {
          boolean firstAnnouncement = match.getLastTakePlaceAnnounceTick() == 0;
          if (firstAnnouncement || match.getTick() - match.getLastTakePlaceAnnounceTick() >= 20 * 20) {
            match.setLastTakePlaceAnnounceTick(match.getTick());

            int matchType = match.getArena().getType(), remainingSeconds = match.getCountdown();
            String announcement = match.getPhase() == MatchPhase.CONTINUING
                ? Lang.TAKEPLACE_ANNOUNCEMENT_2.replace(new String[]{String.valueOf(matchType), String.valueOf(currentPlayers), Utilities.formatTimePretty(remainingSeconds)})
                : Lang.TAKEPLACE_ANNOUNCEMENT.replace(new String[]{String.valueOf(matchType)});
            String wholeMessage = announcement + System.lineSeparator() + Lang.TAKEPLACE_ANNOUNCEMENT_3.replace(null);

            Bukkit.getOnlinePlayers().stream()
                .filter(Objects::nonNull)
                .filter(player -> !fcManager.getMatchManager().getMatch(player).isPresent())
                .forEach(player -> logger.send(player, wholeMessage));
          }
        }
        break;

      case CONTINUING:
        if (match.getTick() % 20 == 0) {
          match.setCountdown(match.getCountdown() - 1);
          scoreboardManager.updateScoreboard(match);
          match.getPlayers().stream().filter(Objects::nonNull).filter(p -> p.getPlayer() != null).forEach(p -> {
            p.getPlayer().setLevel(match.getCountdown());
            p.getPlayer().playSound(p.getPlayer().getLocation(), Sound.NOTE_STICKS, 1, 1);
          });

          if (match.getCountdown() <= 0) {
            match.setPhase(MatchPhase.IN_PROGRESS);
            match.getPlayers().stream().filter(Objects::nonNull).filter(p -> p.getPlayer() != null)
                .forEach(p -> logger.send(p.getPlayer(), Lang.MATCH_PROCEED.replace(null)));
            startRound(match);
          }
        }
        break;

      case ENDED:
        fcManager.getMatchManager().endMatch(match);
        break;
    }

    if (match.getPhase() == MatchPhase.ENDED || currentPlayers >= requiredPlayers) {
      match.setTakePlaceNeeded(false);
      match.setLastTakePlaceAnnounceTick(0);
    }
  }

  public void processQueues() {
    for (Map.Entry<Integer, Queue<Player>> entry : data.getPlayerQueues().entrySet()) {
      int matchType = entry.getKey();
      Queue<Player> queue = entry.getValue();

      if (queue.isEmpty()) continue;
      if (!data.getLockedQueues().add(matchType)) continue;

      try {
        processSingleQueue(matchType, queue);
      } finally {
        data.getLockedQueues().remove(matchType);
      }
    }
  }

  private void processSingleQueue(int matchType, Queue<Player> queue) {
    while (!queue.isEmpty()) {
      queue.removeIf(p -> p == null || !p.isOnline());
      Player player = queue.peek();
      if (player == null || !player.isOnline()) { queue.poll(); continue; }

      Team team = teamManager.getTeam(player);
      List<Player> playerGroup;
      if (team != null) {
        playerGroup = new ArrayList<>(team.getMembers());
        playerGroup.removeIf(p -> p == null || !p.isOnline());
        if (!playerGroup.contains(player)) { queue.poll(); continue; }
        if (!queue.containsAll(playerGroup)) return;
      } else playerGroup = Collections.singletonList(player);

      Match targetMatch = findLobbyForGroup(matchType, playerGroup.size())
          .orElseGet(() -> createNewLobby(matchType));
      if (targetMatch == null) { playerGroup.forEach(p -> logger.send(p, Lang.JOIN_NOARENA.replace(null))); break; }
      if (targetMatch.getLobbyScoreboard() == null) scoreboardManager.createLobbyScoreboard(targetMatch);

      for (Player p : playerGroup) {
        queue.remove(p);
        targetMatch.getPlayers().add(new MatchPlayer(p, null));
        scoreboardManager.showLobbyScoreboard(targetMatch, p);
      }

      scoreboardManager.updateScoreboard(targetMatch);
    }
  }

  public boolean isInAnyQueue(Player player) {
    return data.getPlayerQueues().values().stream().anyMatch(queue -> queue.contains(player));
  }

  private Optional<Match> findLobbyForGroup(int matchType, int groupSize) {
    int maxPlayers = matchType * 2;

    return data.getMatches().stream()
        .filter(m -> m.getArena().getType() == matchType)
        .filter(m -> m.getPhase() == MatchPhase.LOBBY)
        .filter(m -> m.getPlayers().size() + groupSize <= maxPlayers)
        .min(Comparator.comparingInt(m -> maxPlayers - m.getPlayers().size()));
  }

  private synchronized Match createNewLobby(int matchType) {
    List<Arena> availableArenas = arenaManager.getArenas().stream()
        .filter(a -> a.getType() == matchType && data.getMatches().stream().noneMatch(m -> m.getArena().getId() == a.getId()))
        .collect(Collectors.toList());

    if (availableArenas.isEmpty()) return null;

    Collections.shuffle(availableArenas);
    Arena arena = availableArenas.get(0);

    Match newMatch = new Match(arena, new ArrayList<>());
    data.getMatches().add(newMatch);
    return newMatch;
  }
}