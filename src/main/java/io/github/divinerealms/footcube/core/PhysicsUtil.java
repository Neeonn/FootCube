package io.github.divinerealms.footcube.core;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.utils.KickResult;
import io.github.divinerealms.footcube.utils.Logger;
import lombok.experimental.UtilityClass;
import net.minecraft.server.v1_8_R3.EntitySlime;
import net.minecraft.server.v1_8_R3.PathfinderGoalSelector;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftSlime;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

@UtilityClass
public class PhysicsUtil {
  private FCManager fcManager;
  private Physics physics;
  private Logger logger;
  private Plugin plugin;

  public static void initialize(FCManager manager) {
    fcManager = manager;
    physics = manager.getPhysics();
    logger = manager.getLogger();
    plugin = manager.getPlugin();
  }

  // --- Task Intervals (Ticks) ---
  public static final long PHYSICS_TASK_INTERVAL_TICKS = 1L;
  public static final long GLOW_TASK_INTERVAL_TICKS = 10L;
  public static final long CUBE_REMOVAL_DELAY_TICKS = 20L;

  // --- Slime/Entity Configuration ---
  public static final int SLIME_SIZE = 1;
  public static final int JUMP_POTION_DURATION = Integer.MAX_VALUE;
  public static final int JUMP_POTION_AMPLIFIER = -3;

  // --- Timeouts & Cooldowns (Milliseconds) ---
  public static final long KICKED_TIMEOUT_MS = 1000L;
  public static final long REGULAR_HIT_COOLDOWN = 150L;
  public static final long CHARGED_HIT_COOLDOWN = 500L;
  public static final long AFK_THRESHOLD = 60_000L;

  // --- Kick Power & Charge Settings ---
  public static final double MAX_KP = 5.0;
  public static final double SOFT_CAP_MIN_FACTOR = 0.85;
  public static final double CHARGE_MULTIPLIER = 7.0;
  public static final double BASE_POWER = 0.375;
  public static final double CHARGE_BASE_VALUE = 1.0;
  public static final double CHARGE_RECOVERY_RATE = 0.945;
  public static final double MIN_SPEED_FOR_DAMPENING = 0.5;
  public static final double MIN_SOUND_POWER = 0.15;
  public static final double KICK_VERTICAL_BOOST = 0.3;
  public static final int EXP_UPDATE_INTERVAL_TICKS = 3;

  // --- Distance & Collision Thresholds ---
  public static final double HIT_RADIUS = 1.2;
  public static final double HIT_RADIUS_SQUARED = HIT_RADIUS * HIT_RADIUS;
  public static final double MIN_RADIUS = 0.8;
  public static final double MIN_RADIUS_SQUARED = MIN_RADIUS * MIN_RADIUS;
  public static final double BOUNCE_THRESHOLD = 0.3;

  // --- Physics Multipliers ---
  public static final double BALL_TOUCH_Y_OFFSET = 1.0;
  public static final double CUBE_HITBOX_ADJUSTMENT = 1.5;
  public static final double KICK_POWER_SPEED_MULTIPLIER = 2.0;
  public static final double VELOCITY_DAMPENING_FACTOR = 0.5;
  public static final double PLAYER_SPEED_TOUCH_DIVISOR = 3.0;
  public static final double CUBE_SPEED_TOUCH_DIVISOR = 6.0;
  public static final double PROXIMITY_THRESHOLD_MULTIPLIER_SQUARED = 1.69;
  public static final double WALL_BOUNCE_FACTOR = 0.8;
  public static final double AIR_DRAG_FACTOR = 0.98;
  public static final double CUBE_JUMP_RIGHT_CLICK = 0.7;

  // --- Physics Math Thresholds ---
  public static final double VECTOR_CHANGE_THRESHOLD = 0.1;
  public static final double VERTICAL_BOUNCE_THRESHOLD = 0.05;
  public static final double TOLERANCE_VELOCITY_CHECK = 1.0E-6;

  // --- Player/Location Offsets ---
  public static final int PLAYER_HEAD_LEVEL = 2;
  public static final int PLAYER_FOOT_LEVEL = 1;

  // --- Sound Defaults ---
  public static final float SOUND_VOLUME = 0.5F;
  public static final float SOUND_PITCH = 1.0F;

