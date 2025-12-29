package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import org.bukkit.entity.Player;

import java.util.*;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;

/**
 * Handles player-specific updates like charge recovery.
 * Can be run at a lower frequency (e.g., 2-5 ticks) to save CPU.
 */
public class PlayerUpdateTask extends BaseTask {
  private final PhysicsData data;
  private final Set<UUID> playersToRemove = new HashSet<>();

  public PlayerUpdateTask(FCManager fcManager) {
    super(fcManager, "PlayerUpdate", EXP_UPDATE_INTERVAL_TICKS, false);
    this.data = fcManager.getPhysicsData();
  }

  @Override
  protected void kaboom() {
    Map<UUID, Double> charges = data.getCharges();
    if (charges.isEmpty()) {
      return;
    }

    playersToRemove.clear();
    Set<Player> onlinePlayers = fcManager.getCachedPlayers();

    Map<UUID, Player> onlinePlayerMap = new HashMap<>(onlinePlayers.size());
    for (Player player : onlinePlayers) {
      if (player != null) {
        onlinePlayerMap.put(player.getUniqueId(), player);
      }
    }

    for (Map.Entry<UUID, Double> entry : charges.entrySet()) {
      UUID uuid = entry.getKey();
      Player player = onlinePlayerMap.get(uuid);
      if (player == null) {
        playersToRemove.add(uuid);
        continue;
      }

      double currentCharge = entry.getValue();
      double recoveredCharge = CHARGE_BASE_VALUE -
                               (CHARGE_BASE_VALUE - currentCharge) * CHARGE_RECOVERY_RATE;
      entry.setValue(recoveredCharge);

      player.setExp((float) recoveredCharge);
    }

    if (!playersToRemove.isEmpty()) {
      for (UUID uuid : playersToRemove) {
        charges.remove(uuid);
      }
    }
  }
}