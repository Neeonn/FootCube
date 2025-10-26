package io.github.divinerealms.footcube.core;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.utils.KickResult;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_8_R3.EntitySlime;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.PathfinderGoalSelector;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftSlime;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Class that manages physics, movement, and collision detection for the FootCube ball.
 * This class runs the core game loop on a 1-tick interval.
 */
public class Physics {
  private final FCManager fcManager;
  private final Plugin plugin;
  private final Organization org;
  private final Logger logger;
  private final FileConfiguration config;
  private final BukkitScheduler scheduler;

  @Getter private final Set<Slime> cubes = ConcurrentHashMap.newKeySet();
  private final Set<Slime> cubesToRemove = ConcurrentHashMap.newKeySet();
  @Getter private final Map<UUID, Vector> velocities = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Long> kicked = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Double> speed = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Double> charges = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Long> ballHitCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();
  @Getter private final Set<UUID> cubeHits = ConcurrentHashMap.newKeySet();

  private static final long PHYSICS_TASK_INTERVAL_TICKS = 1L;
  private static final long GLOW_TASK_INTERVAL_TICKS = 10L;
  private static final long CUBE_REMOVAL_DELAY_TICKS = 20L;

  private static final int SLIME_SIZE = 1;
  private static final int JUMP_POTION_DURATION = Integer.MAX_VALUE;
  private static final int JUMP_POTION_AMPLIFIER = -3;

  private static final double KICKED_TIMEOUT_MS = 1000L;
  private static final double BALL_TOUCH_Y_OFFSET = 1.0;
  private static final double CUBE_HITBOX_ADJUSTMENT = 1.5;
  private static final double KICK_POWER_SPEED_MULTIPLIER = 2.0;
  private static final double CHARGE_BASE_VALUE = 1.0;

  private static final double MIN_SPEED_FOR_DAMPENING = 0.5;
  private static final double VELOCITY_DAMPENING_FACTOR = 0.5;
  private static final double PLAYER_SPEED_TOUCH_DIVISOR = 3.0;
  private static final double CUBE_SPEED_TOUCH_DIVISOR = 6.0;
  private static final double MIN_SOUND_POWER = 0.15;
  private static final double PROXIMITY_THRESHOLD_MULTIPLIER_SQUARED = 1.69;

  private static final double VECTOR_CHANGE_THRESHOLD = 0.1;
  private static final double VERTICAL_BOUNCE_THRESHOLD = 0.05;
  private static final double TOLERANCE_VELOCITY_CHECK = 1.0E-6;

  private static final int PLAYER_HEAD_LEVEL = 2;
  private static final int PLAYER_FOOT_LEVEL = 1;

  private static final double DISTANCE_PARTICLE_THRESHOLD_SQUARED = 32.0 * 32.0;
  private static final double PARTICLE_Y_OFFSET = 0.25;
  private static final float REDSTONE_PARTICLE_OFFSET = 0.01F;
  private static final float REDSTONE_PARTICLE_SPEED = 0.01F;
  private static final int REDSTONE_PARTICLE_COUNT = 8;
  private static final float GENERIC_PARTICLE_OFFSET = 0.01F;
  private static final float GENERIC_PARTICLE_SPEED = 0.1F;
  private static final int GENERIC_PARTICLE_COUNT = 10;

  private long chargedHitCooldown, regularHitCooldown, afkThreshold;
  private double maxKP, softCapMinFactor, chargeMultiplier, basePower, chargeRecoveryRate;
  private double hitRadius, hitRadiusSquared, minRadius, minRadiusSquared, bounceThreshold;
  @Getter private double cubeJumpRightClick;
  private double wallBounceFactor, airDragFactor;
  @Getter private float soundVolume, soundPitch;

  private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

  private BukkitTask physicsTask, glowTask;

  @Getter @Setter private boolean matchesEnabled = true;
  @Getter public boolean hitDebugEnabled = false;

