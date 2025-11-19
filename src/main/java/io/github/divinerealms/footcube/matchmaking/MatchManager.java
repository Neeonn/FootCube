package io.github.divinerealms.footcube.matchmaking;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.arena.ArenaManager;
import io.github.divinerealms.footcube.matchmaking.ban.BanManager;
import io.github.divinerealms.footcube.matchmaking.logic.MatchData;
import io.github.divinerealms.footcube.matchmaking.logic.MatchSystem;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
import io.github.divinerealms.footcube.matchmaking.scoreboard.ScoreManager;
import io.github.divinerealms.footcube.matchmaking.team.Team;
import io.github.divinerealms.footcube.matchmaking.team.TeamManager;
import io.github.divinerealms.footcube.matchmaking.util.MatchmakingUtils;
import io.github.divinerealms.footcube.utils.Logger;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Getter
public class MatchManager {
  private final FCManager fcManager;
  private final ArenaManager arenaManager;
  private final ScoreManager scoreboardManager;
  private final TeamManager teamManager;
  private final MatchData data;
  private final MatchSystem system;
  private final BanManager banManager;
  private final Logger logger;

  public MatchManager(FCManager fcManager) {
    this.fcManager = fcManager;
    this.arenaManager = fcManager.getArenaManager();
    this.scoreboardManager = fcManager.getScoreboardManager();
    this.data = fcManager.getMatchData();
    this.teamManager = fcManager.getTeamManager();
    this.system = fcManager.getMatchSystem();
    this.banManager = fcManager.getBanManager();
    this.logger = fcManager.getLogger();
  }

  public void joinQueue(Player player, int matchType) {
    if (banManager.isBanned(player)) return;

    Team team = teamManager.getTeam(player);
    List<Player> playersToQueue = (team != null) ? team.getMembers() : Collections.singletonList(player);

    for (Player p : playersToQueue) {
      if (!data.getPlayerQueues().containsKey(matchType)) { logger.send(p, Lang.JOIN_INVALIDTYPE.replace(new String[]{String.valueOf(matchType), ""})); return; }
      if (getMatch(p).isPresent() || data.getPlayerQueues().get(matchType).contains(p)) { logger.send(p, Lang.JOIN_ALREADYINGAME.replace(null)); return; }
    }

    Queue<Player> queue = data.getPlayerQueues().get(matchType);
    for (Player p : playersToQueue) {
      queue.add(p);
      logger.send(p, Lang.WELCOME.replace(null));
      p.setLevel(0);
    }
  }

  public void leaveQueue(Player player, int matchType) {
    if (data.getPlayerQueues().containsKey(matchType)) {
      data.getPlayerQueues().get(matchType).remove(player);
      player.setLevel(0);
    }
  }

