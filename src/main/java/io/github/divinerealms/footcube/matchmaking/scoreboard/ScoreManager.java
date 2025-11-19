package io.github.divinerealms.footcube.matchmaking.scoreboard;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
import io.github.divinerealms.footcube.matchmaking.util.MatchConstants;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.scoreboard.Scoreboard;
import me.neznamy.tab.api.scoreboard.ScoreboardManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ScoreManager {
  private final FCManager fcManager;
  private ScoreboardManager manager;
  private TabAPI tabAPI;

  public ScoreManager(FCManager fcManager) {
    this.fcManager = fcManager;
    this.tabAPI = fcManager.getTabAPI();
    this.manager = tabAPI.getScoreboardManager();
  }

  public void refreshTabAPI() {
    this.tabAPI = fcManager.getTabAPI();
    this.manager = tabAPI.getScoreboardManager();
  }

  public void createLobbyScoreboard(Match match) {
    if (tabAPI == null || match == null) return;

    if (match.getLobbyScoreboard() != null) {
      try {
        manager.removeScoreboard(match.getLobbyScoreboard());
      } catch (IllegalArgumentException ignored) {}
    }

    String type = match.getArena().getType() + "v" + match.getArena().getType();
    String title = Lang.MATCHES_LIST_LOBBY.replace(new String[]{type, String.valueOf(match.getArena().getId())});
    List<String> lines = Arrays.asList(buildLobbyScoreboard(match).split(System.lineSeparator()));
    String scoreboardName = "FCLobby" + match.getArena().getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
    Scoreboard newScoreboard = manager.createScoreboard(scoreboardName, title, lines);
    match.setLobbyScoreboard(newScoreboard);
  }

  public void createMatchScoreboard(Match match) {
    if (tabAPI == null || match == null) return;

    if (match.getLobbyScoreboard() != null) {
      try {
        manager.removeScoreboard(match.getLobbyScoreboard());
      } catch (IllegalArgumentException ignored) {}
      match.setLobbyScoreboard(null);
    }

    if (match.getMatchScoreboard() != null) {
      try {
        manager.removeScoreboard(match.getMatchScoreboard());
      } catch (IllegalArgumentException ignored) {}
    }

    String type = match.getArena().getType() + "v" + match.getArena().getType();
    String title = Lang.MATCHES_LIST_MATCH.replace(new String[]{type, String.valueOf(match.getArena().getId())});
    List<String> lines = Arrays.asList(buildMatchScoreboard(match).split(System.lineSeparator()));
    String scoreboardName = "FCMatch" + match.getArena().getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
    Scoreboard newScoreboard = manager.createScoreboard(scoreboardName, title, lines);
    match.setMatchScoreboard(newScoreboard);
  }

  public void updateScoreboard(Match match) {
    if (tabAPI == null) return;
    Scoreboard scoreboard;
    List<String> lines;

    if (match.getPhase() == MatchPhase.LOBBY || match.getPhase() == MatchPhase.STARTING) {
      if (match.getLobbyScoreboard() == null) createLobbyScoreboard(match);
      scoreboard = match.getLobbyScoreboard();
      lines = Arrays.asList(buildLobbyScoreboard(match).split(System.lineSeparator()));
    } else {
      if (match.getMatchScoreboard() == null) createMatchScoreboard(match);
      scoreboard = match.getMatchScoreboard();
      lines = Arrays.asList(buildMatchScoreboard(match).split(System.lineSeparator()));
    }

    if (scoreboard == null) return;

    for (int i = 0; i < lines.size(); i++) {
      if (i < scoreboard.getLines().size()) scoreboard.getLines().get(i).setText(lines.get(i));
      else scoreboard.addLine(lines.get(i));
    }

    if (lines.size() < scoreboard.getLines().size()) {
      for (int i = scoreboard.getLines().size() - 1; i >= lines.size(); i--) scoreboard.removeLine(i);
    }
  }

  public void showLobbyScoreboard(Match match, Player player) {
    if (tabAPI == null || match == null || player == null || !player.isOnline()) return;
    if (match.getLobbyScoreboard() == null) createLobbyScoreboard(match);
    TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());
    if (tabPlayer != null) manager.showScoreboard(tabPlayer, match.getLobbyScoreboard());
  }

  public void showMatchScoreboard(Match match, Player player) {
    if (tabAPI == null || match == null || player == null || !player.isOnline()) return;
    if (match.getMatchScoreboard() == null) createMatchScoreboard(match);
    TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());
    if (tabPlayer != null) manager.showScoreboard(tabPlayer, match.getMatchScoreboard());
  }

  public void removeScoreboard(Player player) {
    if (tabAPI == null) return;
    TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());
    if (tabPlayer == null) return;

    manager.resetScoreboard(tabPlayer);
  }

  private String buildLobbyScoreboard(Match match) {
    List<Player> redPlayers = match.getPlayers().stream()
        .filter(Objects::nonNull)
        .filter(p -> p.getPlayer() != null && p.getTeamColor() == TeamColor.RED)
        .map(MatchPlayer::getPlayer)
        .collect(Collectors.toList());

    List<Player> bluePlayers = match.getPlayers().stream()
        .filter(Objects::nonNull)
        .filter(p -> p.getPlayer() != null && p.getTeamColor() == TeamColor.BLUE)
        .map(MatchPlayer::getPlayer)
        .collect(Collectors.toList());

    List<Player> waitingPlayers = match.getPlayers().stream()
        .filter(Objects::nonNull)
        .filter(p -> p.getPlayer() != null && p.getTeamColor() == null)
        .map(MatchPlayer::getPlayer)
        .collect(Collectors.toList());

    StringBuilder playersListBuilder = new StringBuilder();
    if (match.getPhase() == MatchPhase.STARTING) {
      if (!redPlayers.isEmpty()) {
        playersListBuilder.append(IntStream.range(0, redPlayers.size()).mapToObj(i -> Lang.SCOREBOARD_LINES_RED_PLAYERS_ENTRY.replace(
            new String[]{String.valueOf(i + 1), redPlayers.get(i).getName()}
        )).collect(Collectors.joining(System.lineSeparator())));
      }

      if (!bluePlayers.isEmpty()) {
        if (playersListBuilder.length() > 0)
          playersListBuilder.append(System.lineSeparator()).append(ChatColor.RESET).append(System.lineSeparator());
        playersListBuilder.append(IntStream.range(0, bluePlayers.size()).mapToObj(i -> Lang.SCOREBOARD_LINES_BLUE_PLAYERS_ENTRY.replace(
            new String[]{String.valueOf(i + 1), bluePlayers.get(i).getName()}
        )).collect(Collectors.joining(System.lineSeparator())));
      }
    } else {
      if (!waitingPlayers.isEmpty()) {
        playersListBuilder.append(IntStream.range(0, waitingPlayers.size()).mapToObj(i -> Lang.SCOREBOARD_LINES_WAITING_PLAYERS_ENTRY.replace(
            new String[]{String.valueOf(i + 1), waitingPlayers.get(i).getName()}
        )).collect(Collectors.joining(System.lineSeparator())));
      }
    }

    String playersList = playersListBuilder.length() == 0 ? Lang.NOBODY.replace(null) : playersListBuilder.toString();
    String status = match.getPhase() == MatchPhase.LOBBY ? Lang.MATCHES_LIST_WAITING.replace(null) : Lang.MATCHES_LIST_STARTING.replace(new String[]{String.valueOf(match.getCountdown())});

    return Lang.SCOREBOARD_LINES_LOBBY.replace(new String[]{
        playersList, status
    }) + System.lineSeparator() + Lang.SCOREBOARD_FOOTER.replace(null);
  }

  private String buildMatchScoreboard(Match match) {
    String timeDisplay;
    if (match.getPhase() == MatchPhase.CONTINUING) {
      timeDisplay = Lang.MATCHES_LIST_CONTINUING.replace(new String[]{String.valueOf(match.getCountdown())});
    } else {
      long matchDuration = match.getArena().getType() == MatchConstants.TWO_V_TWO ? 120 : 300;
      long elapsedMillis = (System.currentTimeMillis() - match.getStartTime()) - match.getTotalPausedTime();
      long remainingSeconds = matchDuration - TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);

      timeDisplay = Utilities.formatTimePretty((int) remainingSeconds);
    }

    return Lang.SCOREBOARD_LINES_MATCH.replace(new String[]{
        Lang.RED.replace(null), String.valueOf(match.getScoreRed()),
        String.valueOf(match.getScoreBlue()), Lang.BLUE.replace(null),
        timeDisplay
    }) + System.lineSeparator() +  Lang.SCOREBOARD_FOOTER.replace(null);
  }

  public void unregisterScoreboard(Scoreboard scoreboard) {
    if (tabAPI != null && manager != null && scoreboard != null) manager.removeScoreboard(scoreboard);
  }
}