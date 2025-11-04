package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.*;
import io.github.divinerealms.footcube.utils.KickResult;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Handles in-game events related to player and cube (Slime entity) interactions
 * within the Footcube physics system. This listener coordinates how players
 * interact with physics-enabled entities, applying custom logic for kicks,
 * hits, and movement-based mechanics.
 *
 * <p>Responsibilities include:</p>
 * <ul>
 * <li>Preventing damage to tracked cube entities.</li>
 * <li>Handling player kicks and right-click actions.</li>
 * <li>Tracking player motion and updating interaction speed.</li>
 * <li>Logging performance if any handler exceeds execution thresholds.</li>
 * </ul>
 *
 * <p>This listener is performance-monitored; any method taking longer than 1ms
 * logs a diagnostic message for profiling and debugging.</p>
 */
public class BallEvents implements Listener {
  private final FCManager fcManager;
  private final Physics physics;
  private final Organization org;
  private final Logger logger;
  private final PhysicsUtil physicsUtil;
  private final BukkitScheduler scheduler;

  private static final String PERM_HIT_DEBUG = "footcube.admin.hitdebug";

  public BallEvents(FCManager fcManager) {
    this.fcManager = fcManager;
    this.physics = fcManager.getPhysics();
    this.org = fcManager.getOrg();
    this.logger = fcManager.getLogger();
    this.physicsUtil = fcManager.getPhysicsUtil();
    this.scheduler = fcManager.getScheduler();
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
      if (event.getEntity() instanceof Slime && physics.getCubes().contains((Slime) event.getEntity()))
        event.setCancelled(true);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "BallEvents#disableDamage() took " + ms + "ms");
    }
  }

  /**
   * Handles custom hit detection for cube entities when attacked by players.
   * <p>Replaces standard attack behavior with physics-driven logic such as applying
   * kick velocity, cooldown tracking, and custom hit effects.</p>
   *
   * @param event the {@link EntityDamageByEntityEvent} triggered when one entity damages another
   */
  @EventHandler(priority = EventPriority.HIGHEST)
  public void hitDetection(EntityDamageByEntityEvent event) {
    long start = System.nanoTime();
    try {
      if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
      if (!(event.getEntity() instanceof Slime)) return;

      Slime cube = (Slime) event.getEntity();
      if (!physics.getCubes().contains(cube)) return;

      if (!(event.getDamager() instanceof Player)) return;
      Player player = (Player) event.getDamager();

      // Cancel vanilla damage to allow custom physics response.
      event.setCancelled(true);

      // Creative players can remove cubes directly.
      if (player.getGameMode() == GameMode.CREATIVE) { cube.setHealth(0); logger.send(player, Lang.CUBE_CLEAR.replace(null)); return; }

      // Prevent AFK or unauthorized players from interacting.
      if (physicsUtil.notAllowedToInteract(player)) return;

      // Calculate kick power and enforce cooldown.
      KickResult kickResult = physicsUtil.calculateKickPower(player);
      boolean onCooldown = !physicsUtil.canHitBall(player);

      UUID playerId = player.getUniqueId();
      if (physics.getCubeHits().contains(playerId)) physicsUtil.showHits(player, kickResult);
      if (onCooldown) return;

      // Register player hit cooldown and record interaction.
      physics.getBallHitCooldowns().put(playerId, System.currentTimeMillis());
      physicsUtil.recordPlayerAction(player);
      org.ballTouch(player, TouchType.HIT);

      // Compute final kick direction and apply impulse.
      Location playerLocation = player.getLocation();
      Vector kick = playerLocation.getDirection().normalize().multiply(kickResult.getFinalKickPower()).setY(PhysicsUtil.KICK_VERTICAL_BOOST);

      physicsUtil.queueHit(cube, kick);
      physicsUtil.queueSound(cube.getLocation(), Sound.SLIME_WALK, 0.75F, 1.0F);

      // Schedule async post-processing for player sound feedback and debug info.
      scheduler.runTask(fcManager.getPlugin(), () -> {
        PlayerSettings settings = fcManager.getPlayerSettings(player);
        if (settings != null && settings.isKickSoundEnabled()) physicsUtil.queueSound(player, settings.getKickSound(), PhysicsUtil.SOUND_VOLUME, PhysicsUtil.SOUND_PITCH);
        if (physics.isHitDebugEnabled()) logger.send(PERM_HIT_DEBUG, playerLocation, 100, physicsUtil.onHitDebug(player, kickResult));
      });
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "BallEvents#hitDetection() took " + ms + "ms");
    }
  }

  /**
   * Handles right-click interactions with cube entities.
   * <p>When a player right-clicks a tracked {@link Slime}, applies a vertical boost
   * and triggers sound effects to simulate a lighter form of interaction than a kick.</p>
   *
   * @param event the {@link PlayerInteractEntityEvent} fired when a player interacts with an entity
   */
  @EventHandler(priority = EventPriority.HIGH)
  public void rightClick(PlayerInteractEntityEvent event) {
    long start = System.nanoTime();
    try {
      if (!(event.getRightClicked() instanceof Slime)) return;

      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      Slime cube = (Slime) event.getRightClicked();

      if (physicsUtil.notAllowedToInteract(player)) return;
      if (!physics.getCubes().contains(cube)) return;
      if (physics.getKicked().containsKey(playerId)) return;

      // Mark player action to prevent spamming.
      physics.getKicked().put(playerId, System.currentTimeMillis());
      physicsUtil.recordPlayerAction(player);
      org.ballTouch(player, TouchType.HIT);

      // Apply small upward force and sound feedback.
      double verticalBoost = PhysicsUtil.CUBE_JUMP_RIGHT_CLICK;
      physicsUtil.queueHit(cube, new Vector(0, verticalBoost, 0));
      physicsUtil.queueSound(cube.getLocation());
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "BallEvents#rightClick() took " + ms + "ms");
    }
  }

  /**
   * Updates player speed tracking within the physics system based on movement events.
   * <p>Called whenever a player moves, this method calculates instantaneous player
   * velocity and records it for use in subsequent physics calculations (e.g. impact power).</p>
   *
   * @param event the {@link PlayerMoveEvent} triggered on any player movement
   */
  @EventHandler(priority = EventPriority.LOWEST)
  public void playerMove(PlayerMoveEvent event) {
    long start = System.nanoTime();
    try {
      Location to = event.getTo(), from = event.getFrom();
      // Skip if no movement occurred.
      if (to.getX() == from.getX() && to.getY() == from.getY() && to.getZ() == from.getZ()) return;

      Player player = event.getPlayer();
      if (physicsUtil.notAllowedToInteract(player) || physicsUtil.isAFK(player)) return;

      // Compute normalized velocity components.
      double dx = to.getX() - from.getX();
      double dy = to.getY() - from.getY();
      double dz = to.getZ() - from.getZ();
      double scaledDy = dy / PhysicsUtil.PLAYER_HEAD_LEVEL;
      double speed = Math.sqrt(dx * dx + scaledDy * scaledDy + dz * dz);

      // Record recent motion and mark player as active.
      physicsUtil.recordPlayerAction(player);
      physics.getSpeed().put(player.getUniqueId(), speed);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "BallEvents#playerMove() took " + ms + "ms");
    }
  }
}
