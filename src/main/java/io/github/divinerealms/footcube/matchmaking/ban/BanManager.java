package io.github.divinerealms.footcube.matchmaking.ban;

import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BanManager {
  @Getter private final Map<UUID, Long> bannedPlayers = new ConcurrentHashMap<>();

  public boolean isBanned(Player player) {
    if (!bannedPlayers.containsKey(player.getUniqueId())) { return false; }
    long banTime = bannedPlayers.get(player.getUniqueId());
    if (System.currentTimeMillis() < banTime) {
      // TODO: Add a message to the player informing them about the remaining ban time.
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