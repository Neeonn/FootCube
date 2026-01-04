package io.github.divinerealms.footcube.matchmaking.logic;

import static io.github.divinerealms.footcube.configs.Lang.BLUE;
import static io.github.divinerealms.footcube.configs.Lang.CLEARED_CUBE_INGAME;
import static io.github.divinerealms.footcube.configs.Lang.JOIN_NOARENA;
import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_LOBBY;
import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_MATCH;
import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_STARTING;
import static io.github.divinerealms.footcube.configs.Lang.MATCH_PREPARATION_SUBTITLE;
import static io.github.divinerealms.footcube.configs.Lang.MATCH_PREPARATION_TITLE;
import static io.github.divinerealms.footcube.configs.Lang.MATCH_PREPARING;
import static io.github.divinerealms.footcube.configs.Lang.MATCH_PROCEED;
import static io.github.divinerealms.footcube.configs.Lang.MATCH_STARTED;
import static io.github.divinerealms.footcube.configs.Lang.MATCH_STARTED_ACTIONBAR;
import static io.github.divinerealms.footcube.configs.Lang.MATCH_STARTING_ACTIONBAR;
import static io.github.divinerealms.footcube.configs.Lang.MATCH_STARTING_SUBTITLE;
import static io.github.divinerealms.footcube.configs.Lang.MATCH_STARTING_TITLE;
import static io.github.divinerealms.footcube.configs.Lang.RED;
import static io.github.divinerealms.footcube.configs.Lang.STARTING;
import static io.github.divinerealms.footcube.configs.Lang.STATS;
import static io.github.divinerealms.footcube.configs.Lang.STATS_NONE;
import static io.github.divinerealms.footcube.configs.Lang.TAKE_PLACE_ANNOUNCEMENT_LOBBY;
import static io.github.divinerealms.footcube.configs.Lang.TAKE_PLACE_ANNOUNCEMENT_MATCH;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.FIVE_V_FIVE;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.FOUR_V_FOUR;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.SCOREBOARD_UPDATE_INTERVAL;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.TAKE_PLACE_ANNOUNCEMENT_INTERVAL_TICKS;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.THREE_V_THREE;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.TWO_V_TWO;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.awardCreditsForGoal;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.broadcastGoalMessage;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.determineScoringPlayers;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.getGoalLocation;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.giveArmor;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.playGoalEffects;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.prepareMatchContinuation;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.updateMatchScore;

import io.github.divinerealms.footcube.configs.PlayerData;
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
import io.github.divinerealms.footcube.utils.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

public class MatchSystem {

  private final FCManager fcManager;
  private final Logger logger;
  private final ScoreManager scoreboardManager;
  private final MatchData data;
  private final ArenaManager arenaManager;
  private final TeamManager teamManager;
  private final Utilities utilities;

  public MatchSystem(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.scoreboardManager = fcManager.getScoreboardManager();
    this.data = fcManager.getMatchData();
    this.arenaManager = fcManager.getArenaManager();
    this.teamManager = fcManager.getTeamManager();
    this.utilities = fcManager.getUtilities();

    int[] initialQueueOrder = {TWO_V_TWO, THREE_V_THREE, FOUR_V_FOUR};
    for (int t : initialQueueOrder) {
      data.getQueueLocks().put(t, new ReentrantLock());
    }
  }

  public void startMatch(Match match) {
    if (match == null || match.getPlayers() == null) {
      return;
    }

    scoreboardManager.createMatchScoreboard(match);

    for (MatchPlayer matchPlayer : match.getPlayers()) {
      if (matchPlayer == null) {
        continue;
      }
      Player player = matchPlayer.getPlayer();
      if (player == null || !player.isOnline()) {
        continue;
      }

      scoreboardManager.showMatchScoreboard(match, player);
      logger.send(player, MATCH_STARTED);
    }

    startRound(match);
  }

  public void startRound(Match match) {
    if (match == null || match.getPlayers() == null) {
      return;
    }

    for (MatchPlayer matchPlayer : match.getPlayers()) {
      if (matchPlayer == null) {
        continue;
      }
      Player player = matchPlayer.getPlayer();
      if (player == null || !player.isOnline()) {
        continue;
      }

      if (matchPlayer.getTeamColor() == TeamColor.RED) {
        player.getPlayer().teleport(match.getArena().getRedSpawn());
      } else {
        player.getPlayer().teleport(match.getArena().getBlueSpawn());
      }
      player.playSound(player.getLocation(), Sound.EXPLODE, 1, 1);
    }

    handleCubeSpawn(match);
  }

