package io.github.divinerealms.footcube.matchmaking.ban;

import static io.github.divinerealms.footcube.configs.Lang.BAN_REMAINING;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.utils.Logger;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.bukkit.entity.Player;

public class BanManager {

  private final Logger logger;
  @Getter
  private final Map<UUID, Long> bannedPlayers = new ConcurrentHashMap<>();

  public BanManager(FCManager fcManager) {
    this.logger = fcManager.getLogger();
  }

  public boolean isBanned(Player player) {
    if (!bannedPlayers.containsKey(player.getUniqueId())) {
      return false;
    }

    long now = System.currentTimeMillis();
    long banTime = bannedPlayers.get(player.getUniqueId());

    if (now < banTime) {
      long secondsLeft = (banTime - now) / 1000L;
      logger.send(player, BAN_REMAINING, player.getDisplayName(),
          Utilities.formatTime(secondsLeft));
      return true;
    }

    bannedPlayers.remove(player.getUniqueId());
    return false;
  }

  public void banPlayer(Player player, long duration) {
    bannedPlayers.put(player.getUniqueId(), System.currentTimeMillis() + duration);
  }

  public void unbanPlayer(Player player) {
    bannedPlayers.remove(player.getUniqueId());
  }
}