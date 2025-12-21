package io.github.divinerealms.footcube.matchmaking;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.arena.ArenaManager;
import io.github.divinerealms.footcube.matchmaking.ban.BanManager;
import io.github.divinerealms.footcube.matchmaking.logic.MatchData;
import io.github.divinerealms.footcube.matchmaking.logic.MatchSystem;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
import io.github.divinerealms.footcube.matchmaking.scoreboard.ScoreManager;
import io.github.divinerealms.footcube.matchmaking.team.Team;
import io.github.divinerealms.footcube.matchmaking.team.TeamManager;
import io.github.divinerealms.footcube.matchmaking.util.MatchConstants;
import io.github.divinerealms.footcube.matchmaking.util.MatchUtils;
import io.github.divinerealms.footcube.utils.Logger;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Getter
public class MatchManager {
  private final FCManager fcManager;
  private final ArenaManager arenaManager;
  private final ScoreManager scoreboardManager;
  private final TeamManager teamManager;
  private final MatchData data;
  private final MatchSystem system;
  private final BanManager banManager;
  private final Utilities utilities;
  private final Logger logger;

  public MatchManager(FCManager fcManager) {
    this.fcManager = fcManager;
    this.arenaManager = fcManager.getArenaManager();
    this.scoreboardManager = fcManager.getScoreboardManager();
    this.data = fcManager.getMatchData();
    this.teamManager = fcManager.getTeamManager();
    this.system = fcManager.getMatchSystem();
    this.banManager = fcManager.getBanManager();
    this.utilities = fcManager.getUtilities();
    this.logger = fcManager.getLogger();
  }

  public synchronized void joinQueue(Player player, int matchType) {
    if (banManager.isBanned(player)) return;

    Team team = teamManager.getTeam(player);
    List<Player> playersToQueue = (team != null) ? new ArrayList<>(team.getMembers()) : Collections.singletonList(player);
    String matchTypeString = matchType + "v" + matchType;

    for (Player p : playersToQueue) {
      if (p == null || !p.isOnline()) continue;
      if (getMatch(p).isPresent() || system.isInAnyQueue(p)) {
        logger.send(player, Lang.JOIN_ALREADYINGAME.replace(null));
        return;
      }
    }

    Match existingLobby = null;
    for (Match match : data.getMatches()) {
      if (match.getPhase() == MatchPhase.LOBBY && match.getArena() != null && match.getArena().getType() == matchType) {
        int nullCount = 0;
        for (MatchPlayer mp : match.getPlayers()) if (mp == null) nullCount++;
        if (nullCount >= playersToQueue.size()) { existingLobby = match; break; }
      }
    }

    for (Player p : playersToQueue) {
      if (p == null || !p.isOnline()) continue;
      for (Queue<Player> otherQueue : data.getPlayerQueues().values()) otherQueue.remove(p);
      logger.send(p, Lang.JOIN_SUCCESS.replace(new String[]{matchTypeString}));
      p.setLevel(0);
    }

    if (existingLobby != null) {
      for (Player p : playersToQueue) {
        for (int i = 0; i < existingLobby.getPlayers().size(); i++) {
          if (existingLobby.getPlayers().get(i) == null) {
            existingLobby.getPlayers().set(i, new MatchPlayer(p, null));
            scoreboardManager.showLobbyScoreboard(existingLobby, p);
            break;
          }
        }
      }
      scoreboardManager.updateScoreboard(existingLobby);
    } else {
      Queue<Player> queue = data.getPlayerQueues().computeIfAbsent(matchType, k -> new ConcurrentLinkedQueue<>());
      for (Player p : playersToQueue) { if (p != null && p.isOnline()) queue.add(p); }
    }

    system.processQueues();
  }

  public synchronized void leaveQueue(Player player, int matchType) {
    Queue<Player> queue = data.getPlayerQueues().get(matchType);
    if (queue != null) {
      queue.remove(player);
      player.setLevel(0);
    }
    leaveMatch(player);
  }