  public void handleCubeSpawn(Match match) {
    if (match.getCube() != null && !match.getCube().isDead()) {
      return;
    }

    Arena arena = match.getArena();
    Slime cube = fcManager.getPhysicsSystem().spawnCube(arena.getCenter());
    match.setCube(cube);

    ThreadLocalRandom random = ThreadLocalRandom.current();
    double vertical = 0.3 * random.nextDouble() + 0.2;
    double horizontal = 0.3 * random.nextDouble() + 0.3;
    if (random.nextBoolean()) {
      horizontal *= -1;
    }

    boolean x = Math.abs(arena.getBlueSpawn().getX() - arena.getRedSpawn().getX()) >
        Math.abs(arena.getBlueSpawn().getZ() - arena.getRedSpawn().getZ());
    if (x) {
      match.getCube().setVelocity(new Vector(0, vertical, horizontal));
    } else {
      match.getCube().setVelocity(new Vector(horizontal, vertical, 0));
    }
  }

  public void handleCubeRespawn(Match match) {
    if (match.getCube() != null && !match.getCube().isDead()) {
      return;
    }
    if (match.getPhase() != MatchPhase.IN_PROGRESS) {
      return;
    }

    startRound(match);
    if (match.getPlayers() == null) {
      return;
    }
    for (MatchPlayer player : match.getPlayers()) {
      if (player == null || player.getPlayer() == null) {
        continue;
      }
      logger.send(player.getPlayer(), CLEARED_CUBE_INGAME);
    }
  }

  public void handleMatchTimer(Match match) {
    if (match.getPhase() != MatchPhase.IN_PROGRESS) {
      return;
    }

    long matchDuration = match.getArena().getType() == TWO_V_TWO
        ? 120
        : 300;
    long totalActiveElapsedMillis = (System.currentTimeMillis() - match.getStartTime());
    long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(totalActiveElapsedMillis);

    if (elapsedSeconds >= matchDuration) {
      match.setPhase(MatchPhase.ENDED);
    }
  }

  public void handleGoalDetection(Match match) {
    Slime cube = match.getCube();
    if (cube == null) {
      return;
    }

    Location cubeLocation = cube.getLocation();
    Arena arena = match.getArena();
    double cubeRadius = 0.26;

    if (arena.isXAxis()) {
      if (arena.isRedIsGreater()
          && cubeLocation.getX() + cubeRadius > arena.getRedSpawn().getX()
          || !arena.isRedIsGreater()
          && cubeLocation.getX() - cubeRadius < arena.getRedSpawn().getX()) {
        score(match, TeamColor.BLUE);
      } else {
        if (arena.isRedIsGreater()
            && cubeLocation.getX() - cubeRadius < arena.getBlueSpawn().getX()
            || !arena.isRedIsGreater()
            && cubeLocation.getX() + cubeRadius > arena.getBlueSpawn().getX()) {
          score(match, TeamColor.RED);
        }
      }
    } else {
      if (arena.isRedIsGreater()
          && cubeLocation.getZ() + cubeRadius > arena.getRedSpawn().getZ()
          || !arena.isRedIsGreater()
          && cubeLocation.getZ() - cubeRadius < arena.getRedSpawn().getZ()) {
        score(match, TeamColor.BLUE);
      } else {
        if (arena.isRedIsGreater()
            && cubeLocation.getZ() - cubeRadius < arena.getBlueSpawn().getZ()
            || !arena.isRedIsGreater()
            && cubeLocation.getZ() + cubeRadius > arena.getBlueSpawn().getZ()) {
          score(match, TeamColor.RED);
        }
      }
    }
  }

