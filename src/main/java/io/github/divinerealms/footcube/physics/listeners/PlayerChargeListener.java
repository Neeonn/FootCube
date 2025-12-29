package io.github.divinerealms.footcube.physics.listeners;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.UUID;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.DEBUG_ON_MS;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_HIT_DEBUG;

public class PlayerChargeListener implements Listener {
  private final Logger logger;

  private final PhysicsData data;
  private final PhysicsSystem system;

  public PlayerChargeListener(FCManager fcManager) {
    this.logger = fcManager.getLogger();

    this.data = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
  }

  /**
   * Calculates and manages charge buildup when players toggle sneaking.
   * When sneaking starts, initializes a charge state; when sneaking stops,
   * resets experience and removes the charge.
   *
   * @param event the {@link PlayerToggleSneakEvent} triggered when sneaking state changes
   */
  @EventHandler
  public void playerChargeCalculator(PlayerToggleSneakEvent event) {
    long start = System.nanoTime();
    try {
      Player player = event.getPlayer();
      if (system.notAllowedToInteract(player)) {
        return;
      }
      UUID playerId = player.getUniqueId();

      if (event.isSneaking()) {
        // Begin charging.
        data.getCharges().put(playerId, 0D);
        system.recordPlayerAction(player);
      } else {
        // Reset when released.
        player.setExp(0);
        data.getCharges().remove(playerId);
      }
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) {
        logger.send(PERM_HIT_DEBUG, "{prefix-admin}&dPlayerChargeListener &ftook &e" + ms + "ms");
      }
    }
  }
}