  public void forceStartMatch(Player player) {
    Optional<Match> matchOpt = getMatch(player);
    if (!matchOpt.isPresent()) { logger.send(player, Lang.MATCHES_LIST_NO_MATCHES.replace(null)); return; }

    Match targetMatch = matchOpt.get();
    if (targetMatch.getPhase() != MatchPhase.LOBBY) { logger.send(player, Lang.MATCH_ALREADY_STARTED.replace(null)); return; }

    List<MatchPlayer> allPlayers = targetMatch.getPlayers();
    if (allPlayers == null) { logger.send(player, Lang.MATCHES_LIST_NO_MATCHES.replace(null)); return; }

    List<MatchPlayer> playersInLobby = new ArrayList<>();
    for (MatchPlayer mp : allPlayers) {
      if (mp != null) playersInLobby.add(mp);
    }

    if (playersInLobby.size() == 2) {
      MatchPlayer p1 = playersInLobby.get(0), p2 = playersInLobby.get(1);

      p1.setTeamColor(TeamColor.RED);
      p2.setTeamColor(TeamColor.BLUE);

      targetMatch.getPlayers().clear();
      targetMatch.getPlayers().addAll(Arrays.asList(p1, p2));

      targetMatch.setPhase(MatchPhase.STARTING);
      targetMatch.setCountdown(12);

      logger.send(player, Lang.MATCHMAN_FORCE_START.replace(new String[]{"1v1"}));
      scoreboardManager.updateScoreboard(targetMatch);
      return;
    }

    int arenaSize = (targetMatch.getArena() != null) ? targetMatch.getArena().getType() : 1;
    int requiredPlayers = arenaSize * 2;

    List<MatchPlayer> finalPlayerLineup = new ArrayList<>();
    List<MatchPlayer> soloPlayers = new ArrayList<>();

    Team foundTeam = null;
    for (MatchPlayer mp : playersInLobby) {
      if (mp == null || mp.getPlayer() == null) continue;
      Team t = teamManager.getTeam(mp.getPlayer());
      if (t != null) { foundTeam = t; break; }
    }

    if (foundTeam != null) {
      List<Player> teamMembers = foundTeam.getMembers();
      for (MatchPlayer mp : playersInLobby) {
        if (mp == null) continue;
        Player p = mp.getPlayer();
        if (p != null && teamMembers != null && teamMembers.contains(p)) finalPlayerLineup.add(mp);
        else soloPlayers.add(mp);
      }
      teamManager.disbandTeam(foundTeam);
    } else soloPlayers.addAll(playersInLobby);

    Collections.shuffle(soloPlayers);
    finalPlayerLineup.addAll(soloPlayers);

    targetMatch.getPlayers().clear();
    targetMatch.getPlayers().addAll(finalPlayerLineup);
    while (targetMatch.getPlayers().size() < requiredPlayers) targetMatch.getPlayers().add(null);

    for (int i = 0; i < requiredPlayers; i++) {
      MatchPlayer matchPlayer = targetMatch.getPlayers().get(i);
      if (matchPlayer != null) matchPlayer.setTeamColor(i < arenaSize ? TeamColor.RED : TeamColor.BLUE);
    }

    targetMatch.setPhase(MatchPhase.STARTING);
    targetMatch.setCountdown(12);

    logger.send(player, Lang.MATCHMAN_FORCE_START.replace(new String[]{targetMatch.getArena().getType() + "v" + targetMatch.getArena().getType()}));
    scoreboardManager.updateScoreboard(targetMatch);
  }

  public Optional<Match> getMatch(Player player) {
    if (player == null || data.getMatches() == null) return Optional.empty();

    for (Match match : data.getMatches()) {
      if (match == null) continue;
      List<MatchPlayer> matchPlayers = match.getPlayers();
      if (matchPlayers == null) continue;

      for (MatchPlayer mp : matchPlayers) {
        if (mp == null) continue;
        Player p = mp.getPlayer();
        if (p != null && p.equals(player)) return Optional.of(match);
      }
    }

    return Optional.empty();
  }

  public void leaveMatch(Player player) {
    getMatch(player).ifPresent(match -> {
      if (match.getPlayers() == null) {
        if (match.getPhase() != MatchPhase.LOBBY) {
          Location lobby = (Location) fcManager.getConfigManager().getConfig("config.yml").get("lobby");
          if (lobby != null) player.teleport(lobby);
          MatchUtils.clearPlayer(player);
        }
        scoreboardManager.removeScoreboard(player);
        return;
      }

      int playerIndex = -1;
      for (int i = 0; i < match.getPlayers().size(); i++) {
        MatchPlayer matchPlayer = match.getPlayers().get(i);
        if (matchPlayer != null && matchPlayer.getPlayer() != null && matchPlayer.getPlayer().equals(player)) {
          playerIndex = i;
          break;
        }
      }

      if (playerIndex != -1) match.getPlayers().set(playerIndex, null);

      boolean allNull = true;
      for (MatchPlayer mp : match.getPlayers()) {
        if (mp != null) { allNull = false; break; }
      }

      if (allNull) endMatch(match);
      else {
        if (!data.getOpenMatches().contains(match)) data.getOpenMatches().add(match);
        scoreboardManager.updateScoreboard(match);
      }

      if (match.getPhase() != MatchPhase.LOBBY) {
        Location lobby = (Location) fcManager.getConfigManager().getConfig("config.yml").get("lobby");
        if (lobby != null) player.teleport(lobby);
        MatchUtils.clearPlayer(player);
      }

      match.setTakePlaceNeeded(true);
      match.setLastTakePlaceAnnounceTick(0);
      scoreboardManager.removeScoreboard(player);
    });
  }