  public void forceStartMatch(Player player) {
    Optional<Match> matchOpt = getMatch(player);
    if (!matchOpt.isPresent()) { logger.send(player, Lang.MATCHES_LIST_NO_MATCHES.replace(null)); return; }

    Match targetMatch = matchOpt.get();
    if (targetMatch.getPhase() != MatchPhase.LOBBY) { logger.send(player, Lang.MATCH_ALREADY_STARTED.replace(null)); return; }

    List<MatchPlayer> playersInLobby = targetMatch.getPlayers().stream().filter(Objects::nonNull).collect(Collectors.toList());
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

    int arenaSize = targetMatch.getArena().getType();
    int requiredPlayers = arenaSize * 2;

    List<MatchPlayer> finalPlayerLineup = new ArrayList<>();
    List<MatchPlayer> soloPlayers = new ArrayList<>();

    Optional<Team> teamOpt = playersInLobby.stream()
        .map(mp -> teamManager.getTeam(mp.getPlayer()))
        .filter(Objects::nonNull)
        .findFirst();

    if (teamOpt.isPresent()) {
      Team team = teamOpt.get();
      List<Player> teamMembers = team.getMembers();

      for (MatchPlayer mp : playersInLobby) {
        if (teamMembers.contains(mp.getPlayer())) finalPlayerLineup.add(mp);
        else soloPlayers.add(mp);
      }

      teamManager.disbandTeam(team);
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

    return data.getMatches().stream()
        .filter(Objects::nonNull)
        .filter(match -> match.getPlayers() != null &&
            match.getPlayers().stream()
                .filter(Objects::nonNull)
                .anyMatch(p -> p.getPlayer() != null && p.getPlayer().equals(player)))
        .findFirst();
  }

  public void leaveMatch(Player player) {
    getMatch(player).ifPresent(match -> {
      int playerIndex = -1;
      for (int i = 0; i < match.getPlayers().size(); i++) {
        MatchPlayer matchPlayer = match.getPlayers().get(i);
        if (matchPlayer != null && matchPlayer.getPlayer() != null && matchPlayer.getPlayer().equals(player)) {
          playerIndex = i;
          break;
        }
      }

      if (playerIndex != -1) match.getPlayers().set(playerIndex, null);

      if (match.getPlayers().stream().allMatch(Objects::isNull)) endMatch(match);
      else {
        if (!data.getOpenMatches().contains(match)) data.getOpenMatches().add(match);
        scoreboardManager.updateScoreboard(match);
      }

      Location lobby = (Location) fcManager.getConfigManager().getConfig("config.yml").get("lobby");
      if (lobby != null) player.teleport(lobby);

      match.setTakePlaceNeeded(true);
      match.setLastTakePlaceAnnounceTick(0);
      MatchmakingUtils.clearPlayer(player);
      scoreboardManager.removeScoreboard(player);
    });
  }

  public void takePlace(Player player, int matchId) {
    Optional<Match> matchOpt = data.getOpenMatches().stream()
        .filter(match -> match.getArena().getId() == matchId)
        .findFirst();

    if (!matchOpt.isPresent()) { logger.send(player, Lang.TAKEPLACE_INVALID_ID.replace(new String[]{String.valueOf(matchId)})); return; }

    Match match = matchOpt.get();
    long redTeamCount = match.getPlayers().stream().filter(matchPlayer -> matchPlayer != null && matchPlayer.getTeamColor() == TeamColor.RED).count();
    long blueTeamCount = match.getPlayers().stream().filter(matchPlayer -> matchPlayer != null && matchPlayer.getTeamColor() == TeamColor.BLUE).count();

    TeamColor teamToJoin = (redTeamCount <= blueTeamCount) ? TeamColor.RED : TeamColor.BLUE;
    int openSlotIndex = -1;
    for (int i = 0; i < match.getPlayers().size(); i++) { if (match.getPlayers().get(i) == null) { openSlotIndex = i; break; } }

    if (openSlotIndex != -1) {
      match.getPlayers().set(openSlotIndex, new MatchPlayer(player, teamToJoin));

      if (teamToJoin == TeamColor.RED) player.teleport(match.getArena().getRedSpawn());
      else player.teleport(match.getArena().getBlueSpawn());

      MatchmakingUtils.giveArmor(player, teamToJoin);

      if (match.getPhase() == MatchPhase.LOBBY || match.getPhase() == MatchPhase.STARTING) scoreboardManager.showLobbyScoreboard(match, player);
      else if (match.getPhase() != MatchPhase.ENDED) scoreboardManager.showMatchScoreboard(match, player);
      scoreboardManager.updateScoreboard(match);

      if (match.getPlayers().stream().noneMatch(Objects::isNull)) data.getOpenMatches().remove(match);
      match.setTakePlaceNeeded(false);
      match.setLastTakePlaceAnnounceTick(0);
      logger.send(player, Lang.TAKEPLACE_SUCCESS.replace(new String[]{String.valueOf(match.getArena().getId())}));
    } else logger.send(player, Lang.TAKEPLACE_FULL.replace(new String[]{String.valueOf(matchId)}));
  }

  public void endMatch(Match match) {
    TeamColor winner = null;
    if (match.getScoreRed() > match.getScoreBlue()) winner = TeamColor.RED;
    else if (match.getScoreBlue() > match.getScoreRed()) winner = TeamColor.BLUE;

    for (MatchPlayer matchPlayer : match.getPlayers()) {
      if (matchPlayer == null) continue;
      Player player = matchPlayer.getPlayer();
      if (player == null || !player.isOnline()) continue;

      if (match.getPhase() == MatchPhase.IN_PROGRESS || match.getPhase() == MatchPhase.CONTINUING) {
        PlayerData data = fcManager.getDataManager().get(player);
        if (winner == null) {
          data.add("ties");
          fcManager.getEconomy().depositPlayer(player, 5);
          logger.send(player, Lang.MATCH_TIED_CREDITS.replace(null));
        } else if (matchPlayer.getTeamColor() == winner) {
          data.add("wins");
          data.add("winstreak");
          if ((int) data.get("winstreak") > (int) data.get("bestwinstreak"))
            data.set("bestwinstreak", data.get("winstreak"));

          fcManager.getEconomy().depositPlayer(player, 15);
          logger.send(player, Lang.MATCH_WIN_CREDITS.replace(null));

          if ((int) data.get("winstreak") > 0 && (int) data.get("winstreak") % 5 == 0) {
            fcManager.getEconomy().depositPlayer(player, 100);
            logger.send(player, Lang.MATCH_WINSTREAK_CREDITS.replace(new String[]{String.valueOf(data.get("winstreak"))}));
          }
        } else {
          data.set("winstreak", 0);
          data.add("losses");
        }
      }

      Location lobby = (Location) fcManager.getConfigManager().getConfig("config.yml").get("lobby");
      if (lobby != null) player.teleport(lobby);
      MatchmakingUtils.clearPlayer(player);

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
    data.getMatches().forEach(system::updateMatch);
    system.processQueues();
  }

  public void kick(Player player) {
    getMatch(player).ifPresent(match ->
        match.getPlayers().stream()
            .filter(Objects::nonNull)
            .filter(p -> p.getPlayer() != null && p.getPlayer().equals(player))
            .findFirst()
            .ifPresent(matchPlayer -> {
              if (match.getLastTouch() != matchPlayer) {
                match.setSecondLastTouch(match.getLastTouch());
                match.setLastTouch(matchPlayer);
              }
            })
    );
  }

  public int countActiveLobbies(int matchType) {
    return (int) data.getMatches().stream()
        .filter(m -> m.getArena().getType() == matchType && m.getPhase() != MatchPhase.LOBBY && m.getPhase() != MatchPhase.ENDED)
        .count();
  }

  public int countPlayersInMatches(int matchType) {
    return data.getMatches().stream()
        .filter(m -> m.getArena().getType() == matchType)
        .mapToInt(m -> m.getPlayers().size())
        .sum();
  }

  public int countWaitingPlayers(int matchType) {
    return data.getPlayerQueues().getOrDefault(matchType, new ConcurrentLinkedQueue<>()).size();
  }

  public String listPlayersInMatches(int matchType) {
    return data.getMatches().stream()
        .filter(m -> m.getArena().getType() == matchType)
        .flatMap(m -> m.getPlayers().stream())
        .map(p -> p.getPlayer().getName())
        .collect(Collectors.joining(", "));
  }

  public void forceLeaveAllPlayers() {
    data.getPlayerQueues().values().forEach(Queue::clear);
    new ArrayList<>(data.getMatches()).forEach(this::endMatch);
  }

  public void clearLobbiesAndQueues() {
    data.getPlayerQueues().values().forEach(Queue::clear);

    new ArrayList<>(data.getMatches()).forEach(match -> {
      if (match.getPhase() == MatchPhase.LOBBY || match.getPhase() == MatchPhase.STARTING) {
        new ArrayList<>(match.getPlayers()).stream().filter(Objects::nonNull).forEach(p -> {
          MatchmakingUtils.clearPlayer(p.getPlayer());
          scoreboardManager.removeScoreboard(p.getPlayer());
          logger.send(p.getPlayer(), Lang.MATCHMAN_FORCE_END.replace(new String[]{match.getArena().getType() + "v" + match.getArena().getType()}));
        });
        data.getMatches().remove(match);
      }
    });
  }

  public void recreateScoreboards() {
    if (scoreboardManager == null) return;

    new ArrayList<>(data.getMatches()).forEach(match -> {
      if (match.getPhase() == MatchPhase.ENDED) return;
      if (match.getPhase() == MatchPhase.LOBBY || match.getPhase() == MatchPhase.STARTING) {
        scoreboardManager.createLobbyScoreboard(match);
      } else scoreboardManager.createMatchScoreboard(match);
    });
  }
}
