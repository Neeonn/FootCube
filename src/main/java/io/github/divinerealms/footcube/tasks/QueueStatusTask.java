package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.entity.Player;

import static io.github.divinerealms.footcube.configs.Lang.*;

public class QueueStatusTask extends BaseTask {
  private final Logger logger;

  public QueueStatusTask(FCManager fcManager) {
    super(fcManager, "QueueStatus", 40, false);
    this.logger = fcManager.getLogger();
  }

  @Override
  protected void kaboom() {
    for (Match match : fcManager.getMatchData().getMatches()) {
      if (match == null || match.getPhase() != MatchPhase.LOBBY) {
        continue;
      }
      if (match.getArena() == null || match.getPlayers() == null) {
        continue;
      }

      int matchType = match.getArena().getType();
      int requiredPlayers = matchType * 2;
      String matchTypeString = matchType + "v" + matchType;

      int currentPlayers = 0;
      for (MatchPlayer mp : match.getPlayers()) {
        if (mp != null && mp.getPlayer() != null && mp.getPlayer().isOnline()) {
          currentPlayers++;
        }
      }

      String colorCode = (currentPlayers == requiredPlayers)
                         ? "&a"
                         : "&e";

      for (MatchPlayer mp : match.getPlayers()) {
        if (mp == null) {
          continue;
        }
        Player player = mp.getPlayer();
        if (player == null || !player.isOnline()) {
          continue;
        }
        logger.sendActionBar(player, QUEUE_ACTIONBAR,
            MATCHES_LIST_LOBBY.replace(
                matchTypeString, String.valueOf(match.getArena().getId())
            ),
            MATCHES_LIST_WAITING.toString(),
            colorCode + currentPlayers,
            String.valueOf(requiredPlayers)
        );
      }
    }
  }
}