  public void takePlace(Player player, int matchId) {
    Optional<Match> matchOpt = Optional.empty();
    List<Match> openMatches = data.getOpenMatches();
    if (openMatches != null) {
      for (Match m : openMatches) {
        if (m == null || m.getArena() == null) continue;
        if (m.getArena().getId() == matchId) { matchOpt = Optional.of(m); break; }
      }
    }

    if (!matchOpt.isPresent()) { logger.send(player, Lang.TAKEPLACE_INVALID_ID.replace(new String[]{String.valueOf(matchId)})); return; }

    Match match = matchOpt.get();
    long redTeamCount = 0, blueTeamCount = 0;
    List<MatchPlayer> players = match.getPlayers();
    if (players != null) {
      for (MatchPlayer mp : players) {
        if (mp == null) continue;
        TeamColor tc = mp.getTeamColor();
        if (tc == TeamColor.RED) redTeamCount++;
        else if (tc == TeamColor.BLUE) blueTeamCount++;
      }
    }

    TeamColor teamToJoin = (redTeamCount <= blueTeamCount) ? TeamColor.RED : TeamColor.BLUE;
    int openSlotIndex = -1;
    if (players != null) {
      for (int i = 0; i < players.size(); i++) {
        if (players.get(i) == null) { openSlotIndex = i; break; }
      }
    }

    if (openSlotIndex != -1) {
      match.getPlayers().set(openSlotIndex, new MatchPlayer(player, teamToJoin));

      if (teamToJoin == TeamColor.RED) player.teleport(match.getArena().getRedSpawn());
      else player.teleport(match.getArena().getBlueSpawn());

      MatchUtils.giveArmor(player, teamToJoin);

      if (match.getPhase() == MatchPhase.LOBBY || match.getPhase() == MatchPhase.STARTING) scoreboardManager.showLobbyScoreboard(match, player);
      else if (match.getPhase() != MatchPhase.ENDED) scoreboardManager.showMatchScoreboard(match, player);
      scoreboardManager.updateScoreboard(match);

      boolean hasNull = false;
      for (MatchPlayer mp : match.getPlayers()) { if (mp == null) { hasNull = true; break; } }
      if (!hasNull) data.getOpenMatches().remove(match);

      match.setTakePlaceNeeded(false);
      match.setLastTakePlaceAnnounceTick(0);
      logger.send(player, Lang.TAKEPLACE_SUCCESS.replace(new String[]{String.valueOf(match.getArena().getId())}));
    } else logger.send(player, Lang.TAKEPLACE_FULL.replace(new String[]{String.valueOf(matchId)}));
  }

