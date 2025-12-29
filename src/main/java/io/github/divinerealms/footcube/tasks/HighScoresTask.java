package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.highscore.HighScoreManager;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HighScoresTask extends BaseTask {
  private static final int BATCH_SIZE = 50;
  private static final long UPDATE_INTERVAL = 12000L; // 10 minutes
  private final HighScoreManager highScoreManager;

  public HighScoresTask(FCManager fcManager) {
    super(fcManager, "HighScores", UPDATE_INTERVAL, true);
    this.highScoreManager = fcManager.getHighscoreManager();
  }

  @Override
  protected void kaboom() {
    if (highScoreManager.isUpdating()) {
      int batchesToProcess = highScoreManager.isHasInitialData()
                             ? 20
                             : 50;
      for (int i = 0; i < batchesToProcess && !highScoreManager.isUpdateComplete(); i++) {
        processBatch();
      }
      return;
    }

    startUpdateCycle();
  }

  @Override
  public void start() {
    super.start();
    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
      startUpdateCycle();

      while (highScoreManager.isUpdating() && !highScoreManager.isUpdateComplete()) {
        processBatch();
      }
    }, 20 * 2);
  }

  public void startUpdateCycle() {
    if (highScoreManager.isUpdating()) {
      logger.info("&e! &d" + getTaskName() + " &6update already in progress.");
      return;
    }

    highScoreManager.startUpdate();
    int totalPlayers = highScoreManager.getParticipants().length;
    logger.info("&a✔ &2Started &d" + getTaskName() + " &2update (&e" + totalPlayers + " &2players to process)");
  }

  private void processBatch() {
    List<CompletableFuture<Void>> nameFutures = highScoreManager.processBatch(BATCH_SIZE);

    if (!nameFutures.isEmpty()) {
      CompletableFuture.allOf(nameFutures.toArray(new CompletableFuture[0])).thenRun(this::checkCompletion);
    } else {
      checkCompletion();
    }
  }

  private void checkCompletion() {
    Bukkit.getScheduler().runTask(plugin, () -> {
      if (highScoreManager.isUpdateComplete() && highScoreManager.isUpdating()) {
        highScoreManager.finishUpdate();
        logger.info("&a✔ &d" + getTaskName() + " &2update completed!");
      }
    });
  }
}
