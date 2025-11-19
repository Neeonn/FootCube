package io.github.divinerealms.footcube.matchmaking;

import io.github.divinerealms.footcube.matchmaking.arena.Arena;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import lombok.Data;
import me.neznamy.tab.api.scoreboard.Scoreboard;
import org.bukkit.entity.Slime;

import java.util.List;

@Data
public class Match {
  private final Arena arena;
  private final List<MatchPlayer> players;
  private MatchPhase phase;
  private int countdown, scoreRed, scoreBlue, tick;
  private Slime cube;
  private MatchPlayer lastTouch, secondLastTouch;
  private Scoreboard lobbyScoreboard, matchScoreboard;
  private long startTime, pauseStartTime, totalPausedTime, lastTakePlaceAnnounceTick;
  private boolean takePlaceNeeded;

  public Match(Arena arena, List<MatchPlayer> players) {
    this.arena = arena;
    this.players = players;
    this.phase = MatchPhase.LOBBY;
    this.countdown = 0;
    this.scoreRed = 0;
    this.scoreBlue = 0;
    this.startTime = 0;
    this.tick = 0;
    this.pauseStartTime = 0;
    this.totalPausedTime = 0;
    this.lastTakePlaceAnnounceTick = 0;
    this.takePlaceNeeded = false;
  }
}
