package io.github.divinerealms.footcube.utils;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.Match;
import io.github.divinerealms.footcube.core.Organization;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MatchHelper {
  public static class ArenaData {
    public Match[] matches;
    public final int lobby;
    public final int size;

    public ArenaData(Match[] matches, int lobby, int size) {
      this.matches = matches;
      this.lobby = lobby;
      this.size = size;
    }
  }

  public static ArenaData getArenaData(Organization org, String type) {
    switch (type.toLowerCase()) {
      case "2v2":
        return new ArenaData(org.getMatches2v2(), org.getLobby2v2(), 2);
      case "3v3":
        return new ArenaData(org.getMatches3v3(), org.getLobby3v3(), 3);
      case "4v4":
        return new ArenaData(org.getMatches4v4(), org.getLobby4v4(), 4);
      default:
        return null;
    }
  }

  public static Match getMatchArena(Organization org, String playerName) {
    for (String type : new String[]{"2v2", "3v3", "4v4"}) {
      ArenaData data = getArenaData(org, type);
      if (data != null && data.matches != null) {
        int lobby = data.lobby;
        if (lobby >= 0 && lobby < data.matches.length) {
          Match match = data.matches[lobby];
          if (match != null && match.isInMatch(org.getPlugin().getServer().getPlayer(playerName))) {
            return match;
          }
        }
      }
    }
    return null;
  }

  public static Match getMatch(Organization org, Player player) {
    List<Match> matches = getAllMatches(org, player);
    return matches.isEmpty() ? null : matches.get(0);
  }

  public static List<Match> getAllMatches(Organization org, Player player) {
    List<Match> result = new ArrayList<>();
    if (player == null) return result;

    Match[][] allMatches = {org.getMatches2v2(), org.getMatches3v3(), org.getMatches4v4()};
    for (Match[] matches : allMatches) {
      if (matches == null) continue;
      for (Match match : matches) {
        if (match != null && match.isInMatch(player)) result.add(match);
      }
    }

    return result;
  }

  public static int getMatchSize(Organization org, Match match) {
    if (match == null) return -1;

    if (org.getMatches2v2() != null && containsMatch(org.getMatches2v2(), match)) return 2;
    if (org.getMatches3v3() != null && containsMatch(org.getMatches3v3(), match)) return 3;
    if (org.getMatches4v4() != null && containsMatch(org.getMatches4v4(), match)) return 4;

    return -1;
  }

  private static boolean containsMatch(Match[] matches, Match match) {
    for (Match m : matches) {
      if (m == match) return true;
    }
    return false;
  }

  public static int getMatchArenaType(Organization org, String playerName) {
    Match match = getMatchArena(org, playerName);
    if (match == null) return -1;

    for (String type : new String[]{"2v2", "3v3", "4v4"}) {
      ArenaData data = getArenaData(org, type);
      if (data != null && data.matches != null) {
        int lobby = data.lobby;
        if (lobby >= 0 && lobby < data.matches.length && data.matches[lobby] == match) {
          return data.size;
        }
      }
    }
    return -1;
  }

  public static int countActiveLobbies(Organization org, String type) {
    ArenaData data = getArenaData(org, type);
    if (data == null || data.matches == null) return 0;
    int count = 0;
    for (Match match : data.matches) {
      if (match != null && match.phase > 1) count++;
    }
    return count;
  }

  public static int countPlayers(Organization org, String type) {
    ArenaData data = getArenaData(org, type);
    if (data == null || data.matches == null) return 0;
    int total = 0;
    for (Match match : data.matches) {
      if (match == null) continue;
      if (match.redPlayers != null) total += match.redPlayers.size();
      if (match.bluePlayers != null) total += match.bluePlayers.size();
    }
    return total;
  }

  public static int countWaitingPlayers(Organization org, String type) {
    ArenaData data = getArenaData(org, type);
    if (data == null || data.matches == null) return 0;
    int total = 0;
    for (Match match : data.matches) {
      if (match == null) continue;
      if (match.phase == 1) {
        if (match.redPlayers != null) total += match.redPlayers.size();
        if (match.bluePlayers != null) total += match.bluePlayers.size();
      }
    }
    return total;
  }

  public static String listPlayers(Organization org, String type) {
    ArenaData data = getArenaData(org, type);
    if (data == null || data.matches == null) return "No players";
    List<String> playerNames = new ArrayList<>();
    for (Match match : data.matches) {
      if (match == null) continue;
      if (match.redPlayers != null) {
        for (Player p : match.redPlayers) {
          if (p != null) playerNames.add(p.getName());
        }
      }
      if (match.bluePlayers != null) {
        for (Player p : match.bluePlayers) {
          if (p != null) playerNames.add(p.getName());
        }
      }
    }
    return playerNames.isEmpty() ? "No players" : String.join(", ", playerNames);
  }

  public static Player getTeammate(Organization org, Player player) {
    if (player == null) return null;

    for (Player[] team : org.getWaitingTeams()) {
      if (team == null) continue;
      if (team[0] != null && team[0].equals(player)) return team[1];
      if (team[1] != null && team[1].equals(player)) return team[0];
    }

    return null;
  }

  public static void leaveMatch(Organization org, Player player, Match active, Logger logger, boolean notifyTeam) {
    player.getInventory().setItem(4, null);
    player.setExp(0);

    if (active != null) {
      Boolean red = active.isRed.get(player);
      org.clearInventory(player);
      active.leave(player);
      if (red != null) org.playerLeaves(active, red);
    }

    Match lobby = getMatchArena(org, player.getName());
    if (lobby != null) {
      lobby.leave(player);

      Player mate = getTeammate(org, player);
      if (mate != null) {
        lobby.leave(mate);
        if (notifyTeam) logger.send(mate, Lang.LEFT.replace(null));
        org.getWaitingPlayers().remove(mate.getName());
        org.getWaitingTeamPlayers().remove(mate);
      }

      org.setWaitingTeams(org.reduceArray(org.getWaitingTeams(), player));
    }

    org.getWaitingPlayers().remove(player.getName());
    org.getPlayingPlayers().remove(player.getName());
    org.getWaitingTeamPlayers().remove(player);
    org.getTeam().remove(player);
    org.getTeamReverse().remove(player);
    org.getTeamType().remove(player);

    logger.send(player, Lang.LEFT.replace(null));
  }
}