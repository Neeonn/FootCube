package io.github.divinerealms.footcube.core;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The Physics class is responsible for managing and simulating physics-related behavior in the game.
 * It handles operations such as collision detection, vector manipulation, charge decay, and managing
 * interactions between entities like Slime entities and cubes.
 * This class maintains various data structures and fields required for physics processing, such as
 * managing entity velocities, tracking scheduled cube removals, handling sound effects, and logging
 * game states.
 */
public class Physics {
  private final FCManager fcManager;
  private final Plugin plugin;
  private final Organization org;
  private final Logger logger;
  private final BukkitScheduler scheduler;

  @Getter private final Set<Slime> cubes = ConcurrentHashMap.newKeySet();
  private final Set<Slime> cubesToRemove = ConcurrentHashMap.newKeySet();
  @Getter private final Map<UUID, Vector> velocities = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Long> kicked = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Double> speed = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Double> charges = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Long> ballHitCooldowns = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();
  @Getter private final Set<UUID> cubeHits = ConcurrentHashMap.newKeySet();
  @Getter private final Queue<SoundAction> soundQueue = new ConcurrentLinkedQueue<>();
  @Getter private final Queue<HitAction> hitQueue = new ConcurrentLinkedQueue<>();
  @Getter private final Map<UUID, Long> buttonCooldowns = new ConcurrentHashMap<>();

  @Getter @Setter private boolean matchesEnabled = true;
  @Getter public boolean hitDebugEnabled = false;

  private int tickCounter = 0;

