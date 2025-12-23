package io.github.divinerealms.footcube.tasks;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsFormulae;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_HIT_DEBUG;

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
public class CubeProcessTask extends BaseTask {
  private final PhysicsData data;
  private final PhysicsSystem system;
  private final PhysicsFormulae formulae;
  private final MatchManager matchManager;

  public CubeProcessTask(FCManager fcManager) {
    super(fcManager, "CubeProcess", PHYSICS_TASK_INTERVAL_TICKS, false);
    this.data = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
    this.formulae = fcManager.getPhysicsFormulae();
    this.matchManager = fcManager.getMatchManager();
  }

  @Override
  protected void execute() {
    // Skip processing if there are no active players or cubes.
    if (fcManager.getCachedPlayers().isEmpty() || data.getCubes().isEmpty()) return;

    data.tickCount++;

    // Build player cache once per tick for all cubes to reuse.
    Map<UUID, PlayerPhysicsCache> playerCache = buildPlayerCache();

    // Main cube processing loop.
    for (Slime cube : data.getCubes()) {
      if (cube.isDead()) { data.getCubesToRemove().add(cube); continue; }

      UUID cubeId = cube.getUniqueId();
      Location cubeLocation = cube.getLocation();
      if (cubeLocation == null) continue;

      Vector previousVelocity = data.getVelocities().get(cubeId);
      if (previousVelocity == null) {
        previousVelocity = cube.getVelocity().clone();
        data.getVelocities().put(cubeId, previousVelocity);
      }

      Vector newVelocity = cube.getVelocity();
      boolean wasMoved = false, playSound = false;

      // Process all nearby entities within hit range.
      List<Entity> nearbyEntities = cube.getNearbyEntities(PLAYER_CLOSE, PLAYER_CLOSE, PLAYER_CLOSE);

      // Store player interaction data to avoid recalculation in anti-clipping.
      Map<UUID, PlayerInteraction> playerInteractions = new HashMap<>();

      for (Entity entity : nearbyEntities) {
        if (!(entity instanceof Player)) continue;
        Player player = (Player) entity;
        UUID playerId = player.getUniqueId();

        PlayerPhysicsCache cache = playerCache.get(playerId);
        if (cache == null || !cache.canInteract()) continue;

        // --- Player proximity and touch detection ---
        // Determines if the player is close enough to directly affect the cube.
        double distance = formulae.getDistance(cubeLocation, cache.location);
        playerInteractions.put(playerId, new PlayerInteraction(player, cache, distance));

        if (distance < HIT_RADIUS) {
          double cubeSpeed = newVelocity.length();

          // Dampen ball speed if inside proximity and moving too fast to prevent overshoot.
          if (distance < MIN_RADIUS && cubeSpeed > MIN_SPEED_FOR_DAMPENING)
            newVelocity.multiply(VELOCITY_DAMPENING_FACTOR);

          // Compute the resulting power from player movement and cube velocity.
          double impactPower = cache.speed / PLAYER_SPEED_TOUCH_DIVISOR
              + Math.max(previousVelocity.length(), VECTOR_CHANGE_THRESHOLD) / CUBE_SPEED_TOUCH_DIVISOR;

          // Apply a horizontal directional force in the direction the player is facing.
          Vector push = cache.direction.clone().multiply(impactPower);
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
        newVelocity.setX(-previousVelocity.getX() * WALL_BOUNCE_FACTOR);
        if (Math.abs(previousVelocity.getX()) > BOUNCE_THRESHOLD) playSound = true; // Trigger sound if impact force is strong enough.

        // If cube wasn’t recently kicked and velocity change is small, apply gradual air drag slowdown.
      } else if (!wasMoved && !cube.isOnGround() && Math.abs(previousVelocity.getX() - newVelocity.getX()) < VECTOR_CHANGE_THRESHOLD)
        newVelocity.setX(previousVelocity.getX() * AIR_DRAG_FACTOR);

      // Z-axis collision and drag adjustment (mirrors X-axis logic).
      if (newVelocity.getZ() == 0) {
        newVelocity.setZ(-previousVelocity.getZ() * WALL_BOUNCE_FACTOR);
        if (Math.abs(previousVelocity.getZ()) > BOUNCE_THRESHOLD) playSound = true;

        // If cube wasn’t recently kicked and velocity change is small, apply gradual air drag slowdown.
      } else if (!wasMoved && !cube.isOnGround() && Math.abs(previousVelocity.getZ() - newVelocity.getZ()) < VECTOR_CHANGE_THRESHOLD)
        newVelocity.setZ(previousVelocity.getZ() * AIR_DRAG_FACTOR);

      // Y-axis bounce (vertical collision against floor or ceiling).
      // This ensures realistic vertical rebound, preventing velocity loss bugs on impact.
      if (newVelocity.getY() < 0 && previousVelocity.getY() < 0 && previousVelocity.getY() < newVelocity.getY() - VERTICAL_BOUNCE_THRESHOLD) {
        newVelocity.setY(-previousVelocity.getY() * WALL_BOUNCE_FACTOR);
        if (Math.abs(previousVelocity.getY()) > BOUNCE_THRESHOLD) playSound = true;

        // If cube is on the ground, add a small bounce so it doesn't stay glued to the ground.
      } else if (!system.wasRecentlyRaised(cubeId) && cube.isOnGround()) {
        double bounceY = -previousVelocity.getY() * WALL_BOUNCE_FACTOR;
        newVelocity.setY(Math.max(MIN_BOUNCE_VELOCITY_Y, Math.abs(bounceY)));
      }

      // Queue impact sound effect if any significant collision occurred.
      if (playSound) system.queueSound(cubeLocation);

      double cubeSpeed = newVelocity.length();

      // --- Anti-clipping / Proximity Logic ---
      // Prevents the cube from passing through players at high speeds.
      if (cubeSpeed > VECTOR_CHANGE_THRESHOLD) {
        Vector cubePos = cubeLocation.toVector();

        for (PlayerInteraction interaction : playerInteractions.values()) {
          if (interaction == null || interaction.cache == null) continue;

          PlayerPhysicsCache cache = interaction.cache;
          double distance = interaction.distance;

          // Skip if player is too far away for clipping to be possible.
          if (distance >= cubeSpeed * PROXIMITY_THRESHOLD_MULTIPLIER) continue;

          double playerLocationY = cache.location.getY();
          Vector projectedNextPos = cubePos.clone().add(newVelocity);

          // Check if the cube's vertical position aligns with player's height.
          boolean withinY = (cubePos.getY() < playerLocationY + PLAYER_HEAD_LEVEL &&
              cubePos.getY() > playerLocationY - PLAYER_FOOT_LEVEL)
              || (projectedNextPos.getY() < playerLocationY + PLAYER_HEAD_LEVEL &&
              projectedNextPos.getY() > playerLocationY - PLAYER_FOOT_LEVEL);

          // If vertically aligned, check if the cube's path intersects player's collision radius.
          if (withinY && formulae.getPerpendicularDistance(newVelocity, cubePos, interaction.player) < MIN_RADIUS) {
            Vector toPlayer = cache.location.toVector().subtract(cubePos).setY(0).normalize();
            Vector ballDirection = new Vector(newVelocity.getX(), 0, newVelocity.getZ()).normalize();
            double dot = toPlayer.dot(ballDirection);

            // Scale back velocity if moving toward player to prevent clipping.
            if (dot > ANTI_CLIP_DOT_THRESHOLD) newVelocity.multiply(distance / cubeSpeed);
          }
        }
      }

      // --- Velocity Capping ---
      // If the ball exceeds MAX_KP, we scale the vector back to prevent "unreal" speeds.
      double finalSpeed = newVelocity.length();
      if (finalSpeed > MAX_KP) {
        newVelocity.multiply(MAX_KP / finalSpeed);
        // Log violation to players with debugging permissions.
        logger.send(PERM_HIT_DEBUG, Lang.HITDEBUG_VELOCITY_CAP.replace(new String[]{String.format("%.2f", finalSpeed), String.valueOf(MAX_KP)}));
      }

      // Apply final computed velocity to the cube and update its tracked state.
      cube.setVelocity(newVelocity);
      data.getVelocities().put(cubeId, newVelocity.clone());
    }

    // Finalize scheduled physics actions.
    system.scheduleSound(); // Dispatch queued sound events to players.
    system.scheduleCubeRemoval(); // Safely remove dead or invalid cube entities.
  }

  private Map<UUID, PlayerPhysicsCache> buildPlayerCache() {
    Map<UUID, PlayerPhysicsCache> cache = new HashMap<>();
    for (Player player : fcManager.getCachedPlayers())
      cache.put(player.getUniqueId(), new PlayerPhysicsCache(player, system, data));
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

    /**
     * Constructs a physics cache snapshot for the given player.
     *
     * @param player the player to cache data for
     * @param system physics system for eligibility checks
     * @param data physics data store containing player speeds
     */
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

    @Override
    public String toString() {
      return String.format(
          "PlayerPhysicsCache[player=%s, speed=%.2f, eligible=%b]",
          playerId, speed, !isIneligible
      );
    }
  }
}