package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsFormulae;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.github.divinerealms.footcube.configs.Lang.HITDEBUG_VELOCITY_CAP;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_HIT_DEBUG;

/**
 * Task responsible for processing the physics of cubes (slimes) in the game.
 * <p>
 * This task runs at a fixed interval, updating cube velocities based on player interactions,
 * handling collisions with walls and the ground, applying air drag, and preventing clipping through players.
 * It also manages sound effects for impacts and ensures cubes do not exceed maximum allowed speeds.
 * </p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Processes all active cubes each tick, adjusting their velocities based on player proximity and actions.</li>
 *   <li>Handles realistic collision responses with walls and the ground, including bounce effects.</li>
 *   <li>Implements anti-clipping logic to prevent cubes from passing through players at high speeds.</li>
 *   <li>Queues sound effects for significant impacts to enhance gameplay feedback.</li>
 *   <li>Caches player physics data per tick to optimize performance during cube processing.</li>
 * </ul>
 */
public class PhysicsTask extends BaseTask {
  private final PhysicsData data;
  private final PhysicsSystem system;
  private final PhysicsFormulae formulae;
  private final MatchManager matchManager;

  public PhysicsTask(FCManager fcManager) {
    super(fcManager, "Physics", PHYSICS_TASK_INTERVAL_TICKS, false);
    this.data = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
    this.formulae = fcManager.getPhysicsFormulae();
    this.matchManager = fcManager.getMatchManager();
  }

