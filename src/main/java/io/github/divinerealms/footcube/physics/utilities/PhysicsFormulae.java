package io.github.divinerealms.footcube.physics.utilities;

import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_HIT_DEBUG;

@SuppressWarnings("unused")
public class PhysicsFormulae {
  private final Logger logger;

  public PhysicsFormulae(Logger logger) {
    this.logger = logger;
  }

  /**
   * Calculates the Euclidean distance between two {@link Location} points,
   * accounting for player and cube height offsets to ensure accurate collision detection.
   * <p>
   * This method is primarily used for short-range interaction checks (e.g., player–cube contact),
   * and adjusts vertical distance to better reflect the cube’s physical hitbox rather than its entity base.
   * </p>
   *
   * <p><b>Implementation Details:</b></p>
   * <ul>
   *   <li>Temporarily offsets {@code locA} by -1 block on the Y-axis to match player height.</li>
   *   <li>Applies a 0.25-block downward adjustment for cube height alignment.</li>
   *   <li>Clamps negative Y-differences to zero to prevent invalid proximity readings.</li>
   * </ul>
   *
   * <p><b>Performance:</b> Uses direct arithmetic and {@link Math#sqrt(double)} for true Euclidean distance.
   * Prefer {@link #getDistanceSquared(Location, Location)} for large-scale or repeated calculations.</p>
   *
   * @param locA The first location (typically the player).
   * @param locB The second location (typically the cube or ball).
   * @return The real-world distance between the two adjusted positions.
   */
  public double getDistance(Location locA, Location locB) {
    long start = System.nanoTime();
    try {
      Location locAnew = locA.clone().add(0, -1, 0);
      double dx = Math.abs(locAnew.getX() - locB.getX());
      double dy = Math.abs(locAnew.getY() - locB.getY() - 0.25) - 1.25;
      if (dy < 0) dy = 0;
      double dz = Math.abs(locAnew.getZ() - locB.getZ());
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send(PERM_HIT_DEBUG, "{prefix-admin}&dPhysicsFormulae#getDistance() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Calculates the squared distance between two locations, optimized for physics calculations.
   * This variant avoids using {@link Math#sqrt(double)} for performance reasons and adjusts for cube height.
   *
   * @param locA The first location (usually player).
   * @param locB The second location (usually cube/ball).
   * @return The squared distance between the two points.
   */
  public double getDistanceSquared(Location locA, Location locB) {
    long start = System.nanoTime();
    try {
      double dx = locA.getX() - locB.getX();
      double dy = (locA.getY() - BALL_TOUCH_Y_OFFSET) - locB.getY() - CUBE_HITBOX_ADJUSTMENT;
      if (dy < 0) dy = 0;
      double dz = locA.getZ() - locB.getZ();

      return dx * dx + dy * dy + dz * dz;
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send(PERM_HIT_DEBUG, "{prefix-admin}&dPhysicsFormulae#getDistanceSquared() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Calculates the perpendicular distance from a player's position to the
   * path of the cube's movement vector. Used for proximity and collision prediction.
   *
   * @param newVelocity The velocity vector of the cube.
   * @param cubePos The cube's current position.
   * @param player The player whose position is used for distance checking.
   * @return The perpendicular distance between the player and the cube's velocity vector.
   */
  public double getPerpendicularDistance(Vector newVelocity, Vector cubePos, Player player) {
    long start = System.nanoTime();
    try {
      if (Math.abs(newVelocity.getX()) < 1e-6) return Double.MAX_VALUE;

      double slopeA = newVelocity.getZ() / newVelocity.getX();
      double interceptB = cubePos.getZ() - slopeA * cubePos.getX();

      double playerX = player.getLocation().getX();
      double playerZ = player.getLocation().getZ();

      // Compute perpendicular distance from player to cube’s path
      return Math.abs(slopeA * playerX - playerZ + interceptB) / Math.sqrt(slopeA * slopeA + 1);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send(PERM_HIT_DEBUG, "{prefix-admin}&dPhysicsFormulae#getPerpendicularDistance() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Calculates the squared perpendicular distance from a player's position to the
   * path of the cube's movement vector. Used for proximity and collision prediction.
   *
   * <p>Squared form avoids costly {@link Math#sqrt(double)} calls when only relative
   * distance comparisons are required.</p>
   *
   * @param newVelocity The velocity vector of the cube.
   * @param cubePos The cube's current position.
   * @param player The player whose position is used for distance checking.
   * @return The squared perpendicular distance between the player and the cube's velocity vector.
   */
  public double getPerpendicularDistanceSquared(Vector newVelocity, Vector cubePos, Player player) {
    long start = System.nanoTime();
    try {
      if (Math.abs(newVelocity.getX()) < 1e-6) return Double.MAX_VALUE;

      double slopeA = newVelocity.getZ() / newVelocity.getX();
      double interceptB = cubePos.getZ() - slopeA * cubePos.getX();

      double playerX = player.getLocation().getX();
      double playerZ = player.getLocation().getZ();

      // (|a*x - z + b| / sqrt(a² + 1))² = (a*x - z + b)² / (a² + 1)
      double numerator = slopeA * playerX - playerZ + interceptB;
      return (numerator * numerator) / (slopeA * slopeA + 1);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send(PERM_HIT_DEBUG, "{prefix-admin}&dPhysicsFormulae#getPerpendicularDistanceSquared() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Applies the soft cap to the calculated kick power.
   * @param baseKickPower Initial kick power.
   * @return Randomized capped kick power.
   */
  public double capKickPower(double baseKickPower) {
    long start = System.nanoTime();
    try {
      if (baseKickPower <= MAX_KP) return baseKickPower;
      double minRandomKP = MAX_KP * SOFT_CAP_MIN_FACTOR;
      return minRandomKP + RANDOM.nextDouble() * (MAX_KP - minRandomKP);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send(PERM_HIT_DEBUG, "{prefix-admin}&dPhysicsFormulae#capKickPower() &ftook &e" + ms + "ms");
    }
  }
}