  public Physics(FCManager fcManager) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.scheduler = plugin.getServer().getScheduler();
    this.org = fcManager.getOrg();
    this.logger = fcManager.getLogger();
  }

  /**
   * The main physics loop, running every tick (20 times per second).
   * This handles charge decay, collision detection, and vector manipulation.
   */
  public void update() {
    long start = System.nanoTime();
    try {
      if (fcManager.getCachedPlayers().isEmpty() || cubes.isEmpty()) return;

      processQueuedHits();

      tickCounter++;
      kicked.entrySet().removeIf(uuidLongEntry -> System.currentTimeMillis() > uuidLongEntry.getValue() + PhysicsUtil.KICKED_TIMEOUT_MS);
      Iterator<Map.Entry<UUID, Double>> chargesIterator = charges.entrySet().iterator();
      while (chargesIterator.hasNext()) {
        Map.Entry<UUID, Double> entry = chargesIterator.next();
        UUID uuid = entry.getKey();
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
          chargesIterator.remove();
          continue;
        }

        double nextCharge = PhysicsUtil.CHARGE_BASE_VALUE - (PhysicsUtil.CHARGE_BASE_VALUE - entry.getValue()) * PhysicsUtil.CHARGE_RECOVERY_RATE;
        entry.setValue(nextCharge);
        if (tickCounter % PhysicsUtil.EXP_UPDATE_INTERVAL_TICKS == 0) player.setExp((float) nextCharge);
      }

      for (Slime cube : cubes) {
        UUID cubeId = cube.getUniqueId();
        if (cube.isDead()) {
          cubesToRemove.add(cube);
          continue;
        }

        Vector oldV = velocities.getOrDefault(cubeId, cube.getVelocity().clone());
        Vector newV = cube.getVelocity().clone();
        Location cubeLoc = cube.getLocation();

        boolean kicked = false, sound = false;

        double closeEnough = PhysicsUtil.HIT_RADIUS + 0.1;
        for (Entity entity : cube.getNearbyEntities(closeEnough, closeEnough, closeEnough)) {
          if (!(entity instanceof Player)) continue;
          Player player = (Player) entity;
          UUID playerId = player.getUniqueId();
          if (PhysicsUtil.notAllowedToInteract(player) || PhysicsUtil.isAFK(player)) continue;

          Location playerLoc = player.getLocation();
          double distanceSquared = PhysicsUtil.getDistanceSquared(cubeLoc, playerLoc);
          if (distanceSquared > PhysicsUtil.HIT_RADIUS_SQUARED) continue;

          double distance = -1;
          if (distanceSquared <= PhysicsUtil.HIT_RADIUS_SQUARED) {
            distance = Math.sqrt(distanceSquared);
            double speed = newV.length();
            if (distance <= PhysicsUtil.MIN_RADIUS && speed >= PhysicsUtil.MIN_SPEED_FOR_DAMPENING)
              newV.multiply(PhysicsUtil.VELOCITY_DAMPENING_FACTOR / speed);
            double power = this.speed.getOrDefault(playerId, 0D) / PhysicsUtil.PLAYER_SPEED_TOUCH_DIVISOR + oldV.length() / PhysicsUtil.CUBE_SPEED_TOUCH_DIVISOR;
            newV.add(playerLoc.getDirection().setY(0).normalize().multiply(power));
            org.ballTouch(player, TouchType.MOVE);
            kicked = true;
            if (power > PhysicsUtil.MIN_SOUND_POWER) sound = true;
          }

          double newVSquared = newV.lengthSquared();
          double proximityThresholdSquared = newVSquared * PhysicsUtil.PROXIMITY_THRESHOLD_MULTIPLIER_SQUARED;

          if (distanceSquared < proximityThresholdSquared) {
            double delta = (distance > -1) ? distance : Math.sqrt(distanceSquared);
            double newVLength = Math.sqrt(newVSquared);

            Vector loc = cubeLoc.toVector();
            Vector nextLoc = loc.clone().add(newV);

            boolean rightDirection = true;
            Vector pDir = new Vector(playerLoc.getX() - loc.getX(), 0, playerLoc.getZ() - loc.getZ());
            Vector cDir = (new Vector(newV.getX(), 0, newV.getZ())).normalize();

            int px = pDir.getX() < 0 ? -1 : 1;
            int pz = pDir.getZ() < 0 ? -1 : 1;
            int cx = cDir.getX() < 0 ? -1 : 1;
            int cz = cDir.getZ() < 0 ? -1 : 1;

            if (px != cx && pz != cz
                || (px != cx || pz != cz) && (!(cx * pDir.getX() > (cx * cz * px) * cDir.getZ())
                || !(cz * pDir.getZ() > (cz * cx * pz) * cDir.getX()))) rightDirection = false;

            if (rightDirection && loc.getY() < playerLoc.getY() + PhysicsUtil.PLAYER_HEAD_LEVEL
                && loc.getY() > playerLoc.getY() - PhysicsUtil.PLAYER_FOOT_LEVEL
                && nextLoc.getY() < playerLoc.getY() + PhysicsUtil.PLAYER_HEAD_LEVEL
                && nextLoc.getY() > playerLoc.getY() - PhysicsUtil.PLAYER_FOOT_LEVEL) {
              double velocityX = newV.getX();
              if (Math.abs(velocityX) < PhysicsUtil.TOLERANCE_VELOCITY_CHECK) continue;

              double a = newV.getZ() / newV.getX();
              double b = loc.getZ() - a * loc.getX();
              double numerator = a * playerLoc.getX() - playerLoc.getZ() + b;
              double numeratorSquared = numerator * numerator;
              double denominatorSquared = a * a + PhysicsUtil.CHARGE_BASE_VALUE;

              if (numeratorSquared < PhysicsUtil.MIN_RADIUS_SQUARED * denominatorSquared)
                newV.multiply(delta / newVLength);
            }
          }
        }

        if (newV.getX() == 0) {
          newV.setX(-oldV.getX() * PhysicsUtil.WALL_BOUNCE_FACTOR);
          if (Math.abs(oldV.getX()) > PhysicsUtil.BOUNCE_THRESHOLD) sound = true;
        } else if (!kicked && Math.abs(oldV.getX() - newV.getX()) < PhysicsUtil.VECTOR_CHANGE_THRESHOLD)
          newV.setX(oldV.getX() * PhysicsUtil.AIR_DRAG_FACTOR);

        if (newV.getZ() == 0) {
          newV.setZ(-oldV.getZ() * PhysicsUtil.WALL_BOUNCE_FACTOR);
          if (Math.abs(oldV.getZ()) > PhysicsUtil.BOUNCE_THRESHOLD) sound = true;
        } else if (!kicked && Math.abs(oldV.getZ() - newV.getZ()) < PhysicsUtil.VECTOR_CHANGE_THRESHOLD)
          newV.setZ(oldV.getZ() * PhysicsUtil.AIR_DRAG_FACTOR);

        if (newV.getY() < 0 && oldV.getY() < 0 && oldV.getY() < newV.getY() - PhysicsUtil.VERTICAL_BOUNCE_THRESHOLD) {
          newV.setY(-oldV.getY() * PhysicsUtil.WALL_BOUNCE_FACTOR);
          if (Math.abs(oldV.getY()) > PhysicsUtil.BOUNCE_THRESHOLD) sound = true;
        }

        if (sound) PhysicsUtil.queueSound(cubeLoc);

        cube.setVelocity(newV);
        velocities.put(cubeId, newV);
      }

      scheduleSound();
      scheduleCubeRemoval();
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "Physics#update() took " + ms + "ms");
    }
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
    }), PhysicsUtil.CUBE_REMOVAL_DELAY_TICKS);
  }

  /**
   * Schedules sounds to be played at specific locations stored in the sound queue.
   * If the sound queue is empty, no actions are performed. Otherwise, the method processes
   * the current list of queued locations, clears the queue, and schedules a task to play
   * the sound at each of the specified locations.
   */
  private void scheduleSound() {
    if (soundQueue.isEmpty()) return;

    Queue<SoundAction> toPlay = new ArrayDeque<>(soundQueue);
    soundQueue.clear();

    scheduler.runTask(plugin, () -> toPlay.forEach(action -> {
      Location location;
      Sound sound = action.getSound();
      float volume = action.getVolume();
      float pitch = action.getPitch();

      if (action.isPlayerTargeted()) {
        Player player = action.getPlayer();
        if (player == null || !player.isOnline()) return;

        location = player.getLocation();
        player.playSound(location, sound, volume, pitch);
      } else {
        location = action.getLocation();
        if (location == null) return;

        location.getWorld().playSound(location, sound, volume, pitch);
      }
    }));
  }

  /**
   * Processes actions stored in the hit queue by updating the velocity of associated Slime entities
   * and storing their new velocities in the velocity map. The hit queue is then cleared.
   */
  private void processQueuedHits() {
    if (hitQueue.isEmpty()) return;

    for (HitAction action : hitQueue) {
      Slime cube = action.getCube();
      if (cube.isDead()) continue;

      Vector appliedVelocity = cube.getVelocity().add(action.getVelocity());
      cube.setVelocity(appliedVelocity);
    }

    hitQueue.clear();
  }

  /**
   * Renders particle trail that follows the cube for players that are far away.
   * This is implemented to tackle the issue of render distance for entities in 1.8.8
   */
  public void showCubeParticles() {
    long start = System.nanoTime();
    try {
      Collection<? extends Player> onlinePlayers = fcManager.getCachedPlayers();
      if (onlinePlayers.isEmpty() || cubes.isEmpty()) return;

      Map<UUID, Location> playerLocations = new HashMap<>(onlinePlayers.size());
      Map<UUID, PlayerSettings> playerSettings = new HashMap<>(onlinePlayers.size());

      for (Player p : onlinePlayers) {
        playerLocations.put(p.getUniqueId(), p.getLocation());
        playerSettings.put(p.getUniqueId(), fcManager.getPlayerSettings(p));
      }

      for (Slime cube : cubes) {
        if (cube == null || cube.isDead()) continue;
        Location cubeLoc = cube.getLocation();
        if (cubeLoc == null) continue;

        double x = cubeLoc.getX();
        double y = cubeLoc.getY() + PhysicsUtil.PARTICLE_Y_OFFSET;
        double z = cubeLoc.getZ();

        boolean anyFarPlayers = false;
        for (Map.Entry<UUID, PlayerSettings> entry : playerSettings.entrySet()) {
          PlayerSettings s = entry.getValue();
          if (s == null || !s.isParticlesEnabled()) continue;
          Location playerLoc = playerLocations.get(entry.getKey());
          if (playerLoc == null) continue;

          double dx = playerLoc.getX() - x;
          double dy = playerLoc.getY() - y;
          double dz = playerLoc.getZ() - z;
          if ((dx * dx + dy * dy + dz * dz) >= PhysicsUtil.DISTANCE_PARTICLE_THRESHOLD_SQUARED) {
            anyFarPlayers = true;
            break;
          }
        }
        if (!anyFarPlayers) continue;

        for (Player player : onlinePlayers) {
          UUID playerId = player.getUniqueId();
          Location playerLoc = playerLocations.get(playerId);
          if (playerLoc == null) continue;

          double dx = playerLoc.getX() - x;
          double dy = playerLoc.getY() - y;
          double dz = playerLoc.getZ() - z;
          if ((dx * dx + dy * dy + dz * dz) < PhysicsUtil.DISTANCE_PARTICLE_THRESHOLD_SQUARED) continue;

          PlayerSettings settings = playerSettings.get(playerId);
          if (settings == null || !settings.isParticlesEnabled()) continue;

          EnumParticle particle = settings.getParticle();
          if (particle == EnumParticle.REDSTONE) {
            Color color = settings.getRedstoneColor();
            Utilities.sendParticle(player, EnumParticle.REDSTONE,
                x, y, z,
                color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F,
                1.0F, 0);
          } else {
            Utilities.sendParticle(player, particle,
                x, y, z,
                PhysicsUtil.GENERIC_PARTICLE_OFFSET,
                PhysicsUtil.GENERIC_PARTICLE_OFFSET,
                PhysicsUtil.GENERIC_PARTICLE_OFFSET,
                PhysicsUtil.GENERIC_PARTICLE_SPEED,
                PhysicsUtil.GENERIC_PARTICLE_COUNT);
          }
        }
      }
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "Physics#showCubeParticles() took " + ms + "ms");
    }
  }

  /**
   * Cleans up various physics-related data structures by clearing them and resetting their states.
   * This method ensures that all leftover data and states from previous operations are removed
   * to prepare for a fresh state. It also logs a warning if the cleanup process takes more than 1 millisecond.
   */
  public void cleanup() {
    long start = System.nanoTime();
    try {
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
      soundQueue.clear();
      hitQueue.clear();
      buttonCooldowns.clear();
      matchesEnabled = true;
      hitDebugEnabled = false;
      tickCounter = 0;
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "Physics#cleanup() took " + ms + "ms");
    }
  }

  /**
   * Represents an action that occurs when a "Slime" entity (referred to as a cube) is hit within the physics system.
   * This class stores information about the entity being hit, the velocity applied during the hit,
   * and whether the velocity from this action is additive or should replace the current velocity.
   * This class is designed to be immutable, ensuring thread-safety when used in concurrent environments.
   */
  @Getter
  @AllArgsConstructor
  static class HitAction {
    private final Slime cube;
    private final Vector velocity;
  }

  /**
   * Represents an action to play a sound at a given location. This class encapsulates
   * all the necessary information such as the location, player-specific targeting,
   * sound type, volume, and pitch.
   * Instances of this class can optionally target a specific player. When targeting a player, the sound
   * will be directed towards that player; otherwise, it will act as a general sound event for all nearby players.
   * The sound properties (volume and pitch) define how the sound is experienced during playback.
   */
  @Getter
  @AllArgsConstructor
  static class SoundAction {
    private final Location location;
    private final Player player;
    private final Sound sound;
    private final float volume;
    private final float pitch;

    public boolean isPlayerTargeted() {
      return player != null;
    }
  }
}