  public void endMatch(Match match) {
    TeamColor winner = null;
    if (match.getScoreRed() > match.getScoreBlue()) winner = TeamColor.RED;
    else if (match.getScoreBlue() > match.getScoreRed()) winner = TeamColor.BLUE;
    String winningTeam = winner == TeamColor.RED ? Lang.RED.replace(null) : Lang.BLUE.replace(null);

    for (MatchPlayer matchPlayer : match.getPlayers()) {
      if (matchPlayer == null) continue;
      Player player = matchPlayer.getPlayer();
      if (player == null || !player.isOnline()) continue;

      if (match.getPhase() == MatchPhase.ENDED) {
        PlayerData data = fcManager.getDataManager().get(player);
        if (winner == null) {
          if (match.getArena().getType() != MatchConstants.TWO_V_TWO) {
            data.add("ties");
            data.set("winstreak", 0);
          }

          fcManager.getEconomy().depositPlayer(player, 5);
          logger.send(player, Lang.MATCH_TIED.replace(null));
          logger.send(player, Lang.MATCH_TIED_CREDITS.replace(null));
        } else if (matchPlayer.getTeamColor() == winner) {
          if (match.getArena().getType() != MatchConstants.TWO_V_TWO) {
            data.add("wins");
            data.add("winstreak");

            if ((int) data.get("winstreak") > (int) data.get("bestwinstreak"))
              data.set("bestwinstreak", data.get("winstreak"));

            if ((int) data.get("winstreak") > 0 && (int) data.get("winstreak") % 5 == 0) {
              fcManager.getEconomy().depositPlayer(player, 100);
              logger.send(player, Lang.MATCH_WINSTREAK_CREDITS.replace(new String[]{String.valueOf(data.get("winstreak"))}));
            }
          }

          fcManager.getEconomy().depositPlayer(player, 15);
          logger.send(player, Lang.MATCH_TIMES_UP.replace(new String[]{winningTeam}));
          logger.send(player, Lang.MATCH_WIN_CREDITS.replace(null));
        } else {
          if (match.getArena().getType() != MatchConstants.TWO_V_TWO) {
            data.set("winstreak", 0);
            data.add("losses");
          }
          logger.send(player, Lang.MATCH_TIMES_UP.replace(new String[]{winningTeam}));
        }
      }

      if (match.getPhase() != MatchPhase.LOBBY) {
        Location lobby = (Location) fcManager.getConfigManager().getConfig("config.yml").get("lobby");
        if (lobby != null) player.teleport(lobby);
        MatchUtils.clearPlayer(player);
      }

      scoreboardManager.removeScoreboard(player);
      scoreboardManager.unregisterScoreboard(match.getMatchScoreboard());
      match.setMatchScoreboard(null);
    }

    if (match.getCube() != null) match.getCube().setHealth(0);
    match.setPhase(MatchPhase.ENDED);
    data.getMatches().remove(match);
    data.getOpenMatches().remove(match);
  }

