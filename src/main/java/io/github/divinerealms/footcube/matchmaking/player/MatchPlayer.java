package io.github.divinerealms.footcube.matchmaking.player;

import lombok.Data;
import org.bukkit.entity.Player;

@Data
public class MatchPlayer {

  private final Player player;
  private TeamColor teamColor;
  private int goals;
  private int assists;
  private int ownGoals;

  public MatchPlayer(Player player, TeamColor teamColor) {
    this.player = player;
    this.teamColor = teamColor;
    this.goals = 0;
    this.assists = 0;
    this.ownGoals = 0;
  }

  public void incrementGoals() {
    this.goals++;
  }

  public void incrementAssists() {
    this.assists++;
  }

  public void incrementOwnGoals() {
    this.ownGoals++;
  }
}