  @Override
  protected void kaboom() {
    // Skip processing if there are no active players or cubes.
    if (fcManager.getCachedPlayers().isEmpty() || data.getCubes().isEmpty()) return;

    // Build player cache once per tick for all cubes to reuse.
    Map<UUID, PlayerPhysicsCache> playerCache = buildPlayerCache();

    // Main cube processing loop.
    for (Slime cube : data.getCubes()) {
      // --- Cube validity check ---
      if (cube.isDead()) { data.getCubesToRemove().add(cube); continue; }

      // --- Initialization and state retrieval ---
      UUID cubeId = cube.getUniqueId();
      Location cubeLocation = cube.getLocation();
      if (cubeLocation == null) continue;

      // Retrieve or initialize previous velocity for collision calculations.
      Vector previousVelocity = data.getVelocities().get(cubeId);
      // Initialize if this is the first tick tracking this cube.
      if (previousVelocity == null) {
        previousVelocity = cube.getVelocity().clone();
        data.getVelocities().put(cubeId, previousVelocity);
      }

      // --- Player interaction and velocity adjustment ---
      Vector newVelocity = cube.getVelocity();
      boolean wasMoved = false, playSound = false;

      // Store player interaction data to avoid recalculation in anti-clipping.
      Map<UUID, PlayerInteraction> playerInteractions = new HashMap<>();
      for (Player player : fcManager.getCachedPlayers()) {
        UUID playerId = player.getUniqueId();

        PlayerPhysicsCache cache = playerCache.get(playerId);
        if (cache == null || !cache.canInteract()) continue;

        // --- Player proximity and touch detection ---
        // Determines if the player is close enough to directly affect the cube.
        double distance = formulae.getDistance(cubeLocation, cache.location);

        // Skip players beyond 3x hit radius for performance,
        // as they cannot meaningfully interact with the cube.
        if (distance > HIT_RADIUS * 3) continue;

        // Cache the interaction data for later use.
        playerInteractions.put(playerId, new PlayerInteraction(player, cache, distance));

        // Skip if player cannot interact or is out of touch range.
        if (distance < HIT_RADIUS) {
          double cubeSpeed = newVelocity.length(); // Current speed of the cube.

          // Apply speed dampening if cube is very close to player for dribbling effect.
          if (distance < MIN_RADIUS && cubeSpeed > MIN_SPEED_FOR_DAMPENING)
            newVelocity.multiply(DRIBBLE_SPEED_LIMIT / cubeSpeed); // Apply speed cap for dribbling effect.

          // Compute the resulting power from player movement and cube velocity.
          double previousSpeed = Math.max(previousVelocity.length(), VECTOR_CHANGE_THRESHOLD);
          double impactPower = cache.speed / PLAYER_SPEED_TOUCH_DIVISOR + previousSpeed / CUBE_SPEED_TOUCH_DIVISOR;
          Vector push = cache.direction.clone().multiply(impactPower); // Directional push vector from player to cube.
          newVelocity.add(cubeSpeed < LOW_VELOCITY_THRESHOLD ? push.multiply(LOW_VELOCITY_PUSH_MULTIPLIER) : push);

          // Register the touch interaction with the organization system.
          matchManager.kick(player);
          wasMoved = true;

          // Trigger sound effect if impact force exceeds threshold.
          if (impactPower > MIN_SOUND_POWER) playSound = true;
        }
      }

      // --- Handle wall collisions and air drag effects ---

      // X-axis collision and drag adjustment.
      // If the cube stops moving horizontally (X=0), bounce it back with reduced energy.
      if (newVelocity.getX() == 0) {
        newVelocity.setX(-previousVelocity.getX() * WALL_BOUNCE_FACTOR); // Reverse and reduce X velocity on collision.
        if (Math.abs(previousVelocity.getX()) > BOUNCE_THRESHOLD) playSound = true; // Trigger sound if impact force is strong enough.

        // If cube wasn’t recently kicked and velocity change is small, apply gradual air drag slowdown.
      } else if (!wasMoved && !cube.isOnGround() && Math.abs(previousVelocity.getX() - newVelocity.getX()) < VECTOR_CHANGE_THRESHOLD)
        newVelocity.setX(previousVelocity.getX() * AIR_DRAG_FACTOR); // Apply air drag.

      // Z-axis collision and drag adjustment (mirrors X-axis logic).
      if (newVelocity.getZ() == 0) {
        newVelocity.setZ(-previousVelocity.getZ() * WALL_BOUNCE_FACTOR); // Reverse and reduce Z velocity on collision.
        if (Math.abs(previousVelocity.getZ()) > BOUNCE_THRESHOLD) playSound = true; // Trigger sound if impact force is strong enough.

        // If cube wasn’t recently kicked and velocity change is small, apply gradual air drag slowdown.
      } else if (!wasMoved && !cube.isOnGround() && Math.abs(previousVelocity.getZ() - newVelocity.getZ()) < VECTOR_CHANGE_THRESHOLD)
        newVelocity.setZ(previousVelocity.getZ() * AIR_DRAG_FACTOR); // Apply air drag.

      // Y-axis bounce (vertical collision against floor or ceiling).
      // This ensures realistic vertical rebound, preventing velocity loss bugs on impact.
      if (newVelocity.getY() < 0 && previousVelocity.getY() < 0 && previousVelocity.getY() < newVelocity.getY() - VERTICAL_BOUNCE_THRESHOLD) {
        newVelocity.setY(-previousVelocity.getY() * WALL_BOUNCE_FACTOR); // Reverse and reduce Y velocity on downward collision.
        if (Math.abs(previousVelocity.getY()) > BOUNCE_THRESHOLD) playSound = true; // Trigger sound if impact force is strong enough.
      }

      // --- Anticipatory Hover Effect ---
      // Applies a gentle upward force when players are nearby to prevent the cube from sticking to the ground.
      // This effect is only applied if the cube is on the ground and wasn't recently manually raised.
      double cubeY = cubeLocation.getY();
      int blockBelowY = (int) Math.floor(cubeY - 0.3);

      Location blockBelowLocation = cubeLocation.clone();
      blockBelowLocation.setY(blockBelowY);
      boolean isSolidBlockBelow = blockBelowLocation.getBlock().getType().isSolid();
      double distanceToGround = cubeY - (blockBelowY + 1);

      boolean isActuallyGrounded = isSolidBlockBelow && distanceToGround < 0.2 && distanceToGround > -0.1;
      boolean isSettledOnGround = Math.abs(newVelocity.getY()) < 0.15;

      if (isActuallyGrounded && isSettledOnGround) {
        boolean hasClosePlayer = false;
        double closestPlayerDistance = Double.MAX_VALUE;
        double hoverForce, bounce = MIN_BOUNCE_VELOCITY_Y * 0.4;

        // Find the closest player and determine if anyone is in hover range.
        for (PlayerInteraction interaction : playerInteractions.values()) {
          if (interaction.distance < closestPlayerDistance) closestPlayerDistance = interaction.distance; // Update closest distance.
          if (interaction.distance < HIT_RADIUS * 2) hasClosePlayer = true; // Define hover range as twice the hit radius (about 2.4 blocks).
        }

        // If players are nearby, apply a gentle upward force.
        if (hasClosePlayer) {
          double hoverRange = HIT_RADIUS * 2; // Define hover range.
          double proximityFactor = 1 - (closestPlayerDistance / hoverRange); // Calculate proximity factor (0 to 1).
          hoverForce = MIN_BOUNCE_VELOCITY_Y * 1 * proximityFactor; // Scale lift based on proximity.

          // Only apply the lift if the cube isn't already moving upward significantly.
          // This prevents the hover from interfering with natural bounces or kicks.
          if (hoverForce < bounce) hoverForce = bounce;
        } else hoverForce = bounce;

        // Apply the lift if there are players nearby and if the cube isn't already hovering.
        if (newVelocity.getY() < hoverForce) newVelocity.setY(hoverForce);
      }

      // Queue impact sound effect if any significant collision occurred.
      if (playSound) system.queueSound(cubeLocation);

      // --- Anti-clipping / Proximity Logic ---
      // Prevents the cube from passing through players at high speeds.
      double cubeSpeed = newVelocity.length();
      if (cubeSpeed > VECTOR_CHANGE_THRESHOLD) {
        Vector cubePos = cubeLocation.toVector(); // Precompute cube position vector for efficiency.
        double minScaleFactor = 1; // Track minimum scale factor needed to prevent clipping.

        // Evaluate each player interaction for potential clipping.
        for (PlayerInteraction interaction : playerInteractions.values()) {
          if (interaction == null || interaction.cache == null) continue;

          // Retrieve cached player data and distance.
          PlayerPhysicsCache cache = interaction.cache;
          double distance = interaction.distance;

          // Skip if player is too far away for clipping to be possible.
          if (distance >= cubeSpeed * PROXIMITY_THRESHOLD_MULTIPLIER) continue;

          // Check vertical alignment with player height.
          double playerLocationY = cache.location.getY();
          Vector projectedNextPos = cubePos.clone().add(newVelocity);

          // Check if the cube's vertical position aligns with player's height.
          boolean withinY = (cubePos.getY() < playerLocationY + PLAYER_HEAD_LEVEL &&
              cubePos.getY() > playerLocationY - PLAYER_FOOT_LEVEL)
              || (projectedNextPos.getY() < playerLocationY + PLAYER_HEAD_LEVEL &&
              projectedNextPos.getY() > playerLocationY - PLAYER_FOOT_LEVEL);

          // If vertically aligned, check if the cube's path intersects player's collision radius.
          if (withinY && formulae.getPerpendicularDistance(newVelocity, cubePos, interaction.player) < MIN_RADIUS) {
            Vector toPlayer = cache.location.toVector().subtract(cubePos).setY(0).normalize(); // Horizontal vector to player.
            Vector ballDirection = new Vector(newVelocity.getX(), 0, newVelocity.getZ()).normalize(); // Horizontal movement direction.
            double dot = toPlayer.dot(ballDirection); // Cosine of angle between cube movement and direction to player.

            // Scale back velocity if moving toward player to prevent clipping.
            if (dot > ANTI_CLIP_DOT_THRESHOLD) {
              double scaleFactor = distance / cubeSpeed; // Scale factor based on distance and speed.
              if (scaleFactor < minScaleFactor) minScaleFactor = scaleFactor; // Track minimum scale factor needed.
            }
          }
        }

        // Apply the most restrictive scale factor to the cube's velocity.
        if (minScaleFactor < 1) newVelocity.multiply(minScaleFactor);
      }

      // --- Velocity Capping ---
      // If the ball exceeds MAX_KP, we scale the vector back to prevent "unreal" speeds.
      double finalSpeed = newVelocity.length(); // Calculate final speed after all adjustments.
      if (finalSpeed > MAX_KP) {
        newVelocity.multiply(MAX_KP / finalSpeed); // Scale back to MAX_KP.
        // Log violation to players with debugging permissions.
        logger.send(PERM_HIT_DEBUG, HITDEBUG_VELOCITY_CAP,
            String.format("%.2f", finalSpeed), String.valueOf(MAX_KP)
        );
      }

      // Apply final computed velocity to the cube and update its tracked state.
      cube.setVelocity(newVelocity);
      data.getVelocities().put(cubeId, newVelocity.clone());
    }

    // Finalize scheduled physics actions.
    system.scheduleSound(); // Dispatch queued sound events to players.
    system.scheduleCubeRemoval(); // Safely remove dead or invalid cube entities.
  }

