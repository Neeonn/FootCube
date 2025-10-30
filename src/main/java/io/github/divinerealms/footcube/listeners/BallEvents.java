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
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * The {@code BallEvents} class listens for various game events and implements
 * custom behavior for interactions involving specific physics-enabled entities,
 * namely {@code Slime} instances. It processes entity damage, player interactions,
 * and movement events, applying custom mechanics in a physics-oriented system.
 * This class is used within the Footcube plugin to manage player interactions
 * with physics-enabled entities while ensuring proper authorization, reducing event
 * abuse, and logging performance information for debugging purposes.
 * Implements the {@link Listener} interface, enabling it to respond to Bukkit
 * events.
 * The primary functions include:
 * - Disabling damage for certain tracked entities
 * - Enabling hit detection and custom mechanics
 * - Tracking player interactions and movement
 * Performance-critical operations are logged if execution exceeds a threshold.
 */
public class BallEvents implements Listener {
  private final FCManager fcManager;
  private final Physics physics;
  private final Organization org;
  private final Logger logger;
  private final BukkitScheduler scheduler;

  private static final String PERM_HIT_DEBUG = "footcube.admin.hitdebug";

  public BallEvents(FCManager fcManager) {
    this.fcManager = fcManager;
    this.physics = fcManager.getPhysics();
    this.org = fcManager.getOrg();
    this.logger = fcManager.getLogger();
    this.scheduler = fcManager.getScheduler();
  }