  public void update() {
    List<Match> matches = data.getMatches();
    if (matches == null || matches.isEmpty()) {
      try {
        system.processQueues();
      } catch (Exception e) {
        Bukkit.getLogger().severe("Error processing queues: " + e.getMessage());
        e.printStackTrace();
      }
      return;
    }

    List<Match> snapshot = new ArrayList<>(matches);
    for (Match match : snapshot) {
      if (match == null) continue;
      try {
        system.updateMatch(match);
      } catch (Exception e) {
        String arenaId = (match.getArena() != null) ? String.valueOf(match.getArena().getId()) : "unknown";
        Bukkit.getLogger().severe("Error updating match (arena=" + arenaId + "): " + e.getMessage());
        e.printStackTrace();
      }
    }

    try {
      system.processQueues();
    } catch (Exception e) {
      Bukkit.getLogger().severe("Error processing queues: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public void kick(Player player) {
    Optional<Match> opt = getMatch(player);
    if (!opt.isPresent()) return;
    Match match = opt.get();
    if (match.getPlayers() == null) return;

    for (MatchPlayer mp : match.getPlayers()) {
      if (mp == null) continue;
      Player p = mp.getPlayer();
      if (p == null) continue;
      if (p.equals(player)) {
        if (match.getLastTouch() != mp) {
          match.setSecondLastTouch(match.getLastTouch());
          match.setLastTouch(mp);
        }
        break;
      }
    }
  }

  public int countActiveLobbies(int matchType) {
    int count = 0;
    if (data.getMatches() == null) return 0;
    for (Match match : data.getMatches()) {
      if (match == null) continue;
      if (match.getArena() == null) continue;
      int type = match.getArena().getType();
      if (type == matchType) {
        MatchPhase phase = match.getPhase();
        if (phase != MatchPhase.LOBBY && phase != MatchPhase.ENDED) count++;
      }
    }
    return count;
  }

  public int countPlayersInMatches(int matchType) {
    int total = 0;
    if (data.getMatches() == null) return 0;
    for (Match match : data.getMatches()) {
      if (match == null || match.getArena() == null) continue;
      if (match.getArena().getType() != matchType) continue;
      List<MatchPlayer> players = match.getPlayers();
      total += (players == null) ? 0 : players.size();
    }
    return total;
  }

  public int countWaitingPlayers(int matchType) {
    return data.getPlayerQueues().getOrDefault(matchType, new ConcurrentLinkedQueue<>()).size();
  }

  public String listPlayersInMatches(int matchType) {
    StringBuilder stringBuilder = new StringBuilder();
    if (data.getMatches() == null) return "";
    boolean first = true;
    for (Match match : data.getMatches()) {
      if (match == null || match.getArena() == null) continue;
      if (match.getArena().getType() != matchType) continue;
      List<MatchPlayer> players = match.getPlayers();
      if (players == null) continue;
      for (MatchPlayer matchPlayer : players) {
        if (matchPlayer == null) continue;
        Player player = matchPlayer.getPlayer();
        if (player == null) continue;
        if (!first) stringBuilder.append(", ");
        stringBuilder.append(player.getName());
        first = false;
      }
    }
    return stringBuilder.toString();
  }

  public void forceLeaveAllPlayers() {
    Map<Integer, Queue<Player>> queues = data.getPlayerQueues();
    if (queues != null) {
      for (Queue<Player> q : queues.values()) {
        if (q != null) q.clear();
      }
    }

    List<Match> matches = data.getMatches();
    if (matches != null) {
      List<Match> snapshot = new ArrayList<>(matches);
      for (Match m : snapshot) {
        if (m == null) continue;
        endMatch(m);
      }
    }
  }

  public void clearLobbiesAndQueues() {
    Map<Integer, Queue<Player>> queues = data.getPlayerQueues();
    if (queues != null) {
      for (Queue<Player> q : queues.values()) {
        if (q != null) q.clear();
      }
    }

    List<Match> matches = data.getMatches();
    if (matches == null) return;
    List<Match> snapshot = new ArrayList<>(matches);

    for (Match match : snapshot) {
      if (match == null) continue;
      MatchPhase phase = match.getPhase();
      if (phase != MatchPhase.LOBBY && phase != MatchPhase.STARTING) continue;

      List<MatchPlayer> players = match.getPlayers();
      if (players != null) {
        for (MatchPlayer mp : new ArrayList<>(players)) {
          if (mp == null) continue;
          Player p = mp.getPlayer();
          if (p == null) continue;
          MatchUtils.clearPlayer(p);
          scoreboardManager.removeScoreboard(p);
          logger.send(p, Lang.MATCHMAN_FORCE_END.replace(new String[]{match.getArena().getType() + "v" + match.getArena().getType()}));
        }
      }

      data.getMatches().remove(match);
    }
  }

  public void recreateScoreboards() {
    if (scoreboardManager == null) return;
    List<Match> matches = data.getMatches();
    if (matches == null) return;

    for (Match match : new ArrayList<>(matches)) {
      if (match == null) continue;
      if (match.getPhase() == MatchPhase.ENDED) continue;
      if (match.getPhase() == MatchPhase.LOBBY || match.getPhase() == MatchPhase.STARTING) {
        scoreboardManager.createLobbyScoreboard(match);
      } else {
        scoreboardManager.createMatchScoreboard(match);
      }
    }
  }

  public void teamChat(Player sender, String message) {
    Optional<Match> matchOpt = getMatch(sender);
    if (!matchOpt.isPresent()) { logger.send(sender, Lang.LEAVE_NOT_INGAME.replace(null)); return; }

    Match match = matchOpt.get();
    MatchPlayer senderMP = null;
    List<MatchPlayer> matchPlayers = match.getPlayers();
    if (matchPlayers != null) {
      for (MatchPlayer mp : matchPlayers) {
        if (mp == null) continue;
        Player p = mp.getPlayer();
        if (p != null && p.equals(sender)) { senderMP = mp; break; }
      }
    }
    if (senderMP == null) return;

    TeamColor teamColor = senderMP.getTeamColor();
    if (teamColor == null) return;

    UUID uuid = sender.getUniqueId();
    String playerName = sender.getName();

    utilities.getPrefixedName(uuid, playerName).thenAcceptAsync(prefixedName ->
        Bukkit.getScheduler().runTask(fcManager.getPlugin(), () -> {
          String formattedMessage = (teamColor == TeamColor.RED
              ? Lang.TEAMCHAT_RED.replace(new String[]{prefixedName})
              : Lang.TEAMCHAT_BLUE.replace(new String[]{prefixedName})) + message;

          List<MatchPlayer> players = match.getPlayers();
          if (players == null) return;
          for (MatchPlayer matchPlayer : players) {
            if (matchPlayer == null) continue;
            Player player = matchPlayer.getPlayer();
            if (player == null || !player.isOnline()) continue;
            if (matchPlayer.getTeamColor() == teamColor) logger.send(player, formattedMessage);
          }
        })
    );
  }
}
