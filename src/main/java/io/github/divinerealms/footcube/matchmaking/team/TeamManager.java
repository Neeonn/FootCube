package io.github.divinerealms.footcube.matchmaking.team;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.utils.Logger;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.*;

import static io.github.divinerealms.footcube.configs.Lang.TEAM_DISBANDED;

@Getter
public class TeamManager {
  private final FCManager fcManager;
  private final Logger logger;
  private final Map<Player, Map<Player, Integer>> teamInvites = new HashMap<>();
  private final Map<UUID, Team> playerTeams = new HashMap<>();

  public TeamManager(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
  }

  public void invite(Player inviter, Player invited, int matchType) {
    teamInvites.put(invited, Collections.singletonMap(inviter, matchType));
  }

  public Player getInviter(Player invited) {
    Map<Player, Integer> invite = teamInvites.get(invited);
    if (invite == null || invite.isEmpty()) {
      return null;
    }
    return invite.keySet().iterator().next();
  }

  public int getInviteMatchType(Player invited) {
    Map<Player, Integer> invite = teamInvites.get(invited);
    if (invite == null || invite.isEmpty()) {
      return -1;
    }
    return invite.values().iterator().next();
  }

  public void removeInvite(Player invited) {
    teamInvites.remove(invited);
  }

  public boolean noInvite(Player invited) {
    return !teamInvites.containsKey(invited);
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

  public void disbandTeamIfInLobby(Player leaver) {
    Team team = getTeam(leaver);
    if (team == null) {
      return;
    }

    boolean anyInMatchLobby = false;
    Optional<Match> matchOpt = fcManager.getMatchManager().getMatch(leaver);
    if (matchOpt.isPresent()) {
      Match match = matchOpt.get();
      if (match.getPhase() == MatchPhase.LOBBY) {
        anyInMatchLobby = true;
      }
    }

    boolean anyInQueue = false;
    Collection<Queue<Player>> playerQueues = fcManager.getMatchManager().getData().getPlayerQueues().values();
    for (Queue<Player> queue : playerQueues) {
      if (queue != null && queue.contains(leaver)) {
        anyInQueue = true;
        break;
      }
    }

    if (!anyInMatchLobby && !anyInQueue) {
      return;
    }

    List<Player> members = team.getMembers();
    if (members != null) {
      for (Player player : members) {
        if (player != null && player.isOnline() && !player.equals(leaver)) {
          logger.send(player, TEAM_DISBANDED, leaver.getName());
        }
      }
    }

    disbandTeam(team);
  }

  public void forceDisbandTeam(Player leaver) {
    Team team = getTeam(leaver);
    if (team == null) {
      return;
    }

    for (Player player : team.getMembers()) {
      if (player != null && player.isOnline() && !player.equals(leaver)) {
        logger.send(player, TEAM_DISBANDED, leaver.getName());
      }
    }

    disbandTeam(team);
  }
}
