package io.github.divinerealms.footcube.physics.listeners;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.DEBUG_ON_MS;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.PLAYER_HEAD_LEVEL;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_HIT_DEBUG;

public class PlayerMovementListener implements Listener {
  private final PhysicsData data;
  private final PhysicsSystem system;
  private final Logger logger;

  public PlayerMovementListener(FCManager fcManager) {
    this.data = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
    this.logger = fcManager.getLogger();
  }

  /**
   * Updates player speed tracking within the physics system based on movement events.
   * <p>Called whenever a player moves, this method calculates instantaneous player
   * velocity and records it for use in subsequent physics calculations (e.g. impact power).</p>
   *
   * @param event the {@link PlayerMoveEvent} triggered on any player movement
   */
  @EventHandler
  public void playerMove(PlayerMoveEvent event) {
    long start = System.nanoTime();
    try {
      Location to = event.getTo(), from = event.getFrom();
      // Skip if no movement occurred.
      if (to.getX() == from.getX() && to.getY() == from.getY() && to.getZ() == from.getZ()) {
        return;
      }

      Player player = event.getPlayer();
      if (system.notAllowedToInteract(player) || system.isAFK(player)) {
        return;
      }
      UUID playerId = player.getUniqueId();

      // Compute normalized velocity components.
      double dx = to.getX() - from.getX();
      double dy = to.getY() - from.getY();
      double dz = to.getZ() - from.getZ();
      double scaledDy = dy / PLAYER_HEAD_LEVEL;
      double speed = Math.sqrt(dx * dx + scaledDy * scaledDy + dz * dz);

      // Record recent motion and mark player as active.
      system.recordPlayerAction(player);
      data.getSpeed().put(playerId, speed);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) {
        logger.send(PERM_HIT_DEBUG, "{prefix-admin}&dPlayerMovementListener &ftook &e" + ms + "ms");
      }
    }
  }
}
