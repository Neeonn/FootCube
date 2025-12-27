package io.github.divinerealms.footcube.matchmaking.util;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

public class MatchUtils {

  public static void giveArmor(Player player, TeamColor color) {
    ItemStack chestplate = createColoredArmor(Material.LEATHER_CHESTPLATE, color == TeamColor.RED ? Color.RED : Color.BLUE);
    ItemStack leggings = createColoredArmor(Material.LEATHER_LEGGINGS, color == TeamColor.RED ? Color.RED : Color.BLUE);

    PlayerInventory inventory = player.getInventory();
    inventory.setChestplate(chestplate);
    inventory.setLeggings(leggings);
  }

  public static ItemStack createColoredArmor(Material material, org.bukkit.Color color) {
    ItemStack is = new ItemStack(material);
    ItemMeta meta = is.getItemMeta();
    if (meta instanceof LeatherArmorMeta) {
      LeatherArmorMeta leatherMeta = (LeatherArmorMeta) meta;
      leatherMeta.setColor(color);
      is.setItemMeta(leatherMeta);
    }
    return is;
  }

  public static void clearPlayer(Player player) {
    if (player == null) return;
    player.getInventory().setArmorContents(null);
    player.getInventory().clear();
    player.setExp(0);
    player.setLevel(0);
  }