  private void score(Match match, TeamColor scoringTeam) {
    if (match.getPhase() != MatchPhase.IN_PROGRESS) {
      return;
    }
    updateMatchScore(match, scoringTeam);
    ScoringResult scoringResult = determineScoringPlayers(match, scoringTeam);
    if (scoringResult.shouldAwardCredits()) {
      awardCreditsForGoal(scoringResult, logger, fcManager);
    }
    Location goalLoc = getGoalLocation(match, scoringTeam);
    playGoalEffects(match, goalLoc, fcManager);
    broadcastGoalMessage(match, scoringResult, goalLoc, utilities, fcManager, logger);
    prepareMatchContinuation(match, scoreboardManager);
  }

  public void updateMatch(Match match) {
    match.setTick(match.getTick() + 1);

    List<MatchPlayer> players = match.getPlayers();
    int currentPlayers = 0;
    if (players == null) {
      return;
    }

    for (MatchPlayer mp : players) {
      if (mp != null && mp.getPlayer() != null && mp.getPlayer().isOnline()) {
        currentPlayers++;
      }
    }

    final int requiredPlayers = match.getArena().getType() * 2;

    switch (match.getPhase()) {
      case LOBBY:
        if (currentPlayers >= requiredPlayers) {
          List<MatchPlayer> playersToAssign = new ArrayList<>(currentPlayers);
          for (MatchPlayer mp : players) {
            if (mp != null && mp.getPlayer() != null && mp.getPlayer().isOnline()) {
              playersToAssign.add(mp);
            }
          }

          Team firstTeam = null;
          for (MatchPlayer mp : playersToAssign) {
            if (mp == null || mp.getPlayer() == null) {
              continue;
            }
            Team t = teamManager.getTeam(mp.getPlayer());
            if (t != null) {
              firstTeam = t;
              break;
            }
          }

          if (firstTeam != null) {
            List<Player> teamMembers = firstTeam.getMembers();
            List<MatchPlayer> teamMatchPlayers = new ArrayList<>();
            List<MatchPlayer> soloPlayers = new ArrayList<>();

            for (MatchPlayer mp : playersToAssign) {
              if (mp.getPlayer() != null && teamMembers.contains(mp.getPlayer())) {
                teamMatchPlayers.add(mp);
              } else {
                soloPlayers.add(mp);
              }
            }

            for (MatchPlayer teamPlayer : teamMatchPlayers) {
              teamPlayer.setTeamColor(TeamColor.RED);
            }
            Collections.shuffle(soloPlayers);

            long redTeamSize = teamMatchPlayers.size();
            for (MatchPlayer soloPlayer : soloPlayers) {
              if (redTeamSize < requiredPlayers / 2) {
                soloPlayer.setTeamColor(TeamColor.RED);
                redTeamSize++;
              } else {
                soloPlayer.setTeamColor(TeamColor.BLUE);
              }
            }

            teamManager.disbandTeam(firstTeam);
          } else {
            Collections.shuffle(playersToAssign);
            for (int i = 0; i < playersToAssign.size(); i++) {
              playersToAssign.get(i).setTeamColor(i < requiredPlayers / 2
                  ? TeamColor.RED
                  : TeamColor.BLUE);
            }
          }

          for (MatchPlayer mp : playersToAssign) {
            if (mp == null || mp.getPlayer() == null) {
              continue;
            }
            logger.send(mp.getPlayer(), STARTING);
          }

          match.setPhase(MatchPhase.STARTING);
          match.setCountdown(15);
          match.setTick(0);
          scoreboardManager.updateScoreboard(match);
        }
        break;

      case STARTING:
        if (shouldUpdateScoreboard(match)) {
          match.setCountdown(match.getCountdown() - 1);
          scoreboardManager.updateScoreboard(match);
          String matchType = match.getArena().getType() + "v" + match.getArena().getType();
          String matchId = String.valueOf(match.getArena().getId());

          String matchTitle = match.getCountdown() == 0
              ? MATCHES_LIST_MATCH.replace(matchType, matchId)
              : MATCHES_LIST_LOBBY.replace(matchType, matchId);

          for (MatchPlayer mp : players) {
            if (mp == null || mp.getPlayer() == null) {
              continue;
            }
            Player player = mp.getPlayer();
            player.playSound(player.getLocation(), Sound.NOTE_STICKS, 1, 1);
            if (match.getCountdown() != 0) {
              logger.sendActionBar(player, MATCH_STARTING_ACTIONBAR,
                  matchTitle,
                  MATCHES_LIST_STARTING.replace(String.valueOf(match.getCountdown()))
              );
            } else {
              logger.sendActionBar(player, MATCH_STARTING_ACTIONBAR,
                  matchTitle,
                  MATCH_STARTED_ACTIONBAR.toString()
              );
            }

            if (match.getCountdown() == 10) {
              if (mp.getTeamColor() == TeamColor.RED) {
                player.teleport(match.getArena().getRedSpawn());
              } else {
                player.teleport(match.getArena().getBlueSpawn());
              }
              giveArmor(player, mp.getTeamColor());
              if (player.getGameMode() != GameMode.SURVIVAL) {
                player.setGameMode(GameMode.SURVIVAL);
              }
              if (player.isFlying()) {
                player.setFlying(false);
                player.setAllowFlight(false);
              }
              logger.title(player, matchTitle, MATCH_PREPARING, 10, 50, 10);
            } else {
              if (match.getCountdown() == 5) {
                logger.title(player, MATCH_PREPARATION_TITLE, MATCH_PREPARATION_SUBTITLE, 10, 50,
                    10);
              } else {
                if (match.getCountdown() <= 0) {
                  logger.title(player, MATCH_STARTING_TITLE, MATCH_STARTING_SUBTITLE, 5, 30, 5);
                }
              }
            }
          }

          if (match.getCountdown() <= 0) {
            match.setPhase(MatchPhase.IN_PROGRESS);
            match.setStartTime(System.currentTimeMillis());
            startMatch(match);
          }
        }
        break;

      case IN_PROGRESS:
        if (shouldUpdateScoreboard(match)) {
          scoreboardManager.updateScoreboard(match);
        }
        handleGoalDetection(match);
        handleMatchTimer(match);
        handleCubeRespawn(match);

        if (currentPlayers >= requiredPlayers) {
          match.setTakePlaceNeeded(false);
          match.setLastTakePlaceAnnounceTick(0);
        }
        break;

      case CONTINUING:
        if (shouldUpdateScoreboard(match)) {
          match.setCountdown(match.getCountdown() - 1);
          scoreboardManager.updateScoreboard(match);

          for (MatchPlayer mp : players) {
            if (mp == null || mp.getPlayer() == null) {
              continue;
            }
            Player player = mp.getPlayer();
            player.setLevel(match.getCountdown());
            player.playSound(player.getLocation(), Sound.NOTE_STICKS, 1, 1);
          }

          if (match.getCountdown() <= 0) {
            match.setPhase(MatchPhase.IN_PROGRESS);
            for (MatchPlayer mp : players) {
              if (mp == null || mp.getPlayer() == null) {
                continue;
              }
              logger.send(mp.getPlayer(), MATCH_PROCEED);
            }
            startRound(match);
          }
        }
        break;

      case ENDED:
        if (currentPlayers >= requiredPlayers) {
          match.setTakePlaceNeeded(false);
          match.setLastTakePlaceAnnounceTick(0);
        }

        fcManager.getMatchManager().endMatch(match);
        break;
    }

    match.setTakePlaceNeeded(
        currentPlayers < requiredPlayers && !match.isTakePlaceNeeded()
            && match.getPhase() != MatchPhase.LOBBY);
    if (match.isTakePlaceNeeded()) {
      announceTakePlace(match);
    }
  }

