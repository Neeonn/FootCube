package io.github.divinerealms.footcube.physics;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.physics.utilities.PhysicsFormulae;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.logging.Level;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_HIT_DEBUG;

public class PhysicsEngine {
  private final FCManager fcManager;
  private final MatchManager matchManager;
  private final Logger logger;

  private final PhysicsData data;
  private final PhysicsSystem system;
  private final PhysicsFormulae formulae;

  public PhysicsEngine(FCManager fcManager) {
    this.fcManager = fcManager;
    this.matchManager = fcManager.getMatchManager();
    this.logger = fcManager.getLogger();

    this.data = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
    this.formulae = fcManager.getPhysicsFormulae();
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
  public void cubeProcess() {
    long start = System.nanoTime();
    try {
      // Skip processing if there are no active players or cubes.
      if (fcManager.getCachedPlayers().isEmpty() || data.getCubes().isEmpty()) return;

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
            Vector directionToCube = cubeLocation.toVector().subtract(cache.location.toVector()).setY(0).normalize();
            boolean isLookingAtBall = cache.direction.dot(directionToCube) > VELOCITY_DAMPENING_FACTOR;

            if (distance < MIN_RADIUS && cubeSpeed > MIN_SPEED_FOR_DAMPENING && !isLookingAtBall)
              newVelocity.multiply(VELOCITY_DAMPENING_FACTOR / cubeSpeed);

            // Compute the resulting power from player movement and cube velocity.
            double impactPower = cache.speed / PLAYER_SPEED_TOUCH_DIVISOR
                + Math.max(previousVelocity.length(), VECTOR_CHANGE_THRESHOLD)
                / CUBE_SPEED_TOUCH_DIVISOR;

            // Apply a horizontal directional force in the direction the player is facing.
            Vector push = cache.direction.clone().multiply(impactPower);
            if (cubeSpeed < LOW_VELOCITY_THRESHOLD) push.multiply(LOW_VELOCITY_PUSH_MULTIPLIER);
            newVelocity.add(push);

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
              if (dot > 0) {
                newVelocity.multiply(distance / cubeSpeed);
                cubeSpeed = newVelocity.length(); // Update speed for next anti-clipping interaction.
              }
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
    } catch (Exception e) {
      Bukkit.getLogger().log(Level.SEVERE, "Critical physics error: " + e.getMessage(), e);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsEngine#cubeProcess() &ftook &e" + ms + "ms");
    }
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

  private Map<UUID, PlayerPhysicsCache> buildPlayerCache() {
    Map<UUID, PlayerPhysicsCache> cache = new HashMap<>();
    for (Player player : fcManager.getCachedPlayers()) cache.put(player.getUniqueId(), new PlayerPhysicsCache(player, system, data));
    return cache;
  }

  /**
   * Maintenance task to be run at a lower frequency (e.g., every 5-10 seconds).
   * <p>
   * Cleans up expired touch data and other non-critical cache structures to
   * keep memory usage lean without interrupting the primary physics calculations.
   * </p>
   */
  public void touchesCleanup() {
    long start = System.nanoTime();
    try {
      long now = System.currentTimeMillis();
      if (!data.getLastTouches().isEmpty()) {
        data.getLastTouches().values().removeIf(playerTouches -> {
          playerTouches.entrySet().removeIf(entry ->
              (now - entry.getValue().getTimestamp()) > entry.getKey().getCooldown());
          return playerTouches.isEmpty();
        });
      }

      if (!data.getRaised().isEmpty()) data.getRaised().entrySet().removeIf(entry -> (now - entry.getValue()) > 1000L);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsEngine#touchesCleanup() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Handles player-specific updates like charge recovery.
   * Can be run at a lower frequency (e.g., 2-5 ticks) to save CPU.
   */
  public void playerUpdate() {
    long start = System.nanoTime();
    try {
      if (data.getCharges().isEmpty()) return;

      Iterator<Map.Entry<UUID, Double>> chargesIterator = data.getCharges().entrySet().iterator();
      while (chargesIterator.hasNext()) {
        Map.Entry<UUID, Double> entry = chargesIterator.next();
        UUID uuid = entry.getKey();
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) { chargesIterator.remove(); continue; }

        double currentCharge = entry.getValue();
        double recoveredCharge = CHARGE_BASE_VALUE - (CHARGE_BASE_VALUE - currentCharge) * CHARGE_RECOVERY_RATE;
        entry.setValue(recoveredCharge);

        player.setExp((float) recoveredCharge);
      }
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsEngine#playerUpdate() &ftook &e" + ms + "ms");
    }
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
   * @implNote This task is executed periodically every X ticks (default 10).
   * The interval balances visual responsiveness and server performance.
   */
  public void cubeParticles() {
    long start = System.nanoTime();
    try {
      Collection<? extends Player> onlinePlayers = fcManager.getCachedPlayers();
      if (onlinePlayers.isEmpty() || data.getCubes().isEmpty()) return;

      // Cache player data
      Map<UUID, Location> playerLocations = new HashMap<>();
      Map<UUID, PlayerSettings> playerSettings = new HashMap<>();

      for (Player p : onlinePlayers) {
        playerLocations.put(p.getUniqueId(), p.getLocation());
        playerSettings.put(p.getUniqueId(), fcManager.getPlayerSettings(p));
      }

      for (Slime cube : data.getCubes()) {
        if (cube == null || cube.isDead()) continue;

        Location currentLoc = cube.getLocation();
        if (currentLoc == null) continue;

        UUID cubeId = cube.getUniqueId();

        // Get stored previous location (from last particle update)
        Location previousLoc = data.getPreviousCubeLocations().get(cubeId);

        // If no previous location, use current (first frame)
        if (previousLoc == null) {
          previousLoc = currentLoc.clone();
          data.getPreviousCubeLocations().put(cubeId, previousLoc);
          continue; // Skip first frame to avoid rendering at same position
        }

        // Calculate distance moved to determine trail density
        double distanceMoved = currentLoc.distance(previousLoc);

        // Skip if ball barely moved (< 0.1 blocks in 0.1s = stationary)
        if (distanceMoved < 0.1) {
          data.getPreviousCubeLocations().put(cubeId, currentLoc.clone());
          continue;
        }

        // Adaptive trail points optimized for 2-tick interval
        // At 2 ticks: fast balls move ~2-4 blocks, slow balls move 0.2-1 blocks
        int trailPoints;
        if (distanceMoved > 3.0) trailPoints = 5;
        else if (distanceMoved > 1.5) trailPoints = 4;
        else if (distanceMoved > 0.8) trailPoints = 3;
        else if (distanceMoved > 0.3) trailPoints = 2;
        else trailPoints = 1;

        double x = currentLoc.getX();
        double y = currentLoc.getY() + PARTICLE_Y_OFFSET;
        double z = currentLoc.getZ();

        double prevX = previousLoc.getX();
        double prevY = previousLoc.getY() + PARTICLE_Y_OFFSET;
        double prevZ = previousLoc.getZ();

        // Emit particles for eligible players
        for (Player player : onlinePlayers) {
          UUID playerId = player.getUniqueId();
          Location playerLoc = playerLocations.get(playerId);
          PlayerSettings settings = playerSettings.get(playerId);

          if (playerLoc == null || settings == null || !settings.isParticlesEnabled()) continue;

          double dx = playerLoc.getX() - x;
          double dy = playerLoc.getY() - y;
          double dz = playerLoc.getZ() - z;
          double distanceSquared = dx * dx + dy * dy + dz * dz;

          if (distanceSquared < DISTANCE_PARTICLE_THRESHOLD_SQUARED) continue;
          if (distanceSquared > MAX_PARTICLE_DISTANCE_SQUARED) continue;

          EnumParticle particle = settings.getParticle();

          // Linear interpolation between previous and current position
          for (int i = 0; i < trailPoints; i++) {
            double t = (double) i / Math.max(trailPoints - 1, 1);

            // Lerp from previous to current
            double trailX = prevX + (x - prevX) * t;
            double trailY = prevY + (y - prevY) * t;
            double trailZ = prevZ + (z - prevZ) * t;

            if (particle == EnumParticle.REDSTONE) {
              Color color = settings.getRedstoneColor();
              // Fade from 0.6 to 1.0 (old to new)
              float fadeFactor = 0.6f + (float) (t * 0.4f);

              Utilities.sendParticle(player, EnumParticle.REDSTONE,
                  trailX, trailY, trailZ,
                  (color.getRed() / 255F) * fadeFactor,
                  (color.getGreen() / 255F) * fadeFactor,
                  (color.getBlue() / 255F) * fadeFactor,
                  1.0F, 0);
            } else {
              // Particle count: 60% at oldest point, 100% at newest
              int particleCount = (int) (GENERIC_PARTICLE_COUNT * (0.6 + t * 0.4));

              Utilities.sendParticle(player, particle,
                  trailX, trailY, trailZ,
                  GENERIC_PARTICLE_OFFSET,
                  GENERIC_PARTICLE_OFFSET,
                  GENERIC_PARTICLE_OFFSET,
                  GENERIC_PARTICLE_SPEED,
                  Math.max(1, particleCount));
            }
          }
        }

        // Update stored previous location for next frame
        data.getPreviousCubeLocations().put(cubeId, currentLoc.clone());
      }
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsEngine#cubeParticles() &ftook &e" + ms + "ms");
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
      data.getCubes().clear();
      data.getCubesToRemove().clear();

      data.getVelocities().clear();
      data.getRaised().clear();
      data.getSpeed().clear();
      data.getCharges().clear();
      fcManager.getPlayerSettings().clear();
      data.getLastAction().clear();
      data.getCubeHits().clear();
      data.getSoundQueue().clear();
      data.getButtonCooldowns().clear();
      data.getLastTouches().clear();
      data.getPreviousCubeLocations().clear();

      // Reset control flags and counters.
      data.hitDebugEnabled = false;
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsEngine#cleanup() &ftook &e" + ms + "ms");
    }
  }
}