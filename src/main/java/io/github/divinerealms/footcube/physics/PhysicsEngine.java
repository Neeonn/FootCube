package io.github.divinerealms.footcube.physics;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.core.TouchType;
import io.github.divinerealms.footcube.managers.Utilities;
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

import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;

public class PhysicsEngine {
  private final FCManager fcManager;
  private final Organization org;
  private final Logger logger;

  private final PhysicsData data;
  private final PhysicsSystem system;
  private final PhysicsFormulae formulae;

  public PhysicsEngine(FCManager fcManager) {
    this.fcManager = fcManager;
    this.org = fcManager.getOrg();
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
      if (fcManager.getCachedPlayers().isEmpty()) return;
      if (data.getCubes().isEmpty()) return;

      ++data.tickCounter;
      // Remove players from the 'lastTouches' cache if their timeout expired.
      if (data.tickCounter % CLEANUP_LAST_TOUCHES_INTERVAL == 0) system.cleanupExpiredTouches();

      // Regenerate player charge values gradually, visualized via the experience bar.
      Iterator<Map.Entry<UUID, Double>> chargesIterator = data.getCharges().entrySet().iterator();
      while (chargesIterator.hasNext()) {
        Map.Entry<UUID, Double> entry = chargesIterator.next();
        UUID uuid = entry.getKey();
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) { chargesIterator.remove(); continue; }

        double currentCharge = entry.getValue();
        double recoveredCharge = CHARGE_BASE_VALUE - (CHARGE_BASE_VALUE - currentCharge) * CHARGE_RECOVERY_RATE;
        entry.setValue(recoveredCharge);

        // Only update player XP every few ticks to reduce overhead.
        if (data.tickCounter % EXP_UPDATE_INTERVAL_TICKS == 0) player.setExp((float) recoveredCharge);
      }

