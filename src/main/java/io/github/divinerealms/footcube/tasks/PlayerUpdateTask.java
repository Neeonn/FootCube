package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;

/**
 * Handles player-specific updates like charge recovery.
 * Can be run at a lower frequency (e.g., 2-5 ticks) to save CPU.
 */
public class PlayerUpdateTask extends BaseTask {
  private final PhysicsData data;

  public PlayerUpdateTask(FCManager fcManager) {
    super(fcManager, "PlayerUpdate", EXP_UPDATE_INTERVAL_TICKS, false);
    this.data = fcManager.getPhysicsData();
  }

  @Override
  protected void execute() {
    if (data.getCharges().isEmpty()) return;

    Iterator<Map.Entry<UUID, Double>> chargesIterator = data.getCharges().entrySet().iterator();
    while (chargesIterator.hasNext()) {
      Map.Entry<UUID, Double> entry = chargesIterator.next();
      UUID uuid = entry.getKey();
      Player player = Bukkit.getPlayer(uuid);
      if (player == null) { chargesIterator.remove(); continue; }

      double currentCharge = entry.getValue();
      double recoveredCharge = CHARGE_BASE_VALUE -
          (CHARGE_BASE_VALUE - currentCharge) * CHARGE_RECOVERY_RATE;
      entry.setValue(recoveredCharge);

      player.setExp((float) recoveredCharge);
    }
  }
}