  public void processQueues() {
    int[] queueOrder = {TWO_V_TWO, THREE_V_THREE, FOUR_V_FOUR, FIVE_V_FIVE};
    for (int matchType : queueOrder) {
      Queue<Player> queue = data.getPlayerQueues().get(matchType);
      if (queue == null || queue.isEmpty()) {
        continue;
      }

      ReentrantLock lock = data.getQueueLocks().get(matchType);
      if (lock == null) {
        continue;
      }

      if (!lock.tryLock()) {
        continue;
      }
      try {
        processSingleQueue(matchType, queue);
      } finally {
        lock.unlock();
      }
    }
  }

  private void processSingleQueue(int matchType, Queue<Player> queue) {
    while (true) {
      Player head = queue.peek();
      while (head != null && !head.isOnline()) {
        queue.poll();
        head = queue.peek();
      }
      if (head == null) {
        break;
      }

      if (fcManager.getMatchManager().getMatch(head).isPresent()) {
        queue.poll();
        continue;
      }

      Team team = teamManager.getTeam(head);
      List<Player> playerGroup;

      if (team != null) {
        Set<Player> snapshot = new HashSet<>(queue);
        List<Player> members = new ArrayList<>();
        for (Player member : team.getMembers()) {
          if (member != null && member.isOnline()) {
            members.add(member);
          }
        }

        if (!snapshot.containsAll(members)) {
          return;
        }

        playerGroup = new ArrayList<>();
        for (Player qp : queue) {
          if (members.contains(qp)) {
            playerGroup.add(qp);
          }
        }
        if (playerGroup.isEmpty()) {
          return;
        }
      } else {
        playerGroup = Collections.singletonList(head);
      }

      Match targetMatch = null;
      int maxPlayers = matchType * 2;
      int groupSize = playerGroup.size();

      for (Match match : data.getMatches()) {
        if (match.getArena().getType() == matchType
            && match.getPhase() == MatchPhase.LOBBY
            && (match.getPlayers().size() + groupSize <= maxPlayers)) {
          if (targetMatch == null
              || match.getPlayers().size() < targetMatch.getPlayers().size()) {
            targetMatch = match;
          }
        }
      }

      if (targetMatch == null) {
        targetMatch = createNewLobby(matchType);
      }
      if (targetMatch == null) {
        for (Player p : playerGroup) {
          logger.send(p, JOIN_NOARENA);
        }
        return;
      }

      if (targetMatch.getLobbyScoreboard() == null) {
        scoreboardManager.createLobbyScoreboard(targetMatch);
      }

      for (Player p : playerGroup) {
        queue.remove(p);
        targetMatch.getPlayers().add(new MatchPlayer(p, null));
        scoreboardManager.showLobbyScoreboard(targetMatch, p);
      }

      scoreboardManager.updateScoreboard(targetMatch);
    }
  }

