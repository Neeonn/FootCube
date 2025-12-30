package io.github.divinerealms.footcube.tasks;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.DISTANCE_PARTICLE_THRESHOLD_SQUARED;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.GENERIC_PARTICLE_COUNT;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.GENERIC_PARTICLE_OFFSET;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.GENERIC_PARTICLE_SPEED;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.GLOW_TASK_INTERVAL_TICKS;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.MAX_PARTICLE_DISTANCE_SQUARED;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.PARTICLE_Y_OFFSET;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;

/**
 * Renders particle trails that visually follow cubes (Slime entities) for players who are far
 * enough away that they might not see the actual cube entity due to Minecraft 1.8.8 render distance
 * limitations.
 * <p>
 * This method improves visibility and game immersion by simulating cube motion with particles,
 * ensuring remote players can still perceive the ball's movement even outside their render
 * distance.
 * </p>
 *
 * <p><b>Performance considerations:</b> Particle effects are sent only to players who are
 * sufficiently far from the
 * cube, reducing unnecessary network and client load. The method is optimized through caching of
 * player locations and settings to minimize per-interval lookups.</p>
 *
 * @implNote This task is executed periodically every X ticks (default 10). The interval balances
 * visual responsiveness and server performance.
 */
public class ParticleTrailTask extends BaseTask {

  private final PhysicsData data;

  public ParticleTrailTask(FCManager fcManager) {
    super(fcManager, "ParticleTrail", GLOW_TASK_INTERVAL_TICKS, true);
    this.data = fcManager.getPhysicsData();
  }

  @Override
  protected void kaboom() {
    Collection<? extends Player> onlinePlayers = fcManager.getCachedPlayers();
    if (onlinePlayers.isEmpty() || data.getCubes().isEmpty()) {
      return;
    }

    // Cache player data
    Map<UUID, Location> playerLocations = new HashMap<>();
    Map<UUID, PlayerSettings> playerSettings = new HashMap<>();

    for (Player p : onlinePlayers) {
      playerLocations.put(p.getUniqueId(), p.getLocation());
      playerSettings.put(p.getUniqueId(), fcManager.getPlayerSettings(p));
    }

    for (Slime cube : data.getCubes()) {
      if (cube == null || cube.isDead()) {
        continue;
      }

      Location currentLoc = cube.getLocation();
      if (currentLoc == null) {
        continue;
      }

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
      int trailPoints = calculateTrailPoints(distanceMoved);

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

        if (playerLoc == null || settings == null || !settings.isParticlesEnabled()) {
          continue;
        }

        double dx = playerLoc.getX() - x;
        double dy = playerLoc.getY() - y;
        double dz = playerLoc.getZ() - z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;

        if (distanceSquared < DISTANCE_PARTICLE_THRESHOLD_SQUARED) {
          continue;
        }
        if (distanceSquared > MAX_PARTICLE_DISTANCE_SQUARED) {
          continue;
        }

        EnumParticle particle = settings.getParticle();

        // Linear interpolation between previous and current position
        renderTrail(player, particle, settings, trailPoints, prevX, prevY, prevZ, x, y, z);
      }

      // Update stored previous location for next frame
      data.getPreviousCubeLocations().put(cubeId, currentLoc.clone());
    }
  }

  private int calculateTrailPoints(double distanceMoved) {
    if (distanceMoved > 3.0) {
      return 5;
    } else {
      if (distanceMoved > 1.5) {
        return 4;
      } else {
        if (distanceMoved > 0.8) {
          return 3;
        } else {
          if (distanceMoved > 0.3) {
            return 2;
          } else {
            return 1;
          }
        }
      }
    }
  }

  private void renderTrail(Player player, EnumParticle particle, PlayerSettings settings,
      int trailPoints, double prevX, double prevY, double prevZ,
      double x, double y, double z) {
    for (int i = 0; i < trailPoints; i++) {
      double t = (double) i / Math.max(trailPoints - 1, 1);

      double trailX = prevX + (x - prevX) * t;
      double trailY = prevY + (y - prevY) * t;
      double trailZ = prevZ + (z - prevZ) * t;

      if (particle == EnumParticle.REDSTONE) {
        Color color = settings.getRedstoneColor();
        float fadeFactor = 0.6f + (float) (t * 0.4f);

        Utilities.sendParticle(player, EnumParticle.REDSTONE,
            trailX, trailY, trailZ,
            (color.getRed() / 255F) * fadeFactor,
            (color.getGreen() / 255F) * fadeFactor,
            (color.getBlue() / 255F) * fadeFactor,
            1.0F, 0);
      } else {
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
}