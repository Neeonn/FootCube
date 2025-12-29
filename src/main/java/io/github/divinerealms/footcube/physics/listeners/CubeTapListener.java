package io.github.divinerealms.footcube.physics.listeners;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.touch.CubeTouchInfo;
import io.github.divinerealms.footcube.physics.touch.CubeTouchType;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.CUBE_JUMP_RIGHT_CLICK;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.DEBUG_ON_MS;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_HIT_DEBUG;

public class CubeTapListener implements Listener {
  private final FCManager fcManager;
  private final Logger logger;
  private final PhysicsData data;
  private final PhysicsSystem system;

  public CubeTapListener(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.data = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
  }

  /**
   * Handles right-click interactions with cube entities.
   * <p>When a player right-clicks a tracked {@link Slime}, applies a vertical boost
   * and triggers sound effects to simulate a lighter form of interaction than a kick.</p>
   *
   * @param event the {@link PlayerInteractEntityEvent} fired when a player interacts with an entity
   */
  @EventHandler
  public void rightClick(PlayerInteractEntityEvent event) {
    long start = System.nanoTime();
    try {
      if (!(event.getRightClicked() instanceof Slime)) {
        return;
      }
      if (!data.getCubes().contains((Slime) event.getRightClicked())) {
        return;
      }

      Slime cube = (Slime) event.getRightClicked();
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();

      // Prevent AFK or unauthorized players from interacting.
      if (system.notAllowedToInteract(player)) {
        return;
      }

      // Enforce cooldown.
      Map<CubeTouchType, CubeTouchInfo> touches = data.getLastTouches().get(playerId);
      if (touches != null && touches.containsKey(CubeTouchType.RISE)) {
        return;
      }

      // Apply vertical boost and play sound.
      Vector previousVelocity = cube.getVelocity().clone();
      double newY = Math.max(previousVelocity.getY(), CUBE_JUMP_RIGHT_CLICK);
      cube.setVelocity(previousVelocity.setY(newY));

      // Mark player action to prevent spamming.
      data.getLastTouches().computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
          .put(CubeTouchType.RISE, new CubeTouchInfo(System.currentTimeMillis(), CubeTouchType.RISE));
      data.getRaised().put(cube.getUniqueId(), System.currentTimeMillis());

      system.recordPlayerAction(player);
      fcManager.getMatchManager().kick(player);

      // Play feedback sound.
      system.queueSound(cube.getLocation());
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) {
        logger.send(PERM_HIT_DEBUG, "{prefix-admin}&bCubeTapListener &ftook &e" + ms + "ms");
      }
    }
  }
}