  public Physics(FCManager fcManager) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.scheduler = plugin.getServer().getScheduler();
    this.org = fcManager.getOrg();
    this.logger = fcManager.getLogger();
    this.config = fcManager.getConfigManager().getConfig("config.yml");
    this.reload();
  }

  public boolean notAllowedToInteract(Player player) {
    return player.getGameMode() != GameMode.SURVIVAL;
  }

  /**
   * Starts the main physics loop running every tick.
   */
  private void startPhysicsTask() {
    if (physicsTask != null) physicsTask.cancel();
    physicsTask = scheduler.runTaskTimer(plugin, this::tick, PHYSICS_TASK_INTERVAL_TICKS, PHYSICS_TASK_INTERVAL_TICKS);
  }

  /**
   * Starts the particle rendering task for cubes, runs every 5 ticks.
   */
  private void startGlowTask() {
    if (glowTask != null) glowTask.cancel();
    glowTask = scheduler.runTaskTimer(plugin, this::showCubeParticles, GLOW_TASK_INTERVAL_TICKS, GLOW_TASK_INTERVAL_TICKS);
  }

  /**
   * Spawns a new ball at the given location and disables its AI.
   * @param location The location to spawn the cube.
   * @return The spawned entity.
   */
  public Slime spawnCube(Location location) {
    Slime cube = (Slime) location.getWorld().spawnEntity(location, EntityType.SLIME);
    cube.setRemoveWhenFarAway(false);
    cube.setSize(SLIME_SIZE);
    // Permanent jump effect that stops the cube from hopping.
    cube.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, JUMP_POTION_DURATION, JUMP_POTION_AMPLIFIER, true), true);

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

    cubes.add(cube);
    return cube;
  }

  /**
   * Removes all Slime entities in the main world.
   * Used only on plugin reload.
   */
  public void removeCubes() {
    List<Entity> entities = plugin.getServer().getWorlds().get(0).getEntities();
    for (Entity entity : entities) {
      if (entity instanceof Slime) {
        ((Slime) entity).setHealth(0);
        if (!cubes.contains(entity)) entity.remove();
      }
    }
  }

  /**
   * Calculates a custom distance squared between a player (locA) and the ball (locB).
   * The calculation includes an offset to account for player height and cube size.
   */
  public double getDistanceSquared(Location locA, Location locB) {
    double dx = locA.getX() - locB.getX();
    double dy = (locA.getY() - BALL_TOUCH_Y_OFFSET) - locB.getY() - CUBE_HITBOX_ADJUSTMENT;
    if (dy < 0) dy = 0;
    double dz = locA.getZ() - locB.getZ();

    return dx * dx + dy * dy + dz * dz;
  }

  /**
   * Calculates the final kick power based on player speed and current charge level.
   * @param player Player who initiated the calculation (who kicked the ball).
   * @return Player's kick power
   */
  public KickResult calculateKickPower(Player player) {
    boolean isCharged = player.isSneaking();
    double charge = CHARGE_BASE_VALUE + charges.getOrDefault(player.getUniqueId(), 0D) * chargeMultiplier;
    double speed = this.speed.getOrDefault(player.getUniqueId(), MIN_SPEED_FOR_DAMPENING);
    double power = speed * KICK_POWER_SPEED_MULTIPLIER + basePower;
    double baseKickPower = isCharged ? charge * power : power;
    double finalKickPower = capKickPower(baseKickPower);

    return new KickResult(power, charge, baseKickPower, finalKickPower, isCharged);
  }

  /**
   * Applies the soft cap to the calculated kick power.
   * @param baseKickPower Initial kick power.
   * @return Randomized capped kick power.
   */
  private double capKickPower(double baseKickPower) {
    if (baseKickPower <= maxKP) return baseKickPower;
    double minRandomKP = maxKP * softCapMinFactor;
    return RANDOM.nextDouble(minRandomKP, maxKP);
  }

  public String onHitDebug(Player player, KickResult result) {
    return result.isChargedHit()
      ? Lang.HITDEBUG_CHARGED.replace(new String[]{
       player.getDisplayName(), (result.getFinalKickPower() != result.getBaseKickPower() ? "&c" : "&a") + String.format("%.2f", result.getFinalKickPower()),
       String.format("%.2f", result.getPower()), String.format("%.2f", result.getCharge())
      })
      : Lang.HITDEBUG_REGULAR.replace(new String[]{player.getDisplayName(), String.format("%.2f", result.getFinalKickPower())});
  }

  /**
   * Check if the player is allowed to hit the ball based on hit cooldowns.
   * @param player Player who is being checked (who kicked the ball).
   * @return Remaining cooldown in milliseconds.
   */
  public boolean canHitBall(Player player) {
    long now = System.currentTimeMillis();
    long cooldown = player.isSneaking() ? chargedHitCooldown : regularHitCooldown;
    long lastHit = ballHitCooldowns.getOrDefault(player.getUniqueId(), 0L);

    return now - lastHit >= cooldown;
  }

  public void showHits(Player player, KickResult kickResult) {
    UUID playerId = player.getUniqueId();
    boolean isChargedHit = kickResult.isChargedHit();
    double finalKickPower = kickResult.getFinalKickPower();

    long cooldownDuration = isChargedHit ? chargedHitCooldown : regularHitCooldown;
    long lastHitTime = ballHitCooldowns.getOrDefault(playerId, 0L);

    long timeElapsedSinceLastHit = System.currentTimeMillis() - lastHitTime;
    long timeRemainingMillis = Math.max(0, cooldownDuration - timeElapsedSinceLastHit);

    logger.sendActionBar(player, (isChargedHit
      ? Lang.HITDEBUG_PLAYER_CHARGED.replace(new String[]{
          String.format("%.2f", finalKickPower),
          String.format("%.2f", kickResult.getPower()),
          String.format("%.2f", kickResult.getCharge())
        })
      : Lang.HITDEBUG_PLAYER_REGULAR.replace(new String[]{ String.format("%.2f", finalKickPower) })
    ) + Lang.HITDEBUG_PLAYER_COOLDOWN.replace(new String[]{timeRemainingMillis > 0 ? "&c" : "&a", String.valueOf(timeRemainingMillis)}));
  }

  /**
   * The main physics loop, running every tick (20 times per second).
   * This handles charge decay, collision detection, and vector manipulation.
   */
  private void tick() {
    if (fcManager.getCachedPlayers().isEmpty() || cubes.isEmpty()) return;

    kicked.entrySet().removeIf(uuidLongEntry -> System.currentTimeMillis() > uuidLongEntry.getValue() + KICKED_TIMEOUT_MS);

    Iterator<Map.Entry<UUID, Double>> chargesIterator = charges.entrySet().iterator();
    while (chargesIterator.hasNext()) {
      Map.Entry<UUID, Double> entry = chargesIterator.next();
      UUID uuid = entry.getKey();
      Player player = Bukkit.getPlayer(uuid);
      if (player == null) { chargesIterator.remove(); continue; }

      double nextCharge = CHARGE_BASE_VALUE - (CHARGE_BASE_VALUE - entry.getValue()) * chargeRecoveryRate;
      entry.setValue(nextCharge);
      player.setExp((float) nextCharge);
    }

    for (Slime cube : cubes) {
      UUID cubeId = cube.getUniqueId();
      if (cube.isDead()) {
        cubesToRemove.add(cube);
        continue;
      }

      Vector oldV = velocities.getOrDefault(cubeId, cube.getVelocity());
      Vector newV = cube.getVelocity().clone();

      boolean kicked = false, sound = false;

      double closeEnough = hitRadius + 0.1;
      List<Entity> nearbyEntities = cube.getNearbyEntities(closeEnough, closeEnough, closeEnough);
      for (Entity entity : nearbyEntities) {
        if (!(entity instanceof Player)) continue;
        Player player = (Player) entity;
        UUID playerId = player.getUniqueId();
        if (notAllowedToInteract(player) || isAFK(player)) continue;

        double distanceSquared = getDistanceSquared(cube.getLocation(), player.getLocation());
        if (distanceSquared > hitRadiusSquared) continue;

        double distance = -1;

        if (distanceSquared <= hitRadiusSquared) {
          distance = Math.sqrt(distanceSquared);
          double speed = newV.length();
          if (distance <= minRadius && speed >= MIN_SPEED_FOR_DAMPENING) newV.multiply(VELOCITY_DAMPENING_FACTOR / speed);
          double power = this.speed.getOrDefault(playerId, 0D) / PLAYER_SPEED_TOUCH_DIVISOR + oldV.length() / CUBE_SPEED_TOUCH_DIVISOR;
          newV.add(player.getLocation().getDirection().setY(0).normalize().multiply(power));
          org.ballTouch(player);
          kicked = true;
          if (power > MIN_SOUND_POWER) sound = true;
        }

        double newVSquared = newV.lengthSquared();
        double proximityThresholdSquared = newVSquared * PROXIMITY_THRESHOLD_MULTIPLIER_SQUARED;

        if (distanceSquared < proximityThresholdSquared) {
          double delta = (distance > -1) ? distance : Math.sqrt(distanceSquared);
          double newVLength = Math.sqrt(newVSquared);

          Vector loc = cube.getLocation().toVector();
          Vector nextLoc = loc.clone().add(newV);

          boolean rightDirection = true;
          Location playerLocation = player.getLocation();
          Vector pDir = new Vector(playerLocation.getX() - loc.getX(), 0, playerLocation.getZ() - loc.getZ());
          Vector cDir = (new Vector(newV.getX(), 0, newV.getZ())).normalize();

          int px = pDir.getX() < 0 ? -1 : 1;
          int pz = pDir.getZ() < 0 ? -1 : 1;
          int cx = cDir.getX() < 0 ? -1 : 1;
          int cz = cDir.getZ() < 0 ? -1 : 1;

          if (px != cx && pz != cz
              || (px != cx || pz != cz) && (!(cx * pDir.getX() > (cx * cz * px) * cDir.getZ())
              || !(cz * pDir.getZ() > (cz * cx * pz) * cDir.getX()))) rightDirection = false;

          if (rightDirection && loc.getY() < playerLocation.getY() + PLAYER_HEAD_LEVEL
              && loc.getY() > playerLocation.getY() - PLAYER_FOOT_LEVEL
              && nextLoc.getY() < playerLocation.getY() + PLAYER_HEAD_LEVEL
              && nextLoc.getY() > playerLocation.getY() - PLAYER_FOOT_LEVEL) {
            double velocityX = newV.getX();
            if (Math.abs(velocityX) < TOLERANCE_VELOCITY_CHECK) continue;

            double a = newV.getZ() / newV.getX();
            double b = loc.getZ() - a * loc.getX();
            double numerator = a * playerLocation.getX() - playerLocation.getZ() + b;
            double numeratorSquared = numerator * numerator;
            double denominatorSquared = a * a + CHARGE_BASE_VALUE;

            if (numeratorSquared < minRadiusSquared * denominatorSquared) newV.multiply(delta / newVLength);
          }
        }
      }

      if (newV.getX() == 0) {
        newV.setX(-oldV.getX() * wallBounceFactor);
        if (Math.abs(oldV.getX()) > bounceThreshold) sound = true;
      } else if (!kicked && Math.abs(oldV.getX() - newV.getX()) < VECTOR_CHANGE_THRESHOLD) newV.setX(oldV.getX() * airDragFactor);

      if (newV.getZ() == 0) {
        newV.setZ(-oldV.getZ() * wallBounceFactor);
        if (Math.abs(oldV.getZ()) > bounceThreshold) sound = true;
      } else if (!kicked && Math.abs(oldV.getZ() - newV.getZ()) < VECTOR_CHANGE_THRESHOLD) newV.setZ(oldV.getZ() * airDragFactor);

      if (newV.getY() < 0 && oldV.getY() < 0 && oldV.getY() < newV.getY() - VERTICAL_BOUNCE_THRESHOLD) {
        newV.setY(-oldV.getY() * wallBounceFactor);
        if (Math.abs(oldV.getY()) > bounceThreshold) sound = true;
      }

      if (sound) cube.getWorld().playSound(cube.getLocation(), Sound.SLIME_WALK, soundVolume, soundPitch);
      cube.setVelocity(newV);
      velocities.put(cubeId, newV);
    }

    scheduleCubeRemoval();
  }

  /**
   * Schedules the removal of dead cubes 20 ticks later (1 second).
   * This delay is added to help mitigate the issues where the cube is dead but a final interaction is processed,
   * and to provide a graceful death animation instead of instant removal.
   */
  private void scheduleCubeRemoval() {
    if (cubesToRemove.isEmpty()) return;

    Set<Slime> toRemove = new HashSet<>(cubesToRemove);
    cubesToRemove.clear();

    scheduler.runTaskLater(plugin, () -> toRemove.forEach(cube -> {
      cubes.remove(cube);
      if (!cube.isDead()) cube.remove();
    }), CUBE_REMOVAL_DELAY_TICKS);
  }

  /**
   * Renders particle trail that follows the cube for players that are far away.
   * This is implemented to tackle the issue of render distance for entities in 1.8.8
   */
  private void showCubeParticles() {
    long start = System.nanoTime();
    try {
      Collection<? extends Player> onlinePlayers = fcManager.getCachedPlayers();
      if (onlinePlayers.isEmpty() || cubes.isEmpty()) return;

      for (Slime cube : cubes) {
        if (cube == null || cube.isDead() || cube.getLocation() == null) continue;

        Location cubeLocation = cube.getLocation().clone().add(0, PARTICLE_Y_OFFSET, 0);

        for (Player player : onlinePlayers) {
          if (cubeLocation.distanceSquared(player.getLocation()) < DISTANCE_PARTICLE_THRESHOLD_SQUARED) continue;

          PlayerSettings settings = fcManager.getPlayerSettings(player);
          if (settings == null || !settings.isParticlesEnabled()) continue;

          EnumParticle particle = settings.getParticle();
          if (particle == EnumParticle.REDSTONE) {
            Color color = settings.getRedstoneColor();
            Utilities.sendParticle(player, EnumParticle.REDSTONE, cubeLocation, REDSTONE_PARTICLE_OFFSET, REDSTONE_PARTICLE_OFFSET, REDSTONE_PARTICLE_OFFSET, REDSTONE_PARTICLE_SPEED, REDSTONE_PARTICLE_COUNT, color);
          } else {
            Utilities.sendParticle(player, particle, cubeLocation, GENERIC_PARTICLE_OFFSET, GENERIC_PARTICLE_OFFSET, GENERIC_PARTICLE_OFFSET, GENERIC_PARTICLE_SPEED, GENERIC_PARTICLE_COUNT);
          }
        }
      }
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "Physics#showCubeParticles() took " + ms + "ms");
    }
  }

  public void cleanupTasks() {
    if (physicsTask != null) { physicsTask.cancel(); physicsTask = null; }
    if (glowTask != null) { glowTask.cancel(); glowTask = null; }
  }

  public void reload() {
    cleanupTasks();

    chargedHitCooldown = config.getLong("physics.cooldowns.charged-hit", 500);
    regularHitCooldown = config.getLong("physics.cooldowns.regular-hit", 150);
    afkThreshold = config.getLong("physics.afk-threshold", 60000);

    maxKP = config.getDouble("physics.kick-power.max", 6.75);
    softCapMinFactor = config.getDouble("physics.kick-power.soft-cap-min-factor", 0.8);
    chargeMultiplier = config.getDouble("physics.kick-power.charge-multiplier", 7.0);
    basePower = config.getDouble("physics.kick-power.base-power", 0.4);
    chargeRecoveryRate = config.getDouble("physics.charge-recovery-rate", 0.945);

    hitRadius = config.getDouble("physics.distance-thresholds.hit-radius", 1.2);
    hitRadiusSquared = hitRadius * hitRadius;
    minRadius = config.getDouble("physics.distance-thresholds.min-radius", 0.8);
    minRadiusSquared = minRadius * minRadius;
    bounceThreshold = config.getDouble("physics.distance-thresholds.bounce-threshold", 0.3);

    cubeJumpRightClick = config.getDouble("physics.jump", 0.7);
    wallBounceFactor = config.getDouble("physics.collision.wall-bounce-factor", 0.8);
    airDragFactor = config.getDouble("physics.collision.air-drag-factor", 0.98);

    soundVolume = (float) config.getDouble("physics.sound.volume", 0.5);
    soundPitch = (float) config.getDouble("physics.sound.pitch", 1.0);

    startPhysicsTask();
    startGlowTask();
  }

  public void removePlayer(Player player) {
    UUID uuid = player.getUniqueId();
    fcManager.getCachedPlayers().remove(player);
    fcManager.getPlayerSettings().remove(uuid);
    speed.remove(uuid);
    charges.remove(uuid);
    kicked.remove(uuid);
    ballHitCooldowns.remove(uuid);
    lastAction.remove(uuid);
    cubeHits.remove(uuid);
  }

  public void recordPlayerAction(Player player) {
    lastAction.put(player.getUniqueId(), System.currentTimeMillis());
  }

  public boolean isAFK(Player player) {
    long last = lastAction.getOrDefault(player.getUniqueId(), 0L);
    return System.currentTimeMillis() - last > afkThreshold;
  }

  public void cleanup() {
    cleanupTasks();

    cubes.clear();
    cubesToRemove.clear();
    velocities.clear();
    kicked.clear();
    speed.clear();
    charges.clear();
    ballHitCooldowns.clear();
    fcManager.getPlayerSettings().clear();
    lastAction.clear();
    cubeHits.clear();
  }
}