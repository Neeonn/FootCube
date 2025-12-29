package io.github.divinerealms.footcube.matchmaking.util;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.logic.MatchSystem;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
import io.github.divinerealms.footcube.matchmaking.scoreboard.ScoreManager;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.github.divinerealms.footcube.configs.Lang.*;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.TWO_V_TWO;

public class MatchUtils {
  public static void giveArmor(Player player, TeamColor color) {
    ItemStack chestplate = createColoredArmor(Material.LEATHER_CHESTPLATE, color == TeamColor.RED
                                                                           ? Color.RED
                                                                           : Color.BLUE);
    ItemStack leggings = createColoredArmor(Material.LEATHER_LEGGINGS, color == TeamColor.RED
                                                                       ? Color.RED
                                                                       : Color.BLUE);

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
    if (player == null) {
      return;
    }
    player.getInventory().setArmorContents(null);
    player.getInventory().clear();
    player.setExp(0);
    player.setLevel(0);
  }

  public static List<String> getFormattedMatches(List<Match> matches) {
    List<String> output = new ArrayList<>();
    if (matches == null) {
      return output;
    }

    boolean firstBlock = true;

    for (Match match : matches) {
      if (match == null || match.getPlayers() == null) {
        continue;
      }

      boolean allNull = true;
      for (MatchPlayer mp : match.getPlayers()) {
        if (mp != null) {
          allNull = false;
          break;
        }
      }
      if (allNull) {
        continue;
      }

      if (!firstBlock) {
        output.add("");
      }
      firstBlock = false;

      String type = match.getArena().getType() + "v" + match.getArena().getType();

      List<String> redPlayers = new ArrayList<>();
      List<String> bluePlayers = new ArrayList<>();
      List<String> waitingPlayers = new ArrayList<>();

      for (MatchPlayer mp : match.getPlayers()) {
        if (mp == null || mp.getPlayer() == null) {
          continue;
        }

        String name = mp.getPlayer().getName();
        if (mp.getTeamColor() == TeamColor.RED) {
          redPlayers.add(name);
        } else {
          if (mp.getTeamColor() == TeamColor.BLUE) {
            bluePlayers.add(name);
          } else {
            if (mp.getTeamColor() == null) {
              waitingPlayers.add(name);
            }
          }
        }
      }

      String timeDisplay;
      if (match.getPhase() == MatchPhase.LOBBY) {
        timeDisplay = MATCHES_LIST_WAITING.toString();
      } else {
        if (match.getPhase() == MatchPhase.STARTING || match.getPhase() == MatchPhase.CONTINUING) {
          timeDisplay = MATCHES_LIST_STARTING.replace(String.valueOf(match.getCountdown()));
        } else {
          long matchDuration = match.getArena().getType() == TWO_V_TWO
                               ? 120
                               : 300;
          long elapsedMillis = (System.currentTimeMillis() - match.getStartTime()) - match.getTotalPausedTime();
          long remainingSeconds = matchDuration - TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);

          timeDisplay = Utilities.formatTimePretty((int) remainingSeconds);
        }
      }

      if (match.getPhase() == MatchPhase.LOBBY) {
        output.add(MATCHES_LIST_LOBBY.replace(type, String.valueOf(match.getArena().getId())));
        output.add(MATCHES_LIST_WAITINGPLAYERS.replace(waitingPlayers.isEmpty()
                                                       ? "/"
                                                       : joinStrings(waitingPlayers)));
        output.add(MATCHES_LIST_STATUS.replace(timeDisplay));
      } else {
        if (match.getPhase() == MatchPhase.STARTING) {
          output.add(MATCHES_LIST_LOBBY.replace(type, String.valueOf(match.getArena().getId())));
          output.add(MATCHES_LIST_REDPLAYERS.replace(redPlayers.isEmpty()
                                                     ? "/"
                                                     : joinStrings(redPlayers)));
          output.add(MATCHES_LIST_BLUEPLAYERS.replace(bluePlayers.isEmpty()
                                                      ? "/"
                                                      : joinStrings(bluePlayers)));
          output.add(MATCHES_LIST_STATUS.replace(timeDisplay));
        } else {
          output.add(MATCHES_LIST_MATCH.replace(type, String.valueOf(match.getArena().getId())));
          output.add(MATCHES_LIST_RESULT.replace(
              String.valueOf(match.getScoreRed()),
              String.valueOf(match.getScoreBlue()),
              MATCHES_LIST_TIMELEFT.replace(timeDisplay))
          );
          output.add(MATCHES_LIST_REDPLAYERS.replace(redPlayers.isEmpty()
                                                     ? "/"
                                                     : joinStrings(redPlayers)));
          output.add(MATCHES_LIST_BLUEPLAYERS.replace(bluePlayers.isEmpty()
                                                      ? "/"
                                                      : joinStrings(bluePlayers)));
        }
      }
    }
    return output;
  }

  private static String joinStrings(List<String> list) {
    if (list == null || list.isEmpty()) {
      return "/";
    }
    StringJoiner joiner = new StringJoiner(", ");
    for (String s : list) {
      if (s != null) {
        joiner.add(s);
      }
    }
    return joiner.toString();
  }

  public static void displayGoalMessage(Player player, String style, boolean ownGoal,
                                        boolean isHatTrick, boolean isViewerScorer,
                                        String scorerName, String assistText,
                                        String teamColorText, double distance,
                                        Match match, Logger logger, Plugin plugin) {
    String distanceString = String.format("%.0f", distance);
    String redTeam = RED.toString(), blueTeam = BLUE.toString();

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

  private static void displayEpicGoal(Player player, boolean ownGoal, boolean isHatTrick,
                                      boolean isViewerScorer, String scorerName, String assistText,
                                      String teamColorText, String distance, String redTeam,
                                      String blueTeam, Match match, Logger logger, Plugin plugin) {
    String initialTitle = ownGoal
                          ? GM_EPIC_TITLE_1.toString()
                          : isHatTrick
                            ? GM_EPIC_TITLE_1_HATTY.toString()
                            : GM_EPIC_TITLE_1_GOAL.toString();

    String initialSubtitle = ownGoal
                             ? GM_EPIC_SUBTITLE_1.toString()
                             : isViewerScorer
                               ? GM_EPIC_SUBTITLE_1_SCORER.toString()
                               : GM_EPIC_SUBTITLE_1_OTHER.toString();

    logger.title(player, initialTitle, initialSubtitle, 5, 35, 10);

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      String secondTitle = ownGoal
                           ? GM_EPIC_TITLE_2.replace(teamColorText)
                           : GM_EPIC_TITLE_2_GOAL.replace(teamColorText);

      String secondSubtitle = GM_EPIC_SUBTITLE_2
          .replace(
              scorerName,
              assistText.isEmpty()
              ? ""
              : assistText
          );

      logger.title(player, secondTitle, secondSubtitle, 0, 35, 10);
    }, 40L);

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      String thirdTitle = GM_EPIC_TITLE_3.replace(distance);

      String thirdSubtitle = GM_EPIC_SUBTITLE_3.replace(
          redTeam,
          String.valueOf(match.getScoreRed()),
          String.valueOf(match.getScoreBlue()),
          blueTeam
      );

      logger.title(player, thirdTitle, thirdSubtitle, 0, 30, 10);
    }, 80L);
  }

  private static void displaySimpleGoal(Player player, boolean ownGoal,
                                        String scorerName, String distance,
                                        Logger logger) {
    String title = ownGoal
                   ? GM_SIMPLE_TITLE.toString()
                   : GM_SIMPLE_TITLE_GOAL.toString();

    String subtitle = GM_SIMPLE_SUBTITLE.replace(scorerName, distance);

    logger.title(player, title, subtitle, 5, 40, 10);
  }

  private static void displayMinimalGoal(Player player, boolean ownGoal,
                                         String scorerName, String redTeam,
                                         String blueTeam, Match match, Logger logger) {
    logger.sendActionBar(player, ownGoal
                                 ? GM_MINIMAL_OWN
                                 : GM_MINIMAL_GOAL,
        scorerName, redTeam,
        String.valueOf(match.getScoreRed()),
        String.valueOf(match.getScoreBlue()),
        blueTeam
    );
  }

  private static void displayDefaultGoal(Player player, boolean ownGoal,
                                         boolean isHatTrick, boolean isViewerScorer,
                                         String scorerName, String assistText,
                                         String teamColorText, String distance,
                                         String redTeam, String blueTeam,
                                         Match match, Logger logger) {
    String title = ownGoal
                   ? GM_DEFAULT_TITLE_OWN.toString()
                   : isHatTrick
                     ? GM_DEFAULT_TITLE_HATTY.toString()
                     : isViewerScorer
                       ? GM_DEFAULT_TITLE_SCORER.toString()
                       : GM_DEFAULT_TITLE_GOAL.toString();

    String subtitle = ownGoal
                      ? GM_DEFAULT_SUBTITLE_OWN.replace(scorerName, teamColorText)
                      : GM_DEFAULT_SUBTITLE_GOAL.replace(scorerName, distance, assistText.isEmpty()
                                                                               ? ""
                                                                               : assistText);

    logger.title(player, title, subtitle, 10, 50, 10);

    logger.sendActionBar(player, GM_DEFAULT_ACTIONBAR,
        redTeam, String.valueOf(match.getScoreRed()),
        String.valueOf(match.getScoreBlue()), blueTeam
    );
  }

  public static boolean isPlayerOffline(MatchPlayer matchPlayer) {
    return matchPlayer == null
           || matchPlayer.getPlayer() == null
           || !matchPlayer.getPlayer().isOnline();
  }

  private static UUID getPlayerUUID(MatchPlayer matchPlayer) {
    return (matchPlayer != null && matchPlayer.getPlayer() != null)
           ? matchPlayer.getPlayer().getUniqueId()
           : null;
  }

  private static CompletableFuture<String> fetchPrefixedName(UUID uuid, MatchPlayer matchPlayer, Utilities utilities) {
    if (uuid != null && matchPlayer != null && matchPlayer.getPlayer() != null) {
      return utilities.getPrefixedName(uuid, matchPlayer.getPlayer().getName());
    }
    return CompletableFuture.completedFuture(null);
  }

  private static boolean isHatTrickGoal(MatchSystem.ScoringResult result) {
    return !result.isOwnGoal()
           && result.getScorer() != null
           && result.getScorer().getPlayer() != null
           && result.getScorer().getGoals() > 0
           && result.getScorer().getGoals() % 3 == 0;
  }

  private static double calculateScoringDistance(MatchSystem.ScoringResult result, Location goalLoc) {
    if (result.getScorer() != null && result.getScorer().getPlayer() != null) {
      return result.getScorer().getPlayer().getLocation().distance(goalLoc);
    }
    return 0;
  }

  private static String getGoalMessageStyle(PlayerSettings settings) {
    if (settings != null && isValidGoalMessage(settings.getGoalMessage())) {
      return settings.getGoalMessage();
    }
    return "default";
  }

  public static void updateMatchScore(Match match, TeamColor scoringTeam) {
    if (scoringTeam == TeamColor.RED) {
      match.setScoreRed(match.getScoreRed() + 1);
    } else {
      match.setScoreBlue(match.getScoreBlue() + 1);
    }
    match.setPauseStartTime(System.currentTimeMillis());
    if (match.getCube() != null) {
      match.getCube().setHealth(0);
    }
    match.setCube(null);
  }

  public static MatchSystem.ScoringResult determineScoringPlayers(Match match, TeamColor scoringTeam) {
    MatchPlayer scorer = match.getLastTouch();
    MatchPlayer assister = null;
    boolean ownGoal = false;
    boolean shouldCountStats = match.getArena().getType() != TWO_V_TWO;

    if (scorer == null) {
      return new MatchSystem.ScoringResult(null, null,
          false, scoringTeam);
    }

    MatchPlayer secondLastTouch = match.getSecondLastTouch();

    if (scorer.getTeamColor() != scoringTeam) {
      if (secondLastTouch != null && secondLastTouch.getTeamColor() == scoringTeam) {
        scorer = secondLastTouch;
      } else {
        ownGoal = true;
        if (shouldCountStats) {
          scorer.incrementOwnGoals();
        }
      }
    }

    if (!ownGoal && secondLastTouch != null
        && secondLastTouch.getTeamColor() == scoringTeam
        && !scorer.equals(secondLastTouch)) {
      assister = secondLastTouch;
      if (shouldCountStats) {
        assister.incrementAssists();
      }
    }

    if (!ownGoal && shouldCountStats) {
      scorer.incrementGoals();
    }

    return new MatchSystem.ScoringResult(scorer, assister, ownGoal, scoringTeam);
  }

  public static void awardCreditsForGoal(MatchSystem.ScoringResult result, Logger logger, FCManager fcManager) {
    MatchPlayer scorer = result.getScorer();
    MatchPlayer assister = result.getAssister();

    if (scorer != null && scorer.getPlayer() != null && scorer.getPlayer().isOnline()) {
      Player scoringPlayer = scorer.getPlayer();
      logger.send(scoringPlayer, MATCH_SCORE_CREDITS);
      fcManager.getEconomy().depositPlayer(scoringPlayer, 10);

      if (scorer.getGoals() > 0 && scorer.getGoals() % 3 == 0) {
        logger.send(scoringPlayer, MATCH_SCORE_HATTRICK);
        fcManager.getEconomy().depositPlayer(scoringPlayer, 100);
      }
    }

    if (assister != null && assister.getPlayer() != null && assister.getPlayer().isOnline()) {
      Player assistingPlayer = assister.getPlayer();
      logger.send(assistingPlayer, MATCH_ASSIST_CREDITS);
      fcManager.getEconomy().depositPlayer(assistingPlayer, 5);
    }
  }

  public static Location getGoalLocation(Match match, TeamColor scoringTeam) {
    return (scoringTeam == TeamColor.RED)
           ? match.getArena().getBlueSpawn()
           : match.getArena().getRedSpawn();
  }

  public static void playGoalEffects(Match match, Location goalLoc, FCManager fcManager) {
    for (MatchPlayer matchPlayer : match.getPlayers()) {
      if (isPlayerOffline(matchPlayer)) {
        continue;
      }

      Player player = matchPlayer.getPlayer();

      PlayerSettings settings = fcManager.getPlayerSettings(player);
      if (settings != null && settings.isGoalSoundEnabled()) {
        player.playSound(player.getLocation(), settings.getGoalSound(), 1, 1);
      }

      player.playEffect(goalLoc, Effect.EXPLOSION_HUGE, null);

      double distanceToGoal = player.getLocation().distance(goalLoc);
      if (distanceToGoal <= 30) {
        Vector launchDir = player.getLocation().toVector()
            .subtract(goalLoc.toVector())
            .normalize()
            .setY(0.5)
            .multiply(1.5);
        player.setVelocity(launchDir);
      }
    }
  }

  public static void broadcastGoalMessage(Match match, MatchSystem.ScoringResult result,
                                          Location goalLoc, Utilities utilities,
                                          FCManager fcManager, Logger logger) {
    UUID scorerUUID = getPlayerUUID(result.getScorer());
    UUID assisterUUID = getPlayerUUID(result.getAssister());

    CompletableFuture<String> scorerPrefixFuture = fetchPrefixedName(scorerUUID, result.getScorer(), utilities);
    CompletableFuture<String> assisterPrefixFuture = fetchPrefixedName(assisterUUID, result.getAssister(), utilities);

    scorerPrefixFuture.thenCombine(assisterPrefixFuture,
            (scorerName, assisterName) -> new String[]{scorerName, assisterName})
        .thenAccept(names -> {
          String prefixedScorer = names[0] != null
                                  ? names[0]
                                  : NOBODY.toString();
          String prefixedAssister = names[1];

          Bukkit.getScheduler().runTask(fcManager.getPlugin(), () ->
              sendGoalMessages(match, result, prefixedScorer, prefixedAssister, goalLoc, fcManager, logger)
          );
        });
  }

  private static void sendGoalMessages(Match match, MatchSystem.ScoringResult result,
                                       String prefixedScorer, String prefixedAssister,
                                       Location goalLoc, FCManager fcManager, Logger logger) {
    boolean isHatTrick = isHatTrickGoal(result);
    String teamColorText = result.getScoringTeam() == TeamColor.RED
                           ? RED.toString()
                           : BLUE.toString();

    double distance = calculateScoringDistance(result, goalLoc);
    String assistText = prefixedAssister != null
                        ? GM_ASSISTS_TEXT.replace(prefixedAssister)
                        : "";

    String goalMessage = result.isOwnGoal()
                         ? MATCH_SCORE_OWN_GOAL_ANNOUNCE.replace(prefixedScorer, teamColorText)
                         : MATCH_GOAL.replace(
                             isHatTrick
                             ? MATCH_HATTRICK.toString()
                             : MATCH_GOALLL.toString(),
                             prefixedScorer,
                             teamColorText,
                             String.format("%.0f", distance),
                             assistText
                         );

    String goalMessageStyle = "default";
    if (result.getScorer() != null && result.getScorer().getPlayer() != null) {
      PlayerSettings scorerSettings = fcManager.getPlayerSettings(result.getScorer().getPlayer());
      goalMessageStyle = getGoalMessageStyle(scorerSettings);
    }

    for (MatchPlayer matchPlayer : match.getPlayers()) {
      if (isPlayerOffline(matchPlayer)) {
        continue;
      }

      Player player = matchPlayer.getPlayer();
      boolean isViewerScorer = matchPlayer.equals(result.getScorer());

      displayGoalMessage(player, goalMessageStyle, result.isOwnGoal(), isHatTrick,
          isViewerScorer, prefixedScorer, assistText, teamColorText,
          distance, match, logger, fcManager.getPlugin()
      );

      logger.send(player, goalMessage);
      logger.send(player, MATCH_SCORE_STATS,
          String.valueOf(match.getScoreRed()),
          String.valueOf(match.getScoreBlue())
      );
    }
  }

  public static void prepareMatchContinuation(Match match, ScoreManager scoreboardManager) {
    match.setPhase(MatchPhase.CONTINUING);
    match.setCountdown(5);
    match.setTick(0);
    scoreboardManager.updateScoreboard(match);
  }
}