  public void checkStats(String playerName, CommandSender asker) {
    PlayerData data = fcManager.getDataManager().get(playerName);
    if (data == null || !data.has("matches")) {
      logger.send(asker, STATS_NONE, playerName);
      return;
    }

    int matches = (int) data.get("matches");
    int wins = (int) data.get("wins");
    int ties = (int) data.get("ties");
    int bestWinStreak = (int) data.get("bestwinstreak");
    int losses = (int) data.get("losses");

    double winsPerMatch = (matches > 0)
        ? (double) wins / matches
        : 0;

    int goals = (int) data.get("goals");
    int assists = (int) data.get("assists");
    int ownGoals = (int) data.get("owngoals");
    double goalsPerMatch = (matches > 0)
        ? (double) goals / matches
        : 0;
    double multiplier = 1.0 - Math.pow(0.9, matches);
    double goalBonus = matches > 0
        ? (goals == matches
        ? 1.0
        : Math.min(1.0, 1 - multiplier * Math.pow(0.2, (double) goals / matches)))
        : 0.5;

    double addition = 0.0;
    if (matches > 0 && wins + ties > 0) {
      addition = 8.0 * (1.0 / ((100.0 * matches) / (wins + 0.5 * ties) / 100.0)) - 4.0;
    } else {
      if (matches > 0) {
        addition = -4.0;
      }
    }

    double skillLevel = Math.min(5.0 + goalBonus + addition * multiplier, 10.0);
    int rank = (int) (skillLevel * 2.0 - 0.5);
    String rang;

    switch (rank) {
      case 1:
        rang = "Nub";
        break;
      case 2:
        rang = "Luzer";
        break;
      case 3:
        rang = "Beba";
        break;
      case 4:
        rang = "Učenik";
        break;
      case 5:
        rang = "Loš";
        break;
      case 6:
        rang = ":(";
        break;
      case 7:
        rang = "Eh";
        break;
      case 8:
        rang = "Igrač";
        break;
      case 9:
        rang = "Ok";
        break;
      case 10:
        rang = "Prosečan";
        break;
      case 11:
        rang = "Dobar";
        break;
      case 12:
        rang = "Odličan";
        break;
      case 13:
        rang = "Kralj";
        break;
      case 14:
        rang = "Super";
        break;
      case 15:
        rang = "Pro";
        break;
      case 16:
        rang = "Maradona";
        break;
      case 17:
        rang = "Supermen";
        break;
      case 18:
        rang = "Bog";
        break;
      case 19:
        rang = "h4x0r";
        break;
      default:
        rang = "Nema";
        break;
    }

    logger.send(asker, STATS,
        playerName, String.valueOf(matches), String.valueOf(wins), String.valueOf(losses),
        String.valueOf(ties), String.format("%.2f", winsPerMatch), String.valueOf(bestWinStreak),
        String.valueOf(goals), String.format("%.2f", goalsPerMatch), String.valueOf(assists),
        String.format("%.2f", skillLevel), rang, String.valueOf(ownGoals)
    );
  }

