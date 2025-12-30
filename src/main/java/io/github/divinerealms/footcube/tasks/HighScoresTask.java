package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.highscore.HighScoreManager;
import org.bukkit.Bukkit;

import java.io.File;

public class HighScoresTask extends BaseTask {
  private final HighScoreManager highScoreManager;

  public HighScoresTask(FCManager fcManager) {
    super(fcManager, "HighScores", 20 * 60 * 10, true);
    this.highScoreManager = fcManager.getHighscoreManager();
  }

  @Override
  protected void kaboom() {
    if (highScoreManager.isUpdating()) {
      return;
    }

    startUpdateCycle();
  }

  @Override
  public void start() {
    super.start();
    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::startUpdateCycle, 20 * 2);
  }

  public void startUpdateCycle() {
    if (highScoreManager.isUpdating()) {
      logger.info("&e! &d" + getTaskName() + " &6update already in progress.");
      return;
    }

    int totalPlayers = 0;
    File playerFolder = new File(plugin.getDataFolder(), "players");
    File[] files = playerFolder.listFiles((dir, name) -> name.endsWith(".yml"));
    if (files != null) {
      totalPlayers = files.length;
    }

    logger.info("&a✔ &2Started &d" + getTaskName() + " &2update (&e" + totalPlayers + " &2players to process)");
    highScoreManager.startUpdate();
    logger.info("&a✔ &d" + getTaskName() + " &2update completed!");
  }
}
