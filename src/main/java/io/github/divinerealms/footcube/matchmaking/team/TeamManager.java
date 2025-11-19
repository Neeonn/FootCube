package io.github.divinerealms.footcube.matchmaking.team;

import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public class TeamManager {
  private final Map<Player, Map<Player, Integer>> teamInvites = new HashMap<>();
  private final Map<UUID, Team> playerTeams = new HashMap<>();

  public void invite(Player inviter, Player invited, int matchType) {
    teamInvites.put(invited, Collections.singletonMap(inviter, matchType));
  }

  public Player getInviter(Player invited) {
    Map<Player, Integer> invite = teamInvites.get(invited);
    if (invite == null || invite.isEmpty()) return null;
    return invite.keySet().iterator().next();
  }

  public int getInviteMatchType(Player invited) {
    Map<Player, Integer> invite = teamInvites.get(invited);
    if (invite == null || invite.isEmpty()) return -1;
    return invite.values().iterator().next();
  }

  public void removeInvite(Player invited) {
    teamInvites.remove(invited);
  }

  public boolean hasInvite(Player invited) {
    return teamInvites.containsKey(invited);
  }

  public Team getTeam(Player player) {
    return playerTeams.get(player.getUniqueId());
  }

  public boolean isInTeam(Player player) {
    return playerTeams.containsKey(player.getUniqueId());
  }

  public void createTeam(Player leader, Player member, int matchType) {
    Team team = new Team(leader, member, matchType);
    playerTeams.put(leader.getUniqueId(), team);
    playerTeams.put(member.getUniqueId(), team);
  }

  public void disbandTeam(Team team) {
    team.getMembers().forEach(member -> playerTeams.remove(member.getUniqueId()));
  }
}
