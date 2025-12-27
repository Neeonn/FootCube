package io.github.divinerealms.footcube.matchmaking.logic;

import io.github.divinerealms.footcube.configs.Lang;
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
import io.github.divinerealms.footcube.matchmaking.util.MatchConstants;
import io.github.divinerealms.footcube.matchmaking.util.MatchUtils;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.SCOREBOARD_UPDATE_INTERVAL;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.TAKEPLACE_ANNOUNCEMENT_INTERVAL_TICKS;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.displayGoalMessage;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.isValidGoalMessage;

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

    int[] initialQueueOrder = {MatchConstants.TWO_V_TWO, MatchConstants.THREE_V_THREE, MatchConstants.FOUR_V_FOUR};
    for (int t : initialQueueOrder) data.getQueueLocks().put(t, new ReentrantLock());
  }

  public void startMatch(Match match) {
    if (match == null || match.getPlayers() == null) return;

    scoreboardManager.createMatchScoreboard(match);

    for (MatchPlayer matchPlayer : match.getPlayers()) {
      if (matchPlayer == null) continue;
      Player player = matchPlayer.getPlayer();
      if (player == null || !player.isOnline()) continue;

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

    ThreadLocalRandom random = ThreadLocalRandom.current();
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
    boolean ownGoal;

    Location goalLoc = (scoringTeam == TeamColor.RED) ? match.getArena().getBlueSpawn() : match.getArena().getRedSpawn();
    boolean shouldCount = match.getArena().getType() != MatchConstants.TWO_V_TWO;

    if (scorer != null) {
      MatchPlayer second = match.getSecondLastTouch();

      if (scorer.getTeamColor() != scoringTeam) {
        if (second != null && second.getTeamColor() == scoringTeam) {
          scorer = second;
          ownGoal = false;
        } else {
          ownGoal = true;
          if (shouldCount) scorer.incrementOwnGoals();
        }
      } else ownGoal = false;

      if (!ownGoal) {
        if (shouldCount) scorer.incrementGoals();
        Player scoringPlayer = scorer.getPlayer();

        if (scoringPlayer != null && scoringPlayer.isOnline()) {
          logger.send(scoringPlayer, Lang.MATCH_SCORE_CREDITS.replace(null));
          fcManager.getEconomy().depositPlayer(scoringPlayer, 10);

          if (scorer.getGoals() > 0 && scorer.getGoals() % 3 == 0) {
            logger.send(scoringPlayer, Lang.MATCH_SCORE_HATTRICK.replace(null));
            fcManager.getEconomy().depositPlayer(scoringPlayer, 100);
          }
        }

        if (second != null && second.getTeamColor() == scoringTeam && !scorer.equals(second)) {
          assister = second;
          if (shouldCount) assister.incrementAssists();
          Player assistingPlayer = assister.getPlayer();

          if (assistingPlayer != null && assistingPlayer.isOnline()) {
            logger.send(assistingPlayer, Lang.MATCH_ASSIST_CREDITS.replace(null));
            fcManager.getEconomy().depositPlayer(assistingPlayer, 5);
          }
        }
      }
    } else ownGoal = false;

    for (MatchPlayer matchPlayer : match.getPlayers()) {
      if (matchPlayer == null || matchPlayer.getPlayer() == null || !matchPlayer.getPlayer().isOnline()) continue;
      Player player = matchPlayer.getPlayer();

      PlayerSettings settings = fcManager.getPlayerSettings(player);
      if (settings != null && settings.isGoalSoundEnabled()) player.playSound(player.getLocation(), settings.getGoalSound(), 1, 1);
      player.playEffect(goalLoc, Effect.EXPLOSION_HUGE, null);

      double distanceToGoal = player.getLocation().distance(goalLoc);
      if (distanceToGoal <= 30) {
        Vector launchDir = player.getLocation().toVector().subtract(goalLoc.toVector()).normalize();
        player.setVelocity(launchDir.setY(0.5).multiply(1.5));
      }
    }

    UUID scorerUUID = (scorer != null && scorer.getPlayer() != null) ? scorer.getPlayer().getUniqueId() : null;
    UUID assisterUUID = (assister != null && assister.getPlayer() != null) ? assister.getPlayer().getUniqueId() : null;

    CompletableFuture<String> scorerPrefixFuture = scorerUUID != null
        ? utilities.getPrefixedName(scorerUUID, scorer.getPlayer().getName())
        : CompletableFuture.completedFuture(null);

    CompletableFuture<String> assisterPrefixFuture = assisterUUID != null
        ? utilities.getPrefixedName(assisterUUID, assister.getPlayer().getName())
        : CompletableFuture.completedFuture(null);

    MatchPlayer finalScorer = scorer;
    PlayerSettings scorerSettings = (finalScorer != null && finalScorer.getPlayer() != null)
        ? fcManager.getPlayerSettings(finalScorer.getPlayer())
        : null;
    String goalMessageStyle = (scorerSettings != null && isValidGoalMessage(scorerSettings.getGoalMessage()))
        ? scorerSettings.getGoalMessage()
        : "default";

    scorerPrefixFuture.thenCombine(assisterPrefixFuture, (scorerName, assisterName) -> new String[]{scorerName, assisterName})
        .thenAccept(names -> {
          String scorerName = names[0], assisterName = names[1];
          boolean isHatTrick = !ownGoal && finalScorer != null && finalScorer.getGoals() > 0 && finalScorer.getGoals() % 3 == 0;

          Bukkit.getScheduler().runTask(fcManager.getPlugin(), () -> {
            double distance = 0;
            if (finalScorer != null && finalScorer.getPlayer() != null) distance = finalScorer.getPlayer().getLocation().distance(goalLoc);

            final double finalDistance = distance;
            String teamColorText = scoringTeam == TeamColor.RED ? Lang.RED.replace(null) : Lang.BLUE.replace(null);
            String assistText = assisterName != null ? Lang.GM_ASSISTS_TEXT.replace(new String[]{assisterName}) : "";

            for (MatchPlayer matchPlayer : match.getPlayers()) {
              if (matchPlayer == null || matchPlayer.getPlayer() == null || !matchPlayer.getPlayer().isOnline()) continue;
              Player player = matchPlayer.getPlayer();

              boolean isViewerScorer = (matchPlayer.equals(finalScorer));

              displayGoalMessage(player, goalMessageStyle, ownGoal, isHatTrick, isViewerScorer, scorerName,
                  assistText, teamColorText, distance, match, logger, fcManager.getPlugin());

              String goalMessage = ownGoal
                  ? Lang.MATCH_SCORE_OWN_GOAL_ANNOUNCE.replace(new String[]{scorerName, teamColorText})
                  : Lang.MATCH_GOAL.replace(new String[]{
                  (isHatTrick ? Lang.MATCH_HATTRICK.replace(null) : Lang.MATCH_GOALLL.replace(null)),
                  scorerName,
                  teamColorText,
                  String.format("%.0f", finalDistance),
                  (assisterName != null ? Lang.MATCH_GOAL_ASSIST.replace(new String[]{assisterName}) : "")
              });

              logger.send(player, goalMessage);
              logger.send(player, Lang.MATCH_SCORE_STATS.replace(new String[]{
                  String.valueOf(match.getScoreRed()),
                  String.valueOf(match.getScoreBlue())
              }));
            }
          });
        });

    match.setPhase(MatchPhase.CONTINUING);
    match.setCountdown(5);
    match.setTick(0);

    scoreboardManager.updateScoreboard(match);
  }

  public void updateMatch(Match match) {
    match.setTick(match.getTick() + 1);

    List<MatchPlayer> players = match.getPlayers();
    int currentPlayers = 0;
    if (players == null) return;

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
          for (MatchPlayer mp : players) if (mp != null && mp.getPlayer() != null && mp.getPlayer().isOnline()) playersToAssign.add(mp);

          Team firstTeam = null;
          for (MatchPlayer mp : playersToAssign) {
            if (mp == null || mp.getPlayer() == null) continue;
            Team t = teamManager.getTeam(mp.getPlayer());
            if (t != null) { firstTeam = t; break; }
          }

          if (firstTeam != null) {
            List<Player> teamMembers = firstTeam.getMembers();
            List<MatchPlayer> teamMatchPlayers = new ArrayList<>();
            List<MatchPlayer> soloPlayers = new ArrayList<>();

            for (MatchPlayer mp : playersToAssign) {
              if (mp.getPlayer() != null && teamMembers.contains(mp.getPlayer())) teamMatchPlayers.add(mp);
              else soloPlayers.add(mp);
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

            teamManager.disbandTeam(firstTeam);
          } else {
            Collections.shuffle(playersToAssign);
            for (int i = 0; i < playersToAssign.size(); i++) {
              playersToAssign.get(i).setTeamColor(i < requiredPlayers / 2 ? TeamColor.RED : TeamColor.BLUE);
            }
          }

          for (MatchPlayer mp : playersToAssign) {
            if (mp == null || mp.getPlayer() == null) continue;
            logger.send(mp.getPlayer(), Lang.STARTING.replace(null));
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
              ? Lang.MATCHES_LIST_MATCH.replace(new String[]{matchType, matchId})
              : Lang.MATCHES_LIST_LOBBY.replace(new String[]{matchType, matchId});

          for (MatchPlayer mp : players) {
            if (mp == null || mp.getPlayer() == null) continue;
            Player player = mp.getPlayer();
            player.playSound(player.getLocation(), Sound.NOTE_STICKS, 1, 1);
            if (match.getCountdown() != 0) {
              logger.sendActionBar(player, Lang.MATCH_STARTING_ACTIONBAR.replace(new String[]{
                  matchTitle,
                  Lang.MATCHES_LIST_STARTING.replace(new String[]{
                      String.valueOf(match.getCountdown())
                  })
              }));
            } else {
              logger.sendActionBar(player, Lang.MATCH_STARTING_ACTIONBAR.replace(new String[]{
                  matchTitle,
                  Lang.MATCH_STARTED_ACTIONBAR.replace(null)
              }));
            }

            if (match.getCountdown() == 10) {
              if (mp.getTeamColor() == TeamColor.RED) player.teleport(match.getArena().getRedSpawn());
              else player.teleport(match.getArena().getBlueSpawn());
              MatchUtils.giveArmor(player, mp.getTeamColor());
              if (player.getGameMode() != GameMode.SURVIVAL) player.setGameMode(GameMode.SURVIVAL);
              if (player.isFlying()) { player.setFlying(false); player.setAllowFlight(false); }
              logger.title(player, matchTitle, Lang.MATCH_PREPARING.replace(null), 10, 50, 10);
            } else if (match.getCountdown() == 5) {
              logger.title(player, Lang.MATCH_PREPARATION_TITLE.replace(null),
                  Lang.MATCH_PREPARATION_SUBTITLE.replace(null), 10, 50, 10);
            } else if (match.getCountdown() <= 0) {
              logger.title(player, Lang.MATCH_STARTING_TITLE.replace(null),
                  Lang.MATCH_STARTING_SUBTITLE.replace(null), 5, 30, 5);
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
        if (shouldUpdateScoreboard(match)) scoreboardManager.updateScoreboard(match);
        handleGoalDetection(match);
        handleMatchTimer(match);
        handleCubeRespawn(match);

        if (currentPlayers < requiredPlayers && !match.isTakePlaceNeeded()) match.setTakePlaceNeeded(true);
        if (match.isTakePlaceNeeded()) sendTakePlaceAnnouncementIfDue(match, currentPlayers);
        break;

      case CONTINUING:
        if (shouldUpdateScoreboard(match)) {
          match.setCountdown(match.getCountdown() - 1);
          scoreboardManager.updateScoreboard(match);

          for (MatchPlayer mp : players) {
            if (mp == null || mp.getPlayer() == null) continue;
            Player player = mp.getPlayer();
            player.setLevel(match.getCountdown());
            player.playSound(player.getLocation(), Sound.NOTE_STICKS, 1, 1);
          }

          if (match.getCountdown() <= 0) {
            match.setPhase(MatchPhase.IN_PROGRESS);
            for (MatchPlayer mp : players) {
              if (mp == null || mp.getPlayer() == null) continue;
              logger.send(mp.getPlayer(), Lang.MATCH_PROCEED.replace(null));
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
  }

  public void processQueues() {
    int[] queueOrder = {MatchConstants.TWO_V_TWO, MatchConstants.THREE_V_THREE, MatchConstants.FOUR_V_FOUR, MatchConstants.FIVE_V_FIVE};
    for (int matchType : queueOrder) {
      Queue<Player> queue = data.getPlayerQueues().get(matchType);
      if (queue == null || queue.isEmpty()) continue;

      ReentrantLock lock = data.getQueueLocks().get(matchType);
      if (lock == null) continue;

      if (!lock.tryLock()) continue;
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
      while (head != null && !head.isOnline()) { queue.poll(); head = queue.peek(); }
      if (head == null) break;

      if (fcManager.getMatchManager().getMatch(head).isPresent()) { queue.poll(); continue; }

      Team team = teamManager.getTeam(head);
      List<Player> playerGroup;

      if (team != null) {
        Set<Player> snapshot = new HashSet<>(queue);
        List<Player> members = new ArrayList<>();
        for (Player member : team.getMembers()) if (member != null && member.isOnline()) members.add(member);

        if (!snapshot.containsAll(members)) return;

        playerGroup = new ArrayList<>();
        for (Player qp : queue) if (members.contains(qp)) playerGroup.add(qp);
        if (playerGroup.isEmpty()) return;
      } else playerGroup = Collections.singletonList(head);

      Match targetMatch = null;
      int maxPlayers = matchType * 2;
      int groupSize = playerGroup.size();

      for (Match match : data.getMatches()) {
        if (match.getArena().getType() == matchType && match.getPhase() == MatchPhase.LOBBY && (match.getPlayers().size() + groupSize <= maxPlayers)) {
          if (targetMatch == null || match.getPlayers().size() < targetMatch.getPlayers().size()) targetMatch = match;
        }
      }

      if (targetMatch == null) targetMatch = createNewLobby(matchType);
      if (targetMatch == null) { for (Player p : playerGroup) logger.send(p, Lang.JOIN_NOARENA.replace(null)); return; }

      if (targetMatch.getLobbyScoreboard() == null) scoreboardManager.createLobbyScoreboard(targetMatch);

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
    if (data == null || !data.has("matches")) { logger.send(asker, Lang.STATS_NONE.replace(new String[]{playerName})); return; }

    int matches = (int) data.get("matches");
    int wins = (int) data.get("wins");
    int ties = (int) data.get("ties");
    int bestWinStreak = (int) data.get("bestwinstreak");
    int losses = (int) data.get("losses");

    double winsPerMatch = (matches > 0) ? (double) wins / matches : 0;

    int goals = (int) data.get("goals");
    int assists = (int) data.get("assists");
    int ownGoals = (int) data.get("owngoals");
    double goalsPerMatch = (matches > 0) ? (double) goals / matches : 0;
    double multiplier = 1.0 - Math.pow(0.9, matches);
    double goalBonus = matches > 0
        ? (goals == matches ? 1.0 : Math.min(1.0, 1 - multiplier * Math.pow(0.2, (double) goals / matches)))
        : 0.5;

    double addition = 0.0;
    if (matches > 0 && wins + ties > 0) addition = 8.0 * (1.0 / ((100.0 * matches) / (wins + 0.5 * ties) / 100.0)) - 4.0;
    else if (matches > 0)addition = -4.0;

    double skillLevel = Math.min(5.0 + goalBonus + addition * multiplier, 10.0);
    int rank = (int) (skillLevel * 2.0 - 0.5);
    String rang;

    switch (rank) {
      case 1: rang = "Nub"; break;
      case 2: rang = "Luzer"; break;
      case 3: rang = "Beba"; break;
      case 4: rang = "Učenik"; break;
      case 5: rang = "Loš"; break;
      case 6: rang = ":("; break;
      case 7: rang = "Eh"; break;
      case 8: rang = "Igrač"; break;
      case 9: rang = "Ok"; break;
      case 10: rang = "Prosečan"; break;
      case 11: rang = "Dobar"; break;
      case 12: rang = "Odličan"; break;
      case 13: rang = "Kralj"; break;
      case 14: rang = "Super"; break;
      case 15: rang = "Pro"; break;
      case 16: rang = "Maradona"; break;
      case 17: rang = "Supermen"; break;
      case 18: rang = "Bog"; break;
      case 19: rang = "h4x0r"; break;
      default: rang = "Nema"; break;
    }

    logger.send(asker, Lang.STATS.replace(new String[]{
        playerName, String.valueOf(matches), String.valueOf(wins), String.valueOf(losses),
        String.valueOf(ties), String.format("%.2f", winsPerMatch), String.valueOf(bestWinStreak),
        String.valueOf(goals), String.format("%.2f", goalsPerMatch), String.valueOf(assists),
        String.format("%.2f", skillLevel), rang, String.valueOf(ownGoals)
    }));
  }

  public boolean isInAnyQueue(Player player) {
    for (Queue<Player> q : data.getPlayerQueues().values()) if (q != null && q.contains(player)) return true;
    return false;
  }

  private synchronized Match createNewLobby(int matchType) {
    List<Arena> available = new ArrayList<>();
    for (Arena a : arenaManager.getArenas()) {
      if (a.getType() != matchType) continue;
      boolean inUse = false;
      for (Match m : data.getMatches()) if (m.getArena().getId() == a.getId()) { inUse = true; break; }
      if (!inUse) available.add(a);
    }

    if (available.isEmpty()) return null;

    Collections.shuffle(available);
    Match newMatch = new Match(available.get(0), new ArrayList<>());
    data.getMatches().add(newMatch);
    return newMatch;
  }

  private boolean shouldUpdateScoreboard(Match match) {
    return match.getTick() % SCOREBOARD_UPDATE_INTERVAL == 0;
  }

  private void sendTakePlaceAnnouncementIfDue(Match match, int currentPlayers) {
    boolean firstAnnouncement = match.getLastTakePlaceAnnounceTick() == 0;
    if (firstAnnouncement || match.getTick() - match.getLastTakePlaceAnnounceTick() >= TAKEPLACE_ANNOUNCEMENT_INTERVAL_TICKS) {
      match.setLastTakePlaceAnnounceTick(match.getTick());

      int matchType = match.getArena().getType();
      int remainingSeconds = match.getCountdown();
      String announcement = match.getPhase() == MatchPhase.CONTINUING
          ? Lang.TAKEPLACE_ANNOUNCEMENT_2.replace(new String[]{String.valueOf(matchType), String.valueOf(currentPlayers), Utilities.formatTimePretty(remainingSeconds)})
          : Lang.TAKEPLACE_ANNOUNCEMENT.replace(new String[]{String.valueOf(matchType)});
      String wholeMessage = announcement + System.lineSeparator() + Lang.TAKEPLACE_ANNOUNCEMENT_3.replace(null);

      for (Player player : Bukkit.getOnlinePlayers()) {
        if (player == null) continue;
        if (fcManager.getMatchManager().getMatch(player).isEmpty()) logger.send(player, wholeMessage);
      }
    }
  }
}