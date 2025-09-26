package io.github.divinerealms.footcube.utils;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.managers.Utilities;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HighScores {
  private final Plugin plugin;
  private final Logger logger;
  private final Utilities utilities;
  private final PlayerDataManager playerDataManager;
  private final BukkitScheduler scheduler;

  private int taskID;
  private long lastUpdate;

  double[] bestRatings;
  int[] mostGoals;
  int[] mostWins;
  int[] longestStreak;

  String[] topSkillNames;
  String[] topGoalsNames;
  String[] topWinsNames;
  String[] topStreakNames;

  private int lastUpdatedParticipant;
  private String[] participants;
  private final ArrayList<Player> waitingPlayers = new ArrayList<>();
  public boolean isUpdating;

  public HighScores(FCManager fcManager) {
    this.plugin = fcManager.getPlugin();
    this.logger = fcManager.getLogger();
    this.utilities = fcManager.getUtilManager();
    this.playerDataManager = fcManager.getDataManager();
    this.scheduler = plugin.getServer().getScheduler();
  }

  public boolean needsUpdate() {
    return lastUpdate + 600_000L < System.currentTimeMillis(); // 10 minutes
  }

  public void addWaitingPlayer(Player player) {
    if (!waitingPlayers.contains(player)) waitingPlayers.add(player);
    if (isUpdating) {
      int remaining = (participants != null ? participants.length - lastUpdatedParticipant : 0);
      logger.send(player, Lang.BEST_UPDATING.replace(new String[]{String.valueOf(remaining)}));
    } else {
      showHighScores(player);
    }
  }

  public void showHighScores(Player player) {
    logger.send(player, Lang.BEST_HEADER.replace(null));

    showTopCategory(player, topSkillNames, bestRatings);
    logger.send(player, Lang.BEST_GOALS.replace(null));
    showTopCategory(player, topGoalsNames, mostGoals);
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

  public void update() {
    bestRatings = new double[3];
    mostGoals = new int[3];
    mostWins = new int[3];
    longestStreak = new int[3];

    topSkillNames = new String[]{"---", "---", "---"};
    topGoalsNames = new String[]{"---", "---", "---"};
    topWinsNames = new String[]{"---", "---", "---"};
    topStreakNames = new String[]{"---", "---", "---"};

    File playerFolder = new File(plugin.getDataFolder(), "players");
    File[] files = playerFolder.listFiles((dir, name) -> name.endsWith(".yml"));
    participants = new String[files != null ? files.length : 0];
    for (int i = 0; i < participants.length; i++) {
      participants[i] = files[i].getName().replace(".yml", "");
    }

    lastUpdatedParticipant = 0;
    taskID = scheduler.runTaskTimerAsynchronously(plugin, this::continueUpdate, 1L, 1L).getTaskId();
  }

  public void playerUpdate(Player requester) {
    if (!isUpdating) waitingPlayers.clear();
    waitingPlayers.add(requester);
    lastUpdate = System.currentTimeMillis();
    isUpdating = true;

    update();
  }

  private void continueUpdate() {
    int processed = 0;
    List<CompletableFuture<Void>> nameFutures = new ArrayList<>();

    while (lastUpdatedParticipant < participants.length && processed < 25) {
      String playerName = participants[lastUpdatedParticipant++];
      PlayerData data = playerDataManager.get(playerName);
      if (data == null) continue;

      int matches = (int) data.get("matches");
      int wins = (int) data.get("wins");
      int ties = (int) data.get("ties");
      int goals = (int) data.get("goals");
      int bestWinStreak = (int) data.get("bestwinstreak");

      double multiplier = 1.0 - Math.pow(0.9, matches);
      double goalBonus = matches > 0
          ? (goals == matches ? 1.0 : Math.min(1.0, 1 - multiplier * Math.pow(0.2, (double) goals / matches)))
          : 0.5;
      double addition = (matches > 0 && wins + ties > 0)
          ? 8.0 * (1.0 / ((100.0 * matches) / (wins + 0.5 * ties) / 100.0)) - 4.0
          : (matches > 0 ? -4.0 : 0.0);

      double skillLevel = Math.min(5.0 + goalBonus + addition * multiplier, 10.0);

      UUID uuid = playerDataManager.getUUID(playerName);
      if (uuid == null) {
        logger.info("&cUUID not found for player &b" + playerName);
        continue;
      }

      nameFutures.add(insertTop3(bestRatings, topSkillNames, skillLevel, uuid, playerName));
      nameFutures.add(insertTop3(mostGoals, topGoalsNames, goals, uuid, playerName));
      nameFutures.add(insertTop3(mostWins, topWinsNames, wins, uuid, playerName));
      nameFutures.add(insertTop3(longestStreak, topStreakNames, bestWinStreak, uuid, playerName));

      processed++;
    }

    lastUpdate = System.currentTimeMillis();

    if (!nameFutures.isEmpty()) {
      CompletableFuture.allOf(nameFutures.toArray(new CompletableFuture[0]))
          .thenRun(this::checkFinishUpdate);
    } else {
      checkFinishUpdate();
    }
  }

  private void checkFinishUpdate() {
    if (lastUpdatedParticipant >= participants.length) finishUpdate();
  }

  private void finishUpdate() {
    scheduler.cancelTask(taskID);
    plugin.getServer().getScheduler().runTask(plugin, () -> {
      isUpdating = false;
      for (Player player : waitingPlayers) showHighScores(player);
      waitingPlayers.clear();
    });
  }

  private CompletableFuture<Void> insertTop3(double[] array, String[] names, double value, UUID uuid, String playerName) {
    value = Math.round(value * 100.0) / 100.0;
    double finalValue = value;
    return utilities.getPrefixedName(uuid, playerName).thenAccept(prefixedName -> {
      for (int i = 0; i < 3; i++) {
        if (finalValue >= array[i]) {
          for (int j = 2; j > i; j--) {
            array[j] = array[j - 1];
            names[j] = names[j - 1];
          }
          array[i] = finalValue;
          names[i] = prefixedName;
          break;
        }
      }
    });
  }

  private CompletableFuture<Void> insertTop3(int[] array, String[] names, int value, UUID uuid, String playerName) {
    return utilities.getPrefixedName(uuid, playerName).thenAccept(prefixedName -> {
      for (int i = 0; i < 3; i++) {
        if (value >= array[i]) {
          for (int j = 2; j > i; j--) {
            array[j] = array[j - 1];
            names[j] = names[j - 1];
          }
          array[i] = value;
          names[i] = prefixedName;
          break;
        }
      }
    });
  }
}
