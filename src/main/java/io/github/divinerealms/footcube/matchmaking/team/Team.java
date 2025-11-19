package io.github.divinerealms.footcube.matchmaking.team;

import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

@Getter
public class Team {
  private final Player leader;
  private final Player member;
  private final int matchType;

  public Team(Player leader, Player member, int matchType) {
    this.leader = leader;
    this.member = member;
    this.matchType = matchType;
  }

  public List<Player> getMembers() {
    return Arrays.asList(leader, member);
  }
}