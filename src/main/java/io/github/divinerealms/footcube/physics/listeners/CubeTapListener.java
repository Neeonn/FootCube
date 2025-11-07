package io.github.divinerealms.footcube.physics.listeners;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.core.TouchType;
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

public class CubeTapListener implements Listener {
  private final Organization org;
  private final Logger logger;

  private final PhysicsData data;
  private final PhysicsSystem system;

  public CubeTapListener(FCManager fcManager) {
    this.org = fcManager.getOrg();
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
  @SuppressWarnings("SuspiciousNameCombination")
  @EventHandler
  public void rightClick(PlayerInteractEntityEvent event) {
    long start = System.nanoTime();
    try {
      if (!(event.getRightClicked() instanceof Slime)) return;
      if (!data.getCubes().contains((Slime) event.getRightClicked())) return;

      Slime cube = (Slime) event.getRightClicked();
      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();

      if (system.notAllowedToInteract(player)) return;
      Map<CubeTouchType, CubeTouchInfo> touches = data.getLastTouches().computeIfAbsent(playerId, key -> new ConcurrentHashMap<>());
      if (touches.containsKey(CubeTouchType.RISE)) return;

      cube.setVelocity(cube.getVelocity().add(new Vector(0, CUBE_JUMP_RIGHT_CLICK, 0)));
      system.queueSound(cube.getLocation());

      // Mark player action to prevent spamming.
      touches.put(CubeTouchType.RISE, new CubeTouchInfo(System.currentTimeMillis(), CubeTouchType.RISE));
      system.recordPlayerAction(player);
      org.ballTouch(player, TouchType.HIT);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&bCubeTapListener &ftook &e" + ms + "ms");
    }
  }
}