  public static List<String> getFormattedMatches(List<Match> matches) {
    List<String> output = new ArrayList<>();
    if (matches == null) return output;

    boolean firstBlock = true;

    for (Match match : matches) {
      if (match == null || match.getPlayers() == null) continue;

      boolean allNull = true;
      for (MatchPlayer mp : match.getPlayers()) { if (mp != null) { allNull = false; break; } }
      if (allNull) continue;

      if (!firstBlock) output.add("");
      firstBlock = false;

      String type = match.getArena().getType() + "v" + match.getArena().getType();

      List<String> redPlayers = new ArrayList<>();
      List<String> bluePlayers = new ArrayList<>();
      List<String> waitingPlayers = new ArrayList<>();

      for (MatchPlayer mp : match.getPlayers()) {
        if (mp == null || mp.getPlayer() == null) continue;

        String name = mp.getPlayer().getName();
        if (mp.getTeamColor() == TeamColor.RED) {
          redPlayers.add(name);
        } else if (mp.getTeamColor() == TeamColor.BLUE) {
          bluePlayers.add(name);
        } else if (mp.getTeamColor() == null) {
          waitingPlayers.add(name);
        }
      }

      String timeDisplay;
      if (match.getPhase() == MatchPhase.LOBBY) {
        timeDisplay = Lang.MATCHES_LIST_WAITING.replace(null);
      } else if (match.getPhase() == MatchPhase.STARTING || match.getPhase() == MatchPhase.CONTINUING) {
        timeDisplay = Lang.MATCHES_LIST_STARTING.replace(new String[]{String.valueOf(match.getCountdown())});
      } else {
        long matchDuration = match.getArena().getType() == MatchConstants.TWO_V_TWO ? 120 : 300;
        long elapsedMillis = (System.currentTimeMillis() - match.getStartTime()) - match.getTotalPausedTime();
        long remainingSeconds = matchDuration - TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);

        timeDisplay = Utilities.formatTimePretty((int) remainingSeconds);
      }

      if (match.getPhase() == MatchPhase.LOBBY) {
        output.add(Lang.MATCHES_LIST_LOBBY.replace(new String[]{type, String.valueOf(match.getArena().getId())}));
        output.add(Lang.MATCHES_LIST_WAITINGPLAYERS.replace(new String[]{waitingPlayers.isEmpty() ? "/" : joinStrings(waitingPlayers)}));
        output.add(Lang.MATCHES_LIST_STATUS.replace(new String[]{timeDisplay}));
      } else if (match.getPhase() == MatchPhase.STARTING) {
        output.add(Lang.MATCHES_LIST_LOBBY.replace(new String[]{type, String.valueOf(match.getArena().getId())}));
        output.add(Lang.MATCHES_LIST_REDPLAYERS.replace(new String[]{redPlayers.isEmpty() ? "/" : joinStrings(redPlayers)}));
        output.add(Lang.MATCHES_LIST_BLUEPLAYERS.replace(new String[]{bluePlayers.isEmpty() ? "/" : joinStrings(bluePlayers)}));
        output.add(Lang.MATCHES_LIST_STATUS.replace(new String[]{timeDisplay}));
      } else {
        output.add(Lang.MATCHES_LIST_MATCH.replace(new String[]{type, String.valueOf(match.getArena().getId())}));
        output.add(Lang.MATCHES_LIST_RESULT.replace(new String[]{String.valueOf(match.getScoreRed()), String.valueOf(match.getScoreBlue()), Lang.MATCHES_LIST_TIMELEFT.replace(new String[]{timeDisplay})}));
        output.add(Lang.MATCHES_LIST_REDPLAYERS.replace(new String[]{redPlayers.isEmpty() ? "/" : joinStrings(redPlayers)}));
        output.add(Lang.MATCHES_LIST_BLUEPLAYERS.replace(new String[]{bluePlayers.isEmpty() ? "/" : joinStrings(bluePlayers)}));
      }
    }
    return output;
  }

  private static String joinStrings(List<String> list) {
    if (list == null || list.isEmpty()) return "/";
    StringJoiner joiner = new StringJoiner(", ");
    for (String s : list) {
      if (s != null) joiner.add(s);
    }
    return joiner.toString();
  }

  public static void displayGoalMessage(Player player, String style, boolean ownGoal, boolean isHatTrick,
                                        boolean isViewerScorer, String scorerName, String assistText, String teamColorText,
                                        double distance, Match match, Logger logger, Plugin plugin) {
    String distanceString = String.format("%.0f", distance);
    String redTeam = Lang.RED.replace(null);
    String blueTeam = Lang.BLUE.replace(null);

    switch (style) {
      case "epic":
        displayEpicGoal(player, ownGoal, isHatTrick, isViewerScorer, scorerName, assistText,
            teamColorText, distanceString, redTeam, blueTeam, match, logger, plugin);
        break;

      case "simple":
        displaySimpleGoal(player, ownGoal, scorerName, distanceString, logger);
        break;

      case "minimal":
        displayMinimalGoal(player, ownGoal, scorerName, redTeam, blueTeam, match, logger);
        break;

      default:
        displayDefaultGoal(player, ownGoal, isHatTrick, isViewerScorer, scorerName, assistText,
            teamColorText, distanceString, redTeam, blueTeam, match, logger);
        break;
    }
  }

  public static boolean isValidGoalMessage(String style) {
    return style != null && (style.equals("default") || style.equals("epic")
        || style.equals("simple") || style.equals("minimal") || style.equals("custom"));
  }

  private static void displayEpicGoal(Player player, boolean ownGoal, boolean isHatTrick, boolean isViewerScorer,
                                      String scorerName, String assistText, String teamColorText, String distance,
                                      String redTeam, String blueTeam, Match match, Logger logger, Plugin plugin) {
    String initialTitle = ownGoal ? Lang.GM_EPIC_TITLE_1.replace(null)
        : (isHatTrick ? Lang.GM_EPIC_TITLE_1_HATTY.replace(null) : Lang.GM_EPIC_TITLE_1_GOAL.replace(null));
    String initialSubtitle = ownGoal ? Lang.GM_EPIC_SUBTITLE_1.replace(null)
        : (isViewerScorer ? Lang.GM_EPIC_SUBTITLE_1_SCORER.replace(null) : Lang.GM_EPIC_SUBTITLE_1_OTHER.replace(null));

    logger.title(player, initialTitle, initialSubtitle, 5, 35, 10);

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      String secondTitle = ownGoal ? Lang.GM_EPIC_TITLE_2.replace(new String[]{teamColorText}) : Lang.GM_EPIC_TITLE_2_GOAL.replace(new String[]{teamColorText});
      String secondSubtitle = Lang.GM_EPIC_SUBTITLE_2.replace(new String[]{scorerName, assistText.isEmpty() ? "" : assistText});
      logger.title(player, secondTitle, secondSubtitle, 0, 35, 10);
    }, 40L);

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      String thirdTitle = Lang.GM_EPIC_TITLE_3.replace(new String[]{distance});
      String thirdSubtitle = Lang.GM_EPIC_SUBTITLE_3.replace(new String[]{redTeam, String.valueOf(match.getScoreRed()), String.valueOf(match.getScoreBlue()), blueTeam});
      logger.title(player, thirdTitle, thirdSubtitle, 0, 30, 10);
    }, 80L);
  }

  private static void displaySimpleGoal(Player player, boolean ownGoal, String scorerName, String distance, Logger logger) {
    String title = ownGoal ? Lang.GM_SIMPLE_TITLE.replace(null) : Lang.GM_SIMPLE_TITLE_GOAL.replace(null);
    String subtitle = Lang.GM_SIMPLE_SUBTITLE.replace(new String[]{scorerName, distance});
    logger.title(player, title, subtitle, 5, 40, 10);
  }

  private static void displayMinimalGoal(Player player, boolean ownGoal, String scorerName,
                                         String redTeam, String blueTeam, Match match, Logger logger) {
    String message = ownGoal
        ? Lang.GM_MINIMAL_OWN.replace(new String[]{scorerName, redTeam, String.valueOf(match.getScoreRed()), String.valueOf(match.getScoreBlue()), blueTeam})
        : Lang.GM_MINIMAL_GOAL.replace(new String[]{scorerName, redTeam, String.valueOf(match.getScoreRed()), String.valueOf(match.getScoreBlue()), blueTeam});

    logger.sendActionBar(player, message);
  }

  private static void displayDefaultGoal(Player player, boolean ownGoal, boolean isHatTrick, boolean isViewerScorer,
                                         String scorerName, String assistText, String teamColorText, String distance,
                                         String redTeam, String blueTeam, Match match, Logger logger) {
    String title = ownGoal ? Lang.GM_DEFAULT_TITLE_OWN.replace(null)
        : (isHatTrick ? Lang.GM_DEFAULT_TITLE_HATTY.replace(null)
        : (isViewerScorer ? Lang.GM_DEFAULT_TITLE_SCORER.replace(null) : Lang.GM_DEFAULT_TITLE_GOAL.replace(null)));

    String subtitle = ownGoal
        ? Lang.GM_DEFAULT_SUBTITLE_OWN.replace(new String[]{scorerName, teamColorText})
        : Lang.GM_DEFAULT_SUBTITLE_GOAL.replace(new String[]{scorerName, distance, assistText.isEmpty() ? "" : assistText});

    logger.title(player, title, subtitle, 10, 50, 10);

    String scoreMessage = Lang.GM_DEFAULT_ACTIONBAR.replace(new String[]{redTeam, String.valueOf(match.getScoreRed()), String.valueOf(match.getScoreBlue()), blueTeam});
    logger.sendActionBar(player, scoreMessage);
  }
}