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
 * Handles all physics interactions between players and cubes in the FootCube plugin.
 * <p>
 * This class is responsible for updating cube positions and velocities every tick (20 times per second),
 * applying realistic physics effects such as collision, drag, and bounces. It also manages interaction
 * logic between players and cubes, including proximity detection, touch power calculation, and sound triggers.
 */
public class Physics {
  private final FCManager fcManager;
  private final Plugin plugin;
  private final Organization org;
  private final Logger logger;
  private final BukkitScheduler scheduler;
  @Setter private PhysicsUtil physicsUtil;

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
    this.org = fcManager.getOrg();
    this.logger = fcManager.getLogger();
    this.physicsUtil = fcManager.getPhysicsUtil();
    this.scheduler = plugin.getServer().getScheduler();
  }

  /**
   * The main physics loop, running every game tick (20 times per second).
   * <p>
   * This method handles charge regeneration, player-cube interaction detection, cube velocity updates,
   * collision responses (wall, air, and floor), and sound scheduling. Each cube is processed individually
   * with vector math to ensure smooth and realistic behavior.
   * </p>
   *
   * <p><b>Performance Note:</b> The entire routine measures its runtime in milliseconds and logs a warning
   * if the update cycle exceeds 1 ms, which helps identify physics bottlenecks on large servers.</p>
   */
  public void update() {
    long start = System.nanoTime();
    try {
      // Skip processing if there are no active players or cubes.
      if (fcManager.getCachedPlayers().isEmpty() || cubes.isEmpty()) return;

      processQueuedHits();
      tickCounter++;

      // Remove players from the 'kicked' cache if their timeout expired.
      kicked.entrySet().removeIf(entry -> System.currentTimeMillis() > entry.getValue() + PhysicsUtil.KICKED_TIMEOUT_MS);

      // Regenerate player charge values gradually, visualized via the experience bar.
      Iterator<Map.Entry<UUID, Double>> chargesIterator = charges.entrySet().iterator();
      while (chargesIterator.hasNext()) {
        Map.Entry<UUID, Double> entry = chargesIterator.next();
        UUID uuid = entry.getKey();
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) { chargesIterator.remove(); continue; }

        double currentCharge = entry.getValue();
        double recoveredCharge = PhysicsUtil.CHARGE_BASE_VALUE - (PhysicsUtil.CHARGE_BASE_VALUE - currentCharge) * PhysicsUtil.CHARGE_RECOVERY_RATE;
        entry.setValue(recoveredCharge);

        // Only update player XP every few ticks to reduce overhead.
        if (tickCounter % PhysicsUtil.EXP_UPDATE_INTERVAL_TICKS == 0) player.setExp((float) recoveredCharge);
      }

      // Main cube processing loop.
      for (Slime cube : cubes) {
        if (cube.isDead()) { cubesToRemove.add(cube); continue; }

        UUID cubeId = cube.getUniqueId();
        Location cubeLocation = cube.getLocation();

        Vector previousVelocity = velocities.getOrDefault(cubeId, cube.getVelocity().clone());
        Vector newVelocity = cube.getVelocity().clone();

        boolean wasKicked = false, playSound = false;

        // Process all nearby entities within hit range.
        double searchRadius = PhysicsUtil.HIT_RADIUS_SQUARED;
        for (Entity entity : cube.getNearbyEntities(searchRadius, searchRadius, searchRadius)) {
          if (!(entity instanceof Player)) continue;
          Player player = (Player) entity;
          if (physicsUtil.notAllowedToInteract(player) || physicsUtil.isAFK(player)) continue;
          UUID playerId = player.getUniqueId();

          // --- Player proximity and touch detection ---
          // Determines if the player is close enough to directly affect the cube.
          double distanceSq = physicsUtil.getDistanceSquared(cube.getLocation(), player.getLocation());
          if (distanceSq <= PhysicsUtil.HIT_RADIUS_SQUARED) {
            double cubeSpeed = newVelocity.length();

            // Dampen ball speed if inside proximity and moving too fast to prevent overshoot.
            if (distanceSq <= PhysicsUtil.MIN_RADIUS_SQUARED && cubeSpeed >= PhysicsUtil.MIN_SPEED_FOR_DAMPENING)
              newVelocity.multiply(PhysicsUtil.VELOCITY_DAMPENING_FACTOR / cubeSpeed);

            // Compute the resulting power from player movement and cube velocity.
            double impactPower = this.speed.getOrDefault(playerId, 1D) / PhysicsUtil.PLAYER_SPEED_TOUCH_DIVISOR
                + previousVelocity.length() / PhysicsUtil.CUBE_SPEED_TOUCH_DIVISOR;

            // Apply a horizontal directional force in the direction the player is facing.
            newVelocity.add(player.getLocation().getDirection().setY(0).normalize().multiply(impactPower));

            // Register the touch interaction with the organization system.
            org.ballTouch(player, TouchType.MOVE);
            wasKicked = true;

            // Trigger sound effect if impact force exceeds threshold.
            if (impactPower > PhysicsUtil.MIN_SOUND_POWER) playSound = true;
          }

          // --- Advanced directional and proximity adjustments ---
          // Used for adjusting near-collision behavior to maintain realistic interactions.
          double newVelocitySq = newVelocity.lengthSquared();
          double proximityThresholdSq = newVelocitySq * PhysicsUtil.PROXIMITY_THRESHOLD_MULTIPLIER_SQUARED;

          if (distanceSq < proximityThresholdSq) {
            double distance = Math.sqrt(distanceSq);

            Vector cubePos = cube.getLocation().toVector();
            Vector projectedNextPos = (new Vector(cubePos.getX(), cubePos.getY(), cubePos.getZ())).add(newVelocity);

            // Calculate directional alignment between cube velocity and player.
            boolean movingTowardPlayer = true;
            Vector directionToPlayer = new Vector(player.getLocation().getX() - cubePos.getX(), 0, player.getLocation().getZ() - cubePos.getZ());
            Vector cubeDirection = (new Vector(newVelocity.getX(), 0, newVelocity.getZ())).normalize();

            // Determine relative direction quadrant using sign comparison.
            int playerDirX = directionToPlayer.getX() < 0 ? -1 : 1;
            int playerDirZ = directionToPlayer.getZ() < 0 ? -1 : 1;
            int cubeDirX = cubeDirection.getX() < 0 ? -1 : 1;
            int cubeDirZ = cubeDirection.getZ() < 0 ? -1 : 1;

            // Complex logic to determine whether cube is traveling away or toward the player.
            if (playerDirX != cubeDirX && playerDirZ != cubeDirZ
                || (playerDirX != cubeDirX || playerDirZ != cubeDirZ)
                && (!(cubeDirX * directionToPlayer.getX() > (cubeDirX * cubeDirZ * playerDirX) * cubeDirection.getZ())
                || !(cubeDirZ * directionToPlayer.getZ() > (cubeDirZ * cubeDirX * playerDirZ) * cubeDirection.getX()))) {
              movingTowardPlayer = false;
            }

            // --- Height (Y-axis) validation ---
            // Ensure cube is within player's vertical interaction range.
            double playerY = player.getLocation().getY();
            boolean withinVerticalRange =
                cubePos.getY() < playerY + PhysicsUtil.PLAYER_HEAD_LEVEL &&
                    cubePos.getY() > playerY - PhysicsUtil.PLAYER_FOOT_LEVEL &&
                    projectedNextPos.getY() < playerY + PhysicsUtil.PLAYER_HEAD_LEVEL &&
                    projectedNextPos.getY() > playerY - PhysicsUtil.PLAYER_FOOT_LEVEL;

            // --- Collision line proximity correction ---
            // Prevents the cube from clipping through the player when moving directly toward them.
            if (movingTowardPlayer && withinVerticalRange) {
              double velocityX = newVelocity.getX();
              if (Math.abs(velocityX) < PhysicsUtil.TOLERANCE_VELOCITY_CHECK) continue;

              // Represent cube trajectory as z = a*x + b and compute perpendicular distance.
              double perpendicularDistance = physicsUtil.getPerpendicularDistance(newVelocity, cubePos, player);

              // Reduce velocity to avoid tunneling effect when too close.
              if (perpendicularDistance < PhysicsUtil.MIN_RADIUS) newVelocity.multiply(distance / newVelocity.length());
            }
          }
        }

        // --- Handle wall collisions and air drag effects ---

        // X-axis collision and drag adjustment.
        // If the cube stops moving horizontally (X=0), bounce it back with reduced energy.
        if (newVelocity.getX() == 0) {
          newVelocity.setX(-previousVelocity.getX() * PhysicsUtil.WALL_BOUNCE_FACTOR);
          if (Math.abs(previousVelocity.getX()) > PhysicsUtil.BOUNCE_THRESHOLD) playSound = true; // Trigger sound if impact force is strong enough.

        // If cube wasn’t recently kicked and velocity change is small, apply gradual air drag slowdown.
        } else if (!wasKicked && Math.abs(previousVelocity.getX() - newVelocity.getX()) < PhysicsUtil.VECTOR_CHANGE_THRESHOLD)
          newVelocity.setX(previousVelocity.getX() * PhysicsUtil.AIR_DRAG_FACTOR);

        // Z-axis collision and drag adjustment (mirrors X-axis logic).
        if (newVelocity.getZ() == 0) {
          newVelocity.setZ(-previousVelocity.getZ() * PhysicsUtil.WALL_BOUNCE_FACTOR);
          if (Math.abs(previousVelocity.getZ()) > PhysicsUtil.BOUNCE_THRESHOLD) playSound = true;
        } else if (!wasKicked && Math.abs(previousVelocity.getZ() - newVelocity.getZ()) < PhysicsUtil.VECTOR_CHANGE_THRESHOLD)
          newVelocity.setZ(previousVelocity.getZ() * PhysicsUtil.AIR_DRAG_FACTOR);

        // Y-axis bounce (vertical collision against floor or ceiling).
        // This ensures realistic vertical rebound, preventing velocity loss bugs on impact.
        if (newVelocity.getY() < 0 && previousVelocity.getY() < 0 && previousVelocity.getY() < newVelocity.getY() - PhysicsUtil.VERTICAL_BOUNCE_THRESHOLD) {
          newVelocity.setY(-previousVelocity.getY() * PhysicsUtil.WALL_BOUNCE_FACTOR);
          if (Math.abs(previousVelocity.getY()) > PhysicsUtil.BOUNCE_THRESHOLD) playSound = true;

        // This patches a weird bug that makes the cube glue to the player and the floor.
        } else if (cube.isOnGround() && !physicsUtil.hasQueuedHit(cube)) {
          double bounceY = -previousVelocity.getY() * PhysicsUtil.WALL_BOUNCE_FACTOR;
          newVelocity.setY(Math.max(0.05, Math.abs(bounceY)));
        }

        // Queue impact sound effect if any significant collision occurred.
        if (playSound) physicsUtil.queueSound(cubeLocation);

        // Apply final computed velocity to the cube and update its tracked state.
        cube.setVelocity(newVelocity);
        velocities.put(cubeId, newVelocity);
      }

      // Finalize scheduled physics actions.
      scheduleSound();// Dispatch queued sound events to players.
      scheduleCubeRemoval(); // Safely remove dead or invalid cube entities.
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1 && tickCounter % 20 == 0) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "Physics#update() took " + ms + "ms");
    }
  }

  /**
   * Schedules the removal of dead or invalid cubes from the world.
   * Ensures the cubes list remains synchronized and prevents entity leaks.
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
   * Schedules the playback of queued sound events after physics updates.
   * Ensures that multiple cubes can trigger sounds in a single tick efficiently.
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
   * Processes queued hit actions to synchronize cube-player interactions.
   * <p>
   * This is called at the beginning of each tick cycle to ensure pending interactions are handled
   * before performing new physics calculations.
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
   * Renders particle trails that visually follow cubes (Slime entities) for players who are far enough away
   * that they might not see the actual cube entity due to Minecraft 1.8.8 render distance limitations.
   * <p>
   * This method improves visibility and game immersion by simulating cube motion with particles,
   * ensuring remote players can still perceive the ball's movement even outside their render distance.
   * </p>
   *
   * <p><b>Performance considerations:</b> Particle effects are sent only to players who are sufficiently far from the cube,
   * reducing unnecessary network and client load. The method is optimized through caching of player locations
   * and settings to minimize per-interval lookups.</p>
   *
   * @implNote This task is executed periodically every {@link PhysicsUtil#GLOW_TASK_INTERVAL_TICKS} ticks (default 10).
   * The interval balances visual responsiveness and server performance.
   */
  public void showCubeParticles() {
    long start = System.nanoTime();
    try {
      Collection<? extends Player> onlinePlayers = fcManager.getCachedPlayers();
      if (onlinePlayers.isEmpty() || cubes.isEmpty()) return;

      // Cache player data to avoid redundant lookups.
      Map<UUID, Location> playerLocations = new HashMap<>(onlinePlayers.size());
      Map<UUID, PlayerSettings> playerSettings = new HashMap<>(onlinePlayers.size());

      for (Player p : onlinePlayers) {
        playerLocations.put(p.getUniqueId(), p.getLocation());
        playerSettings.put(p.getUniqueId(), fcManager.getPlayerSettings(p));
      }

      // Iterate over each cube and determine whether particles should be shown.
      for (Slime cube : cubes) {
        if (cube == null || cube.isDead()) continue;
        Location cubeLoc = cube.getLocation();
        if (cubeLoc == null) continue;

        double x = cubeLoc.getX();
        double y = cubeLoc.getY() + PhysicsUtil.PARTICLE_Y_OFFSET; // Raise particle height slightly above cube.
        double z = cubeLoc.getZ();

        // Determine if any players are far enough away to require particle visibility.
        boolean anyFarPlayers = false;
        for (Map.Entry<UUID, PlayerSettings> entry : playerSettings.entrySet()) {
          PlayerSettings s = entry.getValue();
          if (s == null || !s.isParticlesEnabled()) continue;
          Location playerLoc = playerLocations.get(entry.getKey());
          if (playerLoc == null) continue;

          double dx = playerLoc.getX() - x;
          double dy = playerLoc.getY() - y;
          double dz = playerLoc.getZ() - z;

          // Check if player is beyond the visual threshold distance.
          if ((dx * dx + dy * dy + dz * dz) >= PhysicsUtil.DISTANCE_PARTICLE_THRESHOLD_SQUARED) {
            anyFarPlayers = true;
            break;
          }
        }
        if (!anyFarPlayers) continue;

        // Emit particle trails for players far from the cube.
        for (Player player : onlinePlayers) {
          UUID playerId = player.getUniqueId();
          Location playerLoc = playerLocations.get(playerId);
          if (playerLoc == null) continue;

          double dx = playerLoc.getX() - x;
          double dy = playerLoc.getY() - y;
          double dz = playerLoc.getZ() - z;

          double distanceSquared = dx * dx + dy * dy + dz * dz;
          // Skip players already close enough to see the cube directly.
          if (distanceSquared < PhysicsUtil.DISTANCE_PARTICLE_THRESHOLD_SQUARED) continue;
          // Skip players too far away (beyond 160 blocks).
          if (distanceSquared > PhysicsUtil.MAX_PARTICLE_DISTANCE_SQUARED) continue;

          PlayerSettings settings = playerSettings.get(playerId);
          if (settings == null || !settings.isParticlesEnabled()) continue;

          EnumParticle particle = settings.getParticle();

          // Render colorized Redstone particles.
          if (particle == EnumParticle.REDSTONE) {
            Color color = settings.getRedstoneColor();
            Utilities.sendParticle(player, EnumParticle.REDSTONE,
                x, y, z,
                color.getRed() / 255F, color.getGreen() / 255F, color.getBlue() / 255F,
                1.0F, 0);
          } else {
            // Render standard particle effect.
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
   * Clears and resets all data structures used by the physics engine.
   * <p>
   * This method should be called when unloading the plugin, restarting the system, or resetting the physics simulation.
   * It ensures no residual entity or state data persists between sessions, which could otherwise cause memory leaks
   * or inconsistent game behavior.
   * </p>
   *
   * <p>Execution is timed for performance monitoring and logs a warning if cleanup exceeds 1ms.</p>
   */
  public void cleanup() {
    long start = System.nanoTime();
    try {
      // Reset entity and state tracking structures.
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

      // Reset control flags and counters.
      matchesEnabled = true;
      hitDebugEnabled = false;
      tickCounter = 0;
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > 1) logger.send("group.fcfa", Lang.PREFIX_ADMIN.replace(null) + "Physics#cleanup() took " + ms + "ms");
    }
  }

  /**
   * Represents a discrete hit event applied to a cube (Slime entity) in the physics system.
   * <p>
   * Each {@code HitAction} encapsulates the cube being hit and the vector (velocity) applied as a result.
   * These are immutable objects, designed for safe usage in asynchronous or concurrent contexts such as
   * physics update queues.
   * </p>
   */
  @Getter
  @AllArgsConstructor
  static class HitAction {
    private final Slime cube;
    private final Vector velocity;
  }

  /**
   * Encapsulates a sound effect action within the physics system.
   * <p>
   * A {@code SoundAction} defines a sound event at a specific location, with optional player targeting.
   * It includes the sound type, playback volume, and pitch. If {@code player} is non-null, the sound is only
   * sent to that player; otherwise, it is broadcast to all nearby listeners.
   * </p>
   *
   * <p>This design allows flexible and fine-grained control over sound feedback, ensuring that auditory cues
   * are consistent with the player's experience and interaction with physics objects.</p>
   *
   * @see Sound
   */
  @Getter
  @AllArgsConstructor
  static class SoundAction {
    private final Location location;
    private final Player player;
    private final Sound sound;
    private final float volume;
    private final float pitch;

    /**
     * Checks whether this sound action is targeted at a specific player.
     *
     * @return {@code true} if the sound targets a player; {@code false} otherwise.
     */
    public boolean isPlayerTargeted() {
      return player != null;
    }
  }
}