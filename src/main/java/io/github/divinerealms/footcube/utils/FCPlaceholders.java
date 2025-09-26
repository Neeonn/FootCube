package io.github.divinerealms.footcube.utils;

import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Organization;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class FCPlaceholders extends PlaceholderExpansion {
  private final FCManager fcManager;

  public FCPlaceholders(FCManager fcManager) {
    this.fcManager = fcManager;
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public boolean canRegister() {
    return true;
  }

  @Override
  public @NotNull String getIdentifier() {
    return "fc";
  }

  @Override
  public @NotNull String getAuthor() {
    return "neonsh";
  }

  @Override
  public @NotNull String getVersion() {
    return fcManager.getPlugin().getDescription().getVersion();
  }

  @Override
  public String onPlaceholderRequest(Player player, @NotNull String identifier) {
    Organization org = fcManager.getOrg();

    if (identifier.equals("active_lobbies_all")) {
      int count = 0;
      for (String type : Arrays.asList("2v2", "3v3", "4v4")) {
        count += MatchHelper.countActiveLobbies(org, type);
      }
      return String.valueOf(count);
    }

    // Active lobbies
    if (identifier.startsWith("active_lobbies_")) {
      String type = identifier.replace("active_lobbies_", "");
      return String.valueOf(MatchHelper.countActiveLobbies(org, type));
    }

    // Players in arena
    if (identifier.startsWith("players_")) {
      String type = identifier.replace("players_", "");
      return String.valueOf(MatchHelper.countPlayers(org, type));
    }

    // Waiting players
    if (identifier.startsWith("waiting_")) {
      String type = identifier.replace("waiting_", "");
      return String.valueOf(MatchHelper.countWaitingPlayers(org, type));
    }

    // List players
    if (identifier.startsWith("listplayers_")) {
      String type = identifier.replace("listplayers_", "");
      return MatchHelper.listPlayers(org, type);
    }

    // Best stats placeholders
    if (identifier.startsWith("best_")) {
      HighScores highScores = org.getHighscores();
      if (highScores == null
          || highScores.topSkillNames == null
          || highScores.topGoalsNames == null
          || highScores.topWinsNames == null
          || highScores.topStreakNames == null) return "---";

      String[] parts = identifier.split("_");
      if (parts.length != 4) return null;

      String category = parts[1];
      int rank;
      try {
        rank = Integer.parseInt(parts[2]) - 1;
      } catch (NumberFormatException e) {
        return null;
      }

      if (rank < 0 || rank > 2) return null;

      switch (category) {
        case "rating":
          if ("name".equals(parts[3])) return highScores.topSkillNames[rank];
          if ("value".equals(parts[3])) return String.valueOf(highScores.bestRatings[rank]);
          break;
        case "goals":
          if ("name".equals(parts[3])) return highScores.topGoalsNames[rank];
          if ("value".equals(parts[3])) return String.valueOf(highScores.mostGoals[rank]);
          break;
        case "wins":
          if ("name".equals(parts[3])) return highScores.topWinsNames[rank];
          if ("value".equals(parts[3])) return String.valueOf(highScores.mostWins[rank]);
          break;
        case "streak":
          if ("name".equals(parts[3])) return highScores.topStreakNames[rank];
          if ("value".equals(parts[3])) return String.valueOf(highScores.longestStreak[rank]);
          break;
      }
      return "---";
    }

    // Player-specific stats
    if (identifier.startsWith("stats_") && player != null) {
      String statKey = identifier.replace("stats_", "").toLowerCase();
      PlayerData data = fcManager.getDataManager().get(player);
      if (data == null || !data.has("matches")) return "---";

      int matches = (int) data.get("matches");
      int wins = (int) data.get("wins");
      int ties = (int) data.get("ties");
      int losses = matches - wins - ties;
      int goals = (int) data.get("goals");
      int bestWinStreak = (int) data.get("bestwinstreak");

      double winsPerMatch = matches > 0 ? (double) wins / matches : 0;
      double goalsPerMatch = matches > 0 ? (double) goals / matches : 0;

      double multiplier = 1.0 - Math.pow(0.9, matches);
      double goalBonus = matches > 0
          ? (goals == matches ? 1.0 : Math.min(1.0, 1 - multiplier * Math.pow(0.2, (double) goals / matches)))
          : 0.5;

      double addition = 0.0;
      if (matches > 0 && wins + ties > 0) addition = 8.0 * (1.0 / ((100.0 * matches) / (wins + 0.5 * ties) / 100.0)) - 4.0;
      else if (matches > 0) addition = -4.0;

      double skillLevel = Math.min(5.0 + goalBonus + addition * multiplier, 10.0);

      int rank = (int) (skillLevel * 2.0 - 0.5);
      String rankName;
      switch (rank) {
        case 1: rankName = "Nub"; break;
        case 2: rankName = "Luzer"; break;
        case 3: rankName = "Beba"; break;
        case 4: rankName = "Učenik"; break;
        case 5: rankName = "Loš"; break;
        case 6: rankName = ":("; break;
        case 7: rankName = "Eh"; break;
        case 8: rankName = "Igrač"; break;
        case 9: rankName = "Ok"; break;
        case 10: rankName = "Prosečan"; break;
        case 11: rankName = "Dobar"; break;
        case 12: rankName = "Odličan"; break;
        case 13: rankName = "Kralj"; break;
        case 14: rankName = "Super"; break;
        case 15: rankName = "Pro"; break;
        case 16: rankName = "Maradona"; break;
        case 17: rankName = "Supermen"; break;
        case 18: rankName = "Bog"; break;
        case 19: rankName = "h4x0r"; break;
        default: rankName = "Nema"; break;
      }

      switch (statKey) {
        case "matches": return String.valueOf(matches);
        case "wins": return String.valueOf(wins);
        case "losses": return String.valueOf(losses);
        case "ties": return String.valueOf(ties);
        case "winspermatch": return String.format("%.2f", winsPerMatch);
        case "goals": return String.valueOf(goals);
        case "goalspermatch": return String.format("%.2f", goalsPerMatch);
        case "bestwinstreak": return String.valueOf(bestWinStreak);
        case "skill": return String.format("%.2f", skillLevel);
        case "rank": return rankName;
        default: return "---";
      }
    }

    return null;
  }
}
