package io.github.divinerealms.footcube.matchmaking.scoreboard;

import static io.github.divinerealms.footcube.configs.Lang.BLUE;
import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_CONTINUING;
import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_LOBBY;
import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_MATCH;
import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_STARTING;
import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_WAITING;
import static io.github.divinerealms.footcube.configs.Lang.NOBODY;
import static io.github.divinerealms.footcube.configs.Lang.RED;
import static io.github.divinerealms.footcube.configs.Lang.SCOREBOARD_FOOTER;
import static io.github.divinerealms.footcube.configs.Lang.SCOREBOARD_LINES_BLUE_PLAYERS_ENTRY;
import static io.github.divinerealms.footcube.configs.Lang.SCOREBOARD_LINES_LOBBY;
import static io.github.divinerealms.footcube.configs.Lang.SCOREBOARD_LINES_MATCH;
import static io.github.divinerealms.footcube.configs.Lang.SCOREBOARD_LINES_RED_PLAYERS_ENTRY;
import static io.github.divinerealms.footcube.configs.Lang.SCOREBOARD_LINES_WAITING_PLAYERS_ENTRY;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.TWO_V_TWO;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.scoreboard.Scoreboard;
import me.neznamy.tab.api.scoreboard.ScoreboardManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

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
    if (tabAPI == null || match == null) {
      return;
    }

    if (match.getLobbyScoreboard() != null) {
      try {
        manager.removeScoreboard(match.getLobbyScoreboard());
      } catch (IllegalArgumentException ignored) {
      }
    }

    String type = match.getArena().getType() + "v" + match.getArena().getType();
    String title = MATCHES_LIST_LOBBY.replace(type, String.valueOf(match.getArena().getId()));
    List<String> lines = Arrays.asList(buildLobbyScoreboard(match).split(System.lineSeparator()));
    String scoreboardName =
        "FCLobby" + match.getArena().getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
    Scoreboard newScoreboard = manager.createScoreboard(scoreboardName, title, lines);
    match.setLobbyScoreboard(newScoreboard);
  }

  public void createMatchScoreboard(Match match) {
    if (tabAPI == null || match == null) {
      return;
    }

    if (match.getLobbyScoreboard() != null) {
      try {
        manager.removeScoreboard(match.getLobbyScoreboard());
      } catch (IllegalArgumentException ignored) {
      }
      match.setLobbyScoreboard(null);
    }

    if (match.getMatchScoreboard() != null) {
      try {
        manager.removeScoreboard(match.getMatchScoreboard());
      } catch (IllegalArgumentException ignored) {
      }
    }

    String type = match.getArena().getType() + "v" + match.getArena().getType();
    String title = MATCHES_LIST_MATCH.replace(type, String.valueOf(match.getArena().getId()));
    List<String> lines = Arrays.asList(buildMatchScoreboard(match).split(System.lineSeparator()));
    String scoreboardName =
        "FCMatch" + match.getArena().getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
    Scoreboard newScoreboard = manager.createScoreboard(scoreboardName, title, lines);
    match.setMatchScoreboard(newScoreboard);
  }

  public void updateScoreboard(Match match) {
    if (tabAPI == null) {
      return;
    }
    Scoreboard scoreboard;
    List<String> lines;

    if (match.getPhase() == MatchPhase.LOBBY || match.getPhase() == MatchPhase.STARTING) {
      if (match.getLobbyScoreboard() == null) {
        createLobbyScoreboard(match);
      }
      scoreboard = match.getLobbyScoreboard();
      lines = Arrays.asList(buildLobbyScoreboard(match).split(System.lineSeparator()));
    } else {
      if (match.getMatchScoreboard() == null) {
        createMatchScoreboard(match);
      }
      scoreboard = match.getMatchScoreboard();
      lines = Arrays.asList(buildMatchScoreboard(match).split(System.lineSeparator()));
    }

    if (scoreboard == null) {
      return;
    }

    for (int i = 0; i < lines.size(); i++) {
      if (i < scoreboard.getLines().size()) {
        scoreboard.getLines().get(i).setText(lines.get(i));
      } else {
        scoreboard.addLine(lines.get(i));
      }
    }

    if (lines.size() < scoreboard.getLines().size()) {
      for (int i = scoreboard.getLines().size() - 1; i >= lines.size(); i--) {
        scoreboard.removeLine(i);
      }
    }
  }

  public void showLobbyScoreboard(Match match, Player player) {
    if (tabAPI == null || match == null || player == null || !player.isOnline()) {
      return;
    }
    if (match.getLobbyScoreboard() == null) {
      createLobbyScoreboard(match);
    }
    TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());
    if (tabPlayer != null) {
      manager.showScoreboard(tabPlayer, match.getLobbyScoreboard());
    }
  }

  public void showMatchScoreboard(Match match, Player player) {
    if (tabAPI == null || match == null || player == null || !player.isOnline()) {
      return;
    }
    if (match.getMatchScoreboard() == null) {
      createMatchScoreboard(match);
    }
    TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());
    if (tabPlayer != null) {
      manager.showScoreboard(tabPlayer, match.getMatchScoreboard());
    }
  }

  public void removeScoreboard(Player player) {
    if (tabAPI == null) {
      return;
    }
    TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());
    if (tabPlayer == null) {
      return;
    }
    manager.resetScoreboard(tabPlayer);
  }

  private String buildLobbyScoreboard(Match match) {
    List<Player> redPlayers = new ArrayList<>();
    List<Player> bluePlayers = new ArrayList<>();
    List<Player> waitingPlayers = new ArrayList<>();

    List<MatchPlayer> matchPlayers = match.getPlayers();
    if (matchPlayers != null) {
      for (MatchPlayer mp : matchPlayers) {
        if (mp != null && mp.getPlayer() != null) {
          if (mp.getTeamColor() == TeamColor.RED) {
            redPlayers.add(mp.getPlayer());
          } else {
            if (mp.getTeamColor() == TeamColor.BLUE) {
              bluePlayers.add(mp.getPlayer());
            } else {
              if (mp.getTeamColor() == null) {
                waitingPlayers.add(mp.getPlayer());
              }
            }
          }
        }
      }
    }

    StringBuilder playersListBuilder = new StringBuilder();
    if (match.getPhase() == MatchPhase.STARTING) {
      for (int i = 0; i < redPlayers.size(); i++) {
        if (playersListBuilder.length() > 0) {
          playersListBuilder.append(System.lineSeparator());
        }
        playersListBuilder.append(SCOREBOARD_LINES_RED_PLAYERS_ENTRY.replace(
            String.valueOf(i + 1), redPlayers.get(i).getName())
        );
      }

      if (!redPlayers.isEmpty() && !bluePlayers.isEmpty()) {
        playersListBuilder
            .append(System.lineSeparator())
            .append(ChatColor.RESET)
            .append(System.lineSeparator());
      }

      for (int i = 0; i < bluePlayers.size(); i++) {
        if (i > 0 || (!redPlayers.isEmpty() && playersListBuilder.length() > 0
            && !playersListBuilder.toString().endsWith(System.lineSeparator()))) {
          if (i > 0) {
            playersListBuilder.append(System.lineSeparator());
          }
        }
        playersListBuilder.append(SCOREBOARD_LINES_BLUE_PLAYERS_ENTRY.replace(
            String.valueOf(i + 1), bluePlayers.get(i).getName())
        );
      }
    } else {
      for (int i = 0; i < waitingPlayers.size(); i++) {
        if (i > 0) {
          playersListBuilder.append(System.lineSeparator());
        }
        playersListBuilder.append(SCOREBOARD_LINES_WAITING_PLAYERS_ENTRY.replace(
            String.valueOf(i + 1), waitingPlayers.get(i).getName())
        );
      }
    }

    String playersList = playersListBuilder.length() == 0
        ? NOBODY.toString()
        : playersListBuilder.toString();

    String status = match.getPhase() == MatchPhase.LOBBY
        ? MATCHES_LIST_WAITING.toString()
        : MATCHES_LIST_STARTING.replace(String.valueOf(match.getCountdown()));

    return SCOREBOARD_LINES_LOBBY.replace(playersList, status) + System.lineSeparator()
        + SCOREBOARD_FOOTER;
  }

  private String buildMatchScoreboard(Match match) {
    String timeDisplay;
    if (match.getPhase() == MatchPhase.CONTINUING) {
      timeDisplay = MATCHES_LIST_CONTINUING.replace(String.valueOf(match.getCountdown()));
    } else {
      long matchDuration = match.getArena().getType() == TWO_V_TWO
          ? 120
          : 300;
      long elapsedMillis =
          (System.currentTimeMillis() - match.getStartTime()) - match.getTotalPausedTime();
      long remainingSeconds = matchDuration - TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);

      timeDisplay = Utilities.formatTimePretty((int) remainingSeconds);
    }

    return SCOREBOARD_LINES_MATCH.replace(RED.toString(), String.valueOf(match.getScoreRed()),
        String.valueOf(match.getScoreBlue()), BLUE.toString(),
        timeDisplay) + System.lineSeparator() + SCOREBOARD_FOOTER;
  }

  public void unregisterScoreboard(Scoreboard scoreboard) {
    if (tabAPI != null && manager != null && scoreboard != null) {
      manager.removeScoreboard(scoreboard);
    }
  }
}