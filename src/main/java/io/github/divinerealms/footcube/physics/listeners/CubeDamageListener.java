package io.github.divinerealms.footcube.physics.listeners;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.DEBUG_ON_MS;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_HIT_DEBUG;

public class CubeDamageListener implements Listener {
  private final PhysicsData physicsData;
  private final Logger logger;

  public CubeDamageListener(FCManager fcManager) {
    this.physicsData = fcManager.getPhysicsData();
    this.logger = fcManager.getLogger();
  }

  /**
   * Cancels any damage event involving tracked cube entities.
   * <p>This ensures that physics-enabled {@link Slime} instances are not damaged
   * by players or environmental sources, preserving gameplay integrity.</p>
   *
   * @param event the {@link EntityDamageEvent} fired when any entity takes damage
   */
  @EventHandler
  public void disableDamage(EntityDamageEvent event) {
    long start = System.nanoTime();
    try {
      // Cancel all damage applied to physics cubes.
      if (event.getEntity() instanceof Slime && physicsData.getCubes().contains((Slime) event.getEntity()))
        event.setCancelled(true);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send(PERM_HIT_DEBUG, "{prefix-admin}&dCubeDamageListener &ftook &e" + ms + "ms");
    }
  }
}