  /**
   * Builds a cache of player physics data for the current tick.
   * This cache is reused for all cube-player interactions during this tick,
   * reducing redundant calculations and improving performance.
   *
   * @return a map of player UUIDs to their corresponding physics cache
   */
  private Map<UUID, PlayerPhysicsCache> buildPlayerCache() {
    Map<UUID, PlayerPhysicsCache> cache = new HashMap<>();
    for (Player player : fcManager.getCachedPlayers()) {
      if (player == null || !player.isOnline()) continue;
      cache.put(player.getUniqueId(), new PlayerPhysicsCache(player, system, data));
    }
    return cache;
  }

  /**
   * Temporary structure to store player interaction data during a single cube's processing.
   * Prevents recalculating distances and player lookups in the anti-clipping phase.
   *
   * <p><b>Lifecycle:</b> Created during touch detection, reused in anti-clipping,
   * then discarded at the end of each cube's processing.</p>
   */
  private static class PlayerInteraction {
    final Player player;
    final PlayerPhysicsCache cache;
    final double distance;

    PlayerInteraction(Player player, PlayerPhysicsCache cache, double distance) {
      this.player = player;
      this.cache = cache;
      this.distance = distance;
    }
  }

  /**
   * Immutable cache containing pre-calculated physics data for a player during a single tick.
   * <p>
   * This cache reduces redundant calculations when processing multiple cubes against the same player,
   * improving performance by storing commonly accessed values like location, direction, and eligibility.
   * </p>
   *
   * <p><b>Lifecycle:</b> Created once per player per tick in {@code buildPlayerCache()},
   * then reused for all cube-player interactions during that tick.</p>
   */
  private static class PlayerPhysicsCache {
    final Location location;
    final Vector direction;
    final double speed;
    final boolean isIneligible;
    final UUID playerId;

    PlayerPhysicsCache(Player player, PhysicsSystem system, PhysicsData data) {
      this.playerId = player.getUniqueId();
      this.location = player.getLocation();
      this.direction = location.getDirection().setY(0).normalize();
      this.speed = data.getSpeed().getOrDefault(playerId, 1.0D);
      this.isIneligible = system.notAllowedToInteract(player) || system.isAFK(player);
    }

    /**
     * Checks if this player can interact with cubes during this physics tick.
     *
     * @return true if the player is eligible for cube interactions
     */
    public boolean canInteract() {
      return !isIneligible;
    }
  }
}