  /**
   * Listens for the {@link EntityDamageEvent} and cancels the event if the damaged entity is a {@code Slime}
   * that exists in the physics cube set.
   * This method is primarily used to prevent damage to specific {@code Slime} entities tracked by the
   * physics system. Additionally, it logs a warning if the execution time of this event handler exceeds 1 millisecond.
   *
   * @param event the {@code EntityDamageEvent} triggered when an entity takes damage
   */
  @EventHandler
  public void disableDamage(EntityDamageEvent event) {
    long start = System.nanoTime();
    try {
      if (event.getEntity() instanceof Slime && physics.getCubes().contains((Slime) event.getEntity()))
        event.setCancelled(true);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "BallEvents#disableDamage() took " + ms + "ms");
    }
  }

  /**
   * Listens for the {@link EntityDamageByEntityEvent} and processes hit detection logic when certain
   * conditions are met. Specifically, it detects interactions where a player attacks a {@code Slime} that
   * is registered within the physics system, cancels the event, and handles custom logic such as applying
   * kick mechanics to the slime or preventing unauthorized interactions.
   * This method also tracks player interactions, plays sounds, and logs execution time
   * if the operation takes longer than 1 millisecond.
   *
   * @param event the {@code EntityDamageByEntityEvent} triggered when an entity is damaged by another entity
   */
  @EventHandler
  public void hitDetection(EntityDamageByEntityEvent event) {
    long start = System.nanoTime();
    try {
      if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
      if (!(event.getEntity() instanceof Slime)) return;

      Slime cube = (Slime) event.getEntity();
      if (!physics.getCubes().contains(cube)) return;

      if (!(event.getDamager() instanceof Player)) return;
      Player player = (Player) event.getDamager();

      event.setCancelled(true);

      if (player.getGameMode() == GameMode.CREATIVE) { cube.setHealth(0); logger.send(player, Lang.CUBE_CLEAR.replace(null)); return; }
      if (PhysicsUtil.notAllowedToInteract(player)) return;

      KickResult kickResult = PhysicsUtil.calculateKickPower(player);
      boolean onCooldown = !PhysicsUtil.canHitBall(player);

      UUID playerId = player.getUniqueId();
      if (physics.getCubeHits().contains(playerId)) PhysicsUtil.showHits(player, kickResult);
      if (onCooldown) return;

      physics.getBallHitCooldowns().put(playerId, System.currentTimeMillis());
      PhysicsUtil.recordPlayerAction(player);
      org.ballTouch(player, TouchType.HIT);

      Location playerLocation = player.getLocation();
      Vector kick = playerLocation.getDirection().normalize().multiply(kickResult.getFinalKickPower()).setY(PhysicsUtil.KICK_VERTICAL_BOOST);

      PhysicsUtil.queueHit(cube, kick);
      PhysicsUtil.queueSound(cube.getLocation(), Sound.SLIME_WALK, 0.75F, 1.0F);

      scheduler.runTask(fcManager.getPlugin(), () -> {
        PlayerSettings settings = fcManager.getPlayerSettings(player);
        if (settings != null && settings.isKickSoundEnabled()) PhysicsUtil.queueSound(player, settings.getKickSound(), PhysicsUtil.SOUND_VOLUME, PhysicsUtil.SOUND_PITCH);
        if (physics.isHitDebugEnabled()) logger.send(PERM_HIT_DEBUG, playerLocation, 100, PhysicsUtil.onHitDebug(player, kickResult));
      });
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "BallEvents#hitDetection() took " + ms + "ms");
    }
  }

  /**
   * Listens for the {@link PlayerInteractEntityEvent} and processes interactions
   * where a player right-clicks a {@code Slime} that is part of the physics system.
   * This method verifies interaction permissions, prevents duplicate actions,
   * and applies a vertical boost to the slime entity. Additionally, it logs a
   * warning if the execution time exceeds 1 millisecond.
   *
   * @param event the {@code PlayerInteractEntityEvent} triggered when a player
   *              interacts with an entity
   */
  @EventHandler
  public void rightClick(PlayerInteractEntityEvent event) {
    long start = System.nanoTime();
    try {
      if (!(event.getRightClicked() instanceof Slime)) return;

      Player player = event.getPlayer();
      UUID playerId = player.getUniqueId();
      Slime cube = (Slime) event.getRightClicked();

      if (PhysicsUtil.notAllowedToInteract(player)) return;
      if (!physics.getCubes().contains(cube)) return;
      if (physics.getKicked().containsKey(playerId)) return;

      physics.getKicked().put(playerId, System.currentTimeMillis());
      PhysicsUtil.recordPlayerAction(player);
      org.ballTouch(player, TouchType.HIT);

      double verticalBoost = PhysicsUtil.CUBE_JUMP_RIGHT_CLICK;
      PhysicsUtil.queueHit(cube, new Vector(0, verticalBoost, 0));
      PhysicsUtil.queueSound(cube.getLocation());
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "BallEvents#rightClick() took " + ms + "ms");
    }
  }

  /**
   * Listens for the {@link PlayerMoveEvent} and handles player movement logic.
   * This method calculates the speed of the player based on movement between the
   * previous and current locations, checks for permission to interact, and updates
   * the physics system accordingly. It also logs execution time if the method takes
   * longer than 1 millisecond.
   *
   * @param event the {@code PlayerMoveEvent} triggered when a player moves
   */
  @EventHandler
  public void playerMove(PlayerMoveEvent event) {
    long start = System.nanoTime();
    try {
      Location to = event.getTo(), from = event.getFrom();
      if (to.getX() == from.getX() && to.getY() == from.getY() && to.getZ() == from.getZ()) return;

      Player player = event.getPlayer();
      if (PhysicsUtil.notAllowedToInteract(player) || PhysicsUtil.isAFK(player)) return;

      double dx = to.getX() - from.getX();
      double dy = to.getY() - from.getY();
      double dz = to.getZ() - from.getZ();
      double scaledDy = dy / PhysicsUtil.PLAYER_HEAD_LEVEL;
      double speed = Math.sqrt(dx * dx + scaledDy * scaledDy + dz * dz);

      PhysicsUtil.recordPlayerAction(player);
      physics.getSpeed().put(player.getUniqueId(), speed);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "BallEvents#playerMove() took " + ms + "ms");
    }
  }
}