  public boolean isInAnyQueue(Player player) {
    for (Queue<Player> q : data.getPlayerQueues().values()) {
      if (q != null && q.contains(player)) {
        return true;
      }
    }
    return false;
  }

  private synchronized Match createNewLobby(int matchType) {
    List<Arena> available = new ArrayList<>();
    for (Arena a : arenaManager.getArenas()) {
      if (a.getType() != matchType) {
        continue;
      }
      boolean inUse = false;
      for (Match m : data.getMatches()) {
        if (m.getArena().getId() == a.getId()) {
          inUse = true;
          break;
        }
      }
      if (!inUse) {
        available.add(a);
      }
    }

    if (available.isEmpty()) {
      return null;
    }

    Collections.shuffle(available);
    Match newMatch = new Match(available.get(0), new ArrayList<>());
    data.getMatches().add(newMatch);
    return newMatch;
  }

  private boolean shouldUpdateScoreboard(Match match) {
    return match.getTick() % SCOREBOARD_UPDATE_INTERVAL == 0;
  }

  private void announceTakePlace(Match match) {
    boolean firstAnnouncement = match.getLastTakePlaceAnnounceTick() == 0;
    if (firstAnnouncement ||
        match.getTick() - match.getLastTakePlaceAnnounceTick()
            >= TAKE_PLACE_ANNOUNCEMENT_INTERVAL_TICKS) {
      match.setLastTakePlaceAnnounceTick(match.getTick());

      long matchDuration = match.getArena().getType() == TWO_V_TWO
          ? 120
          : 300;
      long elapsedMillis = System.currentTimeMillis() - match.getStartTime();
      long remainingSeconds = matchDuration - TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);

      int matchType = match.getArena().getType();
      String matchIdString = String.valueOf(match.getArena().getId()), matchTypeString =
          matchType + "v" + matchType;
      boolean activeMatch = match.getPhase() == MatchPhase.IN_PROGRESS;

      String matchTitle = activeMatch
          ? "&a&l" + matchTypeString + " Meča #" + matchIdString
          : "&b&l" + matchTypeString + " Queue #" + matchIdString;

      String announcement = activeMatch
          ? TAKE_PLACE_ANNOUNCEMENT_MATCH.replace(
          matchTitle,
          RED.toString(), String.valueOf(match.getScoreRed()),
          String.valueOf(match.getScoreBlue()), BLUE.toString(),
          Utilities.formatTimePretty((int) remainingSeconds)
      )
          : TAKE_PLACE_ANNOUNCEMENT_LOBBY.replace(matchTitle);

      for (Player player : Bukkit.getOnlinePlayers()) {
        if (player == null) {
          continue;
        }
        if (fcManager.getMatchManager().getMatch(player).isEmpty()) {
          logger.send(player, announcement);
        }
      }
    }
  }

  /**
   * Represents the result of determining who scored and assisted. This is a traditional Java class
   * that holds immutable data about a scoring event.
   */
  public static class ScoringResult {

    @Getter
    private final MatchPlayer scorer;
    @Getter
    private final MatchPlayer assister;
    @Getter
    private final boolean ownGoal;
    @Getter
    private final TeamColor scoringTeam;

    public ScoringResult(MatchPlayer scorer, MatchPlayer assister,
        boolean ownGoal, TeamColor scoringTeam) {
      this.scorer = scorer;
      this.assister = assister;
      this.ownGoal = ownGoal;
      this.scoringTeam = scoringTeam;
    }

    /**
     * Determines if credits should be awarded for this goal. Credits are only awarded for regular
     * goals, not own goals.
     */
    public boolean shouldAwardCredits() {
      return !ownGoal && scorer != null;
    }
  }
}