  // --- Particle Defaults ---
  public static final double DISTANCE_PARTICLE_THRESHOLD_SQUARED = 32.0 * 32.0;
  public static final double PARTICLE_Y_OFFSET = 0.25;
  public static final float GENERIC_PARTICLE_OFFSET = 0.01F;
  public static final float GENERIC_PARTICLE_SPEED = 0.1F;
  public static final int GENERIC_PARTICLE_COUNT = 10;

  // --- Utility ---
  public static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

  /**
   * Adds the specified location to the sound queue for further processing.
   * If the location is not null, it creates a clone of the location and queues it in the sound queue.
   *
   * @param location The location to be added to the sound queue. Must not be null.
   */
  public static void queueSound(Location location, Sound sound, float volume, float pitch) {
    if (location == null || sound == null) return;
    physics.getSoundQueue().offer(new Physics.SoundAction(location, null, sound, volume, pitch));
  }

  /**
   * Adds a sound action to the sound queue for a specific player.
   * This method queues the specified sound to be played at the player's location with the given volume and pitch.
   * If the player or sound is null, the method exits without queuing any action.
   *
   * @param player The player at whose location the sound will be played. Must not be null.
   * @param sound The sound to be played. Must not be null.
   * @param volume The volume level of the sound. A float value where higher values indicate louder sounds.
   * @param pitch The pitch of the sound. A float value where higher values indicate a higher pitch.
   */
  public static void queueSound(Player player, Sound sound, float volume, float pitch) {
    if (player == null || sound == null) return;
    physics.getSoundQueue().offer(new Physics.SoundAction(null, player, sound, volume, pitch));
  }

  /**
   * Adds the specified location to the sound queue for further processing.
   * This method queues a sound at the given location using default sound type, volume, and pitch values.
   *
   * @param location The location where the sound will be queued. Must not be null.
   */
  public static void queueSound(Location location) {
    queueSound(location, Sound.SLIME_WALK, 0.1F, SOUND_PITCH);
  }

  /**
   * Queues a hit action for a slime object with the specified velocity and mode of applying the velocity.
   *
   * @param cube The Slime entity that is being hit. Must not be null and must be alive.
   * @param velocity The Vector defining the direction and magnitude of the hit's velocity. Must not be null.
   * @param additive If true, the velocity is added to the slime's current velocity. If false, the velocity replaces the current velocity.
   */
  public static void queueHit(Slime cube, Vector velocity, boolean additive) {
    if (cube == null || cube.isDead() || velocity == null) return;
    physics.getHitQueue().offer(new Physics.HitAction(cube, velocity, additive));
  }

