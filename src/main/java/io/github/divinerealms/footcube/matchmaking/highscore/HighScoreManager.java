package io.github.divinerealms.footcube.matchmaking.highscore;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.utils.Logger;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HighScoreManager {
  private final Plugin plugin;
  private final Logger logger;
  private final Utilities utilities;
  private final PlayerDataManager playerDataManager;

  @Getter private long lastUpdate;

  public double[] bestRatings;
  public int[] mostGoals;
  public int[] mostAssists;
  public int[] mostOwnGoals;
  public int[] mostWins;
  public int[] longestStreak;

  public String[] topSkillNames;
  public String[] topGoalsNames;
  public String[] topAssistsNames;
  public String[] topOwnGoalsNames;
  public String[] topWinsNames;
  public String[] topStreakNames;

  @Getter private int lastUpdatedParticipant;
  @Getter private String[] participants;
  @Getter private boolean isUpdating, hasInitialData = false;
  private boolean finishCalled = false;

  public HighScoreManager(FCManager fcManager) {
    this.plugin = fcManager.getPlugin();
    this.logger = fcManager.getLogger();
    this.utilities = fcManager.getUtilities();
    this.playerDataManager = fcManager.getDataManager();
    initializeArrays();
  }

  private void initializeArrays() {
    bestRatings = new double[3];
    mostGoals = new int[3];
    mostAssists = new int[3];
    mostOwnGoals = new int[3];
    mostWins = new int[3];
    longestStreak = new int[3];

    String nobody = Lang.NOBODY.replace(null);
    topSkillNames = new String[]{nobody, nobody, nobody};
    topGoalsNames = new String[]{nobody, nobody, nobody};
    topAssistsNames = new String[]{nobody, nobody, nobody};
    topOwnGoalsNames = new String[]{nobody, nobody, nobody};
    topWinsNames = new String[]{nobody, nobody, nobody};
    topStreakNames = new String[]{nobody, nobody, nobody};
  }

  public void showHighScores(Player player) {
    logger.send(player, Lang.BEST_HEADER.replace(null));
    showTopCategory(player, topSkillNames, bestRatings);

    logger.send(player, Lang.BEST_GOALS.replace(null));
    showTopCategory(player, topGoalsNames, mostGoals);

    logger.send(player, Lang.BEST_ASSISTS.replace(null));
    showTopCategory(player, topAssistsNames, mostAssists);

    logger.send(player, Lang.BEST_OWN_GOALS.replace(null));
    showTopCategory(player, topOwnGoalsNames, mostOwnGoals);

    logger.send(player, Lang.BEST_WINS.replace(null));
    showTopCategory(player, topWinsNames, mostWins);

    logger.send(player, Lang.BEST_WINSTREAK.replace(null));
    showTopCategory(player, topStreakNames, longestStreak);
  }

  private void showTopCategory(Player player, String[] names, double[] values) {
    for (int i = 0; i < 3; i++) {
      logger.send(player, Lang.BEST_ENTRY.replace(new String[]{
          String.valueOf(i + 1),
          names[i],
          String.valueOf(values[i])
      }));
    }
  }

  private void showTopCategory(Player player, String[] names, int[] values) {
    for (int i = 0; i < 3; i++) {
      logger.send(player, Lang.BEST_ENTRY.replace(new String[]{
          String.valueOf(i + 1),
          names[i],
          String.valueOf(values[i])
      }));
    }
  }

  public void startUpdate() {
    File playerFolder = new File(plugin.getDataFolder(), "players");
    File[] files = playerFolder.listFiles((dir, name) -> name.endsWith(".yml"));
    participants = new String[files != null ? files.length : 0];
    for (int i = 0; i < participants.length; i++) participants[i] = files[i].getName().replace(".yml", "");

    lastUpdatedParticipant = 0;
    finishCalled = false;
    isUpdating = true;
  }

  public List<CompletableFuture<Void>> processBatch(int batchSize) {
    List<CompletableFuture<Void>> nameFutures = new ArrayList<>();
    int processed = 0;

    while (lastUpdatedParticipant < participants.length && processed < batchSize) {
      String playerName = participants[lastUpdatedParticipant++];
      PlayerData data = playerDataManager.get(playerName);
      if (data == null) { processed++; continue; }

      int matches = (int) data.get("matches");
      int wins = (int) data.get("wins");
      int ties = (int) data.get("ties");
      int goals = (int) data.get("goals");
      int assists = (int) data.get("assists");
      int ownGoals = (int) data.get("owngoals");
      int bestWinStreak = (int) data.get("bestwinstreak");

      double multiplier = 1 - Math.pow(0.9, matches);
      double goalBonus = matches > 0
          ? (goals == matches ? 1 : Math.min(1, 1 - multiplier * Math.pow(0.2, (double) goals / matches)))
          : 0.5;
      double addition = (matches > 0 && wins + ties > 0)
          ? 8 * (1 / ((100 * matches) / (wins + 0.5 * ties) / 100)) - 4
          : (matches > 0 ? -4 : 0);
      double skillLevel = Math.min(5 + goalBonus + addition * multiplier, 10);

      UUID uuid = playerDataManager.getUUID(playerName);
      if (uuid == null) { logger.info("&cUUID not found for player &b" + playerName); processed++; continue; }

      nameFutures.add(insertTop3(bestRatings, topSkillNames, skillLevel, uuid, playerName));
      nameFutures.add(insertTop3(mostGoals, topGoalsNames, goals, uuid, playerName));
      nameFutures.add(insertTop3(mostAssists, topAssistsNames, assists, uuid, playerName));
      nameFutures.add(insertTop3(mostOwnGoals, topOwnGoalsNames, ownGoals, uuid, playerName));
      nameFutures.add(insertTop3(mostWins, topWinsNames, wins, uuid, playerName));
      nameFutures.add(insertTop3(longestStreak, topStreakNames, bestWinStreak, uuid, playerName));

      processed++;
    }

    lastUpdate = System.currentTimeMillis();
    return nameFutures;
  }

  public boolean isUpdateComplete() {
    return lastUpdatedParticipant >= participants.length;
  }

  public void finishUpdate() {
    if (finishCalled) return;
    finishCalled = true;
    isUpdating = false;
    hasInitialData = true;
  }

  private CompletableFuture<Void> insertTop3(double[] array, String[] names, double value, UUID uuid, String playerName) {
    value = (double) Math.round(value * 100) / 100;
    double finalValue = value;

    return utilities.getPrefixedName(uuid, playerName).thenAccept(prefixedName -> {
      synchronized (array) {
        for (int i = 0; i < 3; i++) {
          if (finalValue > array[i]) {
            for (int j = 2; j > i; j--) {
              array[j] = array[j - 1];
              names[j] = names[j - 1];
            }

            array[i] = finalValue;
            names[i] = prefixedName;
            break;
          }
        }
      }
    });
  }

  private CompletableFuture<Void> insertTop3(int[] array, String[] names, int value, UUID uuid, String playerName) {
    return utilities.getPrefixedName(uuid, playerName).thenAccept(prefixedName -> {
      synchronized (array) {
        for (int i = 0; i < 3; i++) {
          if (value > array[i]) {
            for (int j = 2; j > i; j--) {
              array[j] = array[j - 1];
              names[j] = names[j - 1];
            }

            array[i] = value;
            names[i] = prefixedName;
            break;
          }
        }
      }
    });
  }
}