      // Main cube processing loop.
      Iterator<Slime> cubeIterator = data.getCubes().iterator();
      while (cubeIterator.hasNext()) {
        Slime cube = cubeIterator.next();
        if (cube.isDead()) { data.getCubesToRemove().add(cube); cubeIterator.remove(); continue; }

        UUID cubeId = cube.getUniqueId();
        Location cubeLocation = cube.getLocation();

        Vector previousVelocity = data.getVelocities().getOrDefault(cubeId, cube.getVelocity().clone());
        Vector newVelocity = cube.getVelocity().clone();

        boolean wasMoved = false, playSound = false;
        // Process all nearby entities within hit range.
        double distance = -1, cubeSpeed = -1;
        List<Entity> nearbyEntities = cube.getNearbyEntities(PLAYER_CLOSE, PLAYER_CLOSE, PLAYER_CLOSE);
        for (Entity entity : nearbyEntities) {
          if (!(entity instanceof Player)) continue;
          Player player = (Player) entity;
          UUID playerId = player.getUniqueId();
          if (system.notAllowedToInteract(player) || system.isAFK(player)) continue;

          // --- Player proximity and touch detection ---
          // Determines if the player is close enough to directly affect the cube.
          Location playerLocation = player.getLocation();
          distance = formulae.getDistance(cubeLocation, playerLocation);
          if (distance < HIT_RADIUS) {
            cubeSpeed = newVelocity.length();
            // Dampen ball speed if inside proximity and moving too fast to prevent overshoot.
            if (distance < MIN_RADIUS && cubeSpeed > MIN_SPEED_FOR_DAMPENING)
              newVelocity.multiply(VELOCITY_DAMPENING_FACTOR / cubeSpeed);

            // Compute the resulting power from player movement and cube velocity.
            double impactPower = data.getSpeed().getOrDefault(playerId, 1D) / PLAYER_SPEED_TOUCH_DIVISOR
                + Math.max(previousVelocity.length(), VECTOR_CHANGE_THRESHOLD) / CUBE_SPEED_TOUCH_DIVISOR;

            // Apply a horizontal directional force in the direction the player is facing.
            Vector push = playerLocation.getDirection().setY(0).normalize().multiply(impactPower);
            newVelocity.add(cubeSpeed < LOW_VELOCITY_THRESHOLD ? push.multiply(LOW_VELOCITY_PUSH_MULTIPLIER) : push);

            // Register the touch interaction with the organization system.
            org.ballTouch(player, TouchType.MOVE);
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

        for (Entity entity : nearbyEntities) {
          if (!(entity instanceof Player)) continue;
          Player player = (Player) entity;
          if (system.notAllowedToInteract(player) || system.isAFK(player)) continue;

          Location playerLocation = player.getLocation();
          distance = distance != -1 ? distance : formulae.getDistance(cubeLocation, playerLocation);
          cubeSpeed = cubeSpeed != -1 ? cubeSpeed : newVelocity.length();
          if (distance < cubeSpeed * PROXIMITY_THRESHOLD_MULTIPLIER) {
            Vector cubePos = cubeLocation.toVector();
            Vector projectedNextPos = (new Vector(cubePos.getX(), cubePos.getY(), cubePos.getZ())).add(newVelocity);

            // Calculate directional alignment between cube velocity and player.
            boolean movingTowardPlayer = true;
            Vector directionToPlayer = new Vector(playerLocation.getX() - cubePos.getX(), 0, playerLocation.getZ() - cubePos.getZ());
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
            double playerY = playerLocation.getY();
            boolean withinVerticalRange = cubePos.getY() < playerY + PLAYER_HEAD_LEVEL
                && cubePos.getY() > playerY - PLAYER_FOOT_LEVEL
                && projectedNextPos.getY() < playerY + PLAYER_HEAD_LEVEL
                && projectedNextPos.getY() > playerY - PLAYER_FOOT_LEVEL;

            // --- Collision line proximity correction ---
            // Prevents the cube from clipping through the player when moving directly toward them.
            if (movingTowardPlayer && withinVerticalRange) {
              double velocityX = newVelocity.getX();
              if (Math.abs(velocityX) < TOLERANCE_VELOCITY_CHECK) continue;

              // Reduce velocity to avoid tunneling effect when too close.
              if (formulae.getPerpendicularDistance(newVelocity, cubePos, player) < MIN_RADIUS)
                newVelocity.multiply(distance / cubeSpeed);
            }
          }
        }

        // Apply final computed velocity to the cube and update its tracked state.
        cube.setVelocity(newVelocity);
        data.getVelocities().put(cubeId, newVelocity);
      }

      // Finalize scheduled physics actions.
      system.scheduleSound();// Dispatch queued sound events to players.
      system.scheduleCubeRemoval(); // Safely remove dead or invalid cube entities.
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsEngine#cubeProcess() &ftook &e" + ms + "ms");
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

      // Cache player data to avoid redundant lookups.
      Map<UUID, Location> playerLocations = new HashMap<>(onlinePlayers.size());
      Map<UUID, PlayerSettings> playerSettings = new HashMap<>(onlinePlayers.size());

      for (Player p : onlinePlayers) {
        playerLocations.put(p.getUniqueId(), p.getLocation());
        playerSettings.put(p.getUniqueId(), fcManager.getPlayerSettings(p));
      }

      // Iterate over each cube and determine whether particles should be shown.
      for (Slime cube : data.getCubes()) {
        if (cube == null || cube.isDead()) continue;
        Location cubeLoc = cube.getLocation();
        if (cubeLoc == null) continue;

        double x = cubeLoc.getX();
        double y = cubeLoc.getY() + PARTICLE_Y_OFFSET; // Raise particle height slightly above cube.
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
          if ((dx * dx + dy * dy + dz * dz) >= DISTANCE_PARTICLE_THRESHOLD_SQUARED) {
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
          if (distanceSquared < DISTANCE_PARTICLE_THRESHOLD_SQUARED) continue;
          // Skip players too far away (beyond 160 blocks).
          if (distanceSquared > MAX_PARTICLE_DISTANCE_SQUARED) continue;

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
                GENERIC_PARTICLE_OFFSET,
                GENERIC_PARTICLE_OFFSET,
                GENERIC_PARTICLE_OFFSET,
                GENERIC_PARTICLE_SPEED,
                GENERIC_PARTICLE_COUNT);
          }
        }
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

      // Reset control flags and counters.
      data.matchesEnabled = true;
      data.hitDebugEnabled = false;
      data.tickCounter = 0;
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsEngine#cleanup() &ftook &e" + ms + "ms");
    }
  }
}