  /**
   * Calculates a custom distance squared between a player (locA) and the ball (locB).
   * The calculation includes an offset to account for player height and cube size.
   */
  public static double getDistanceSquared(Location locA, Location locB) {
    long start = System.nanoTime();
    try {
      double dx = locA.getX() - locB.getX();
      double dy = (locA.getY() - PhysicsUtil.BALL_TOUCH_Y_OFFSET) - locB.getY() - PhysicsUtil.CUBE_HITBOX_ADJUSTMENT;
      if (dy < 0) dy = 0;
      double dz = locA.getZ() - locB.getZ();

      return dx * dx + dy * dy + dz * dz;
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "PhysicsUtil#getDistanceSquared() took " + ms + "ms");
    }
  }

  /**
   * Check if the player is allowed to hit the ball based on hit cooldowns.
   * @param player Player who is being checked (who kicked the ball).
   * @return Remaining cooldown in milliseconds.
   */
  public static boolean canHitBall(Player player) {
    long start = System.nanoTime();
    try {
      long now = System.currentTimeMillis();
      long cooldown = player.isSneaking() ? PhysicsUtil.CHARGED_HIT_COOLDOWN : PhysicsUtil.REGULAR_HIT_COOLDOWN;
      long lastHit = physics.getBallHitCooldowns().getOrDefault(player.getUniqueId(), 0L);

      return now - lastHit >= cooldown;
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "PhysicsUtil#canHitBall() took " + ms + "ms");
    }
  }

  /**
   * Applies the soft cap to the calculated kick power.
   * @param baseKickPower Initial kick power.
   * @return Randomized capped kick power.
   */
  private static double capKickPower(double baseKickPower) {
    long start = System.nanoTime();
    try {
      if (baseKickPower <= PhysicsUtil.MAX_KP) return baseKickPower;
      double minRandomKP = PhysicsUtil.MAX_KP * PhysicsUtil.SOFT_CAP_MIN_FACTOR;
      return PhysicsUtil.RANDOM.nextDouble(minRandomKP, PhysicsUtil.MAX_KP);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "PhysicsUtil#capKickPower() took " + ms + "ms");
    }
  }

  /**
   * Calculates the final kick power based on player speed and current charge level.
   * @param player Player who initiated the calculation (who kicked the ball).
   * @return Player's kick power
   */
  public static KickResult calculateKickPower(Player player) {
    long start = System.nanoTime();
    try {
      boolean isCharged = player.isSneaking();
      double charge = PhysicsUtil.CHARGE_BASE_VALUE + physics.getCharges().getOrDefault(player.getUniqueId(), 0D) * PhysicsUtil.CHARGE_MULTIPLIER;
      double speed = physics.getSpeed().getOrDefault(player.getUniqueId(), PhysicsUtil.MIN_SPEED_FOR_DAMPENING);
      double power = speed * PhysicsUtil.KICK_POWER_SPEED_MULTIPLIER + PhysicsUtil.BASE_POWER;
      double baseKickPower = isCharged ? charge * power : power;
      double finalKickPower = capKickPower(baseKickPower);

      return new KickResult(power, charge, baseKickPower, finalKickPower, isCharged);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "PhysicsUtil#calculateKickPower() took " + ms + "ms");
    }
  }

  /**
   * Removes all Slime entities in the main world.
   * Used only on plugin reload.
   */
  public static void removeCubes() {
    long start = System.nanoTime();
    try {
      List<Entity> entities = plugin.getServer().getWorlds().get(0).getEntities();
      for (Entity entity : entities) {
        if (entity instanceof Slime) {
          ((Slime) entity).setHealth(0);
          if (!physics.getCubes().contains(entity)) entity.remove();
        }
      }
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "PhysicsUtil#removeCubes() took " + ms + "ms");
    }
  }

  /**
   * Spawns a new ball at the given location and disables its AI.
   * @param location The location to spawn the cube.
   * @return The spawned entity.
   */
  public static Slime spawnCube(Location location) {
    long start = System.nanoTime();
    try {
      Slime cube = (Slime) location.getWorld().spawnEntity(location, EntityType.SLIME);
      cube.setRemoveWhenFarAway(false);
      cube.setSize(PhysicsUtil.SLIME_SIZE);
      // Permanent jump effect that stops the cube from hopping.
      cube.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, PhysicsUtil.JUMP_POTION_DURATION, PhysicsUtil.JUMP_POTION_AMPLIFIER, true), true);

      // NMS Hack to prevent the ball from trying to reach the player.
      EntitySlime nmsSlime = ((CraftSlime) cube).getHandle();
      try {
        Field bField = PathfinderGoalSelector.class.getDeclaredField("b");
        bField.setAccessible(true);
        bField.set(nmsSlime.goalSelector, new LinkedList<>());
        bField.set(nmsSlime.targetSelector, new LinkedList<>());
      } catch (Exception exception) {
        plugin.getLogger().log(Level.SEVERE, "Error injecting NMS Pathfinder Goals:", exception);
      }

      physics.getCubes().add(cube);
      return cube;
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "PhysicsUtil#spawnCube() took " + ms + "ms");
    }
  }

  /**
   * Checks if the given player is not allowed to interact based on their game mode.
   *
   * @param player The player to check. Must not be null.
   * @return True if the player is not in survival game mode, otherwise false.
   */
  public static boolean notAllowedToInteract(Player player) {
    return player.getGameMode() != GameMode.SURVIVAL;
  }

  /**
   * Determines if the specified player is currently away-from-keyboard (AFK)
   * based on their last recorded action time.
   *
   * @param player The player to check for AFK status. Must not be null.
   * @return True if the player is considered AFK (time since last action exceeds the set threshold), false otherwise.
   */
  public static boolean isAFK(Player player) {
    long last = physics.getLastAction().getOrDefault(player.getUniqueId(), 0L);
    return System.currentTimeMillis() - last > PhysicsUtil.AFK_THRESHOLD;
  }

  /**
   * Records the most recent action performed by a player by updating the timestamp of their last action.
   *
   * @param player The player whose action is being recorded. Must not be null.
   */
  public static void recordPlayerAction(Player player) {
    physics.getLastAction().put(player.getUniqueId(), System.currentTimeMillis());
  }

  /**
   * Removes a player and cleans up associated data in various system states.
   * This includes clearing cached player data, settings, physics data,
   * cooldowns, and actions associated with the specified player.
   *
   * @param player The player to be removed. Must not be null.
   */
  public static void removePlayer(Player player) {
    long start = System.nanoTime();
    try {
      UUID uuid = player.getUniqueId();
      fcManager.getCachedPlayers().remove(player);
      fcManager.getPlayerSettings().remove(uuid);
      physics.getSpeed().remove(uuid);
      physics.getCharges().remove(uuid);
      physics.getKicked().remove(uuid);
      physics.getBallHitCooldowns().remove(uuid);
      physics.getLastAction().remove(uuid);
      physics.getCubeHits().remove(uuid);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "PhysicsUtil#removePlayer() took " + ms + "ms");
    }
  }

  /**
   * Displays hit-related information to the player and manages the cooldown logic for hits
   * based on whether the hit is a charged or regular hit. This method also logs any performance
   * overhead if the execution time exceeds a millisecond.
   *
   * @param player The player who performed the hit. This parameter must not be null.
   * @param kickResult The result of the kick action, containing details such as kick power,
   *                   charge level, and whether the hit was charged. This parameter must not be null.
   */
  public static void showHits(Player player, KickResult kickResult) {
    long start = System.nanoTime();
    try {
      UUID playerId = player.getUniqueId();
      boolean isChargedHit = kickResult.isChargedHit();
      double finalKickPower = kickResult.getFinalKickPower();

      long cooldownDuration = isChargedHit ? PhysicsUtil.CHARGED_HIT_COOLDOWN : PhysicsUtil.REGULAR_HIT_COOLDOWN;
      long lastHitTime = physics.getBallHitCooldowns().getOrDefault(playerId, 0L);

      long timeElapsedSinceLastHit = System.currentTimeMillis() - lastHitTime;
      long timeRemainingMillis = Math.max(0, cooldownDuration - timeElapsedSinceLastHit);

      logger.sendActionBar(player, (isChargedHit
          ? Lang.HITDEBUG_PLAYER_CHARGED.replace(new String[]{
          String.format("%.2f", finalKickPower),
          String.format("%.2f", kickResult.getPower()),
          String.format("%.2f", kickResult.getCharge())
      })
          : Lang.HITDEBUG_PLAYER_REGULAR.replace(new String[]{String.format("%.2f", finalKickPower)})
      ) + Lang.HITDEBUG_PLAYER_COOLDOWN.replace(new String[]{timeRemainingMillis > 0 ? "&c" : "&a", String.valueOf(timeRemainingMillis)}));
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "PhysicsUtil#showHits() took " + ms + "ms");
    }
  }

  /**
   * Generates a debug message for a player's hit action based on whether the hit
   * is charged or regular. The method formats and returns a string with details
   * including the player's name, kick power, base power, and charge level. Logs
   * execution time if it exceeds a defined threshold.
   *
   * @param player The player who performed the hit. This parameter must not be null.
   * @param result The result of the hit action, which contains information such as kick power,
   *               charge level, and whether the hit was charged. This parameter must not be null.
   * @return A string containing the formatted debug information about the hit action.
   */
  public static String onHitDebug(Player player, KickResult result) {
    long start = System.nanoTime();
    try {
      return result.isChargedHit()
          ? Lang.HITDEBUG_CHARGED.replace(new String[]{
          player.getDisplayName(), (result.getFinalKickPower() != result.getBaseKickPower() ? "&c" : "&a") + String.format("%.2f", result.getFinalKickPower()),
          String.format("%.2f", result.getPower()), String.format("%.2f", result.getCharge())
      })
          : Lang.HITDEBUG_REGULAR.replace(new String[]{player.getDisplayName(), String.format("%.2f", result.getFinalKickPower())});
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "PhysicsUtil#onHitDebug() took " + ms + "ms");
    }
  }
}
