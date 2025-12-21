package io.github.divinerealms.footcube.physics;

import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

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
public final class PlayerPhysicsCache {
  public final Location location;
  public final Vector direction;
  public final double speed;
  public final boolean isIneligible;
  public final UUID playerId;

  /**
   * Constructs a physics cache snapshot for the given player.
   *
   * @param player the player to cache data for
   * @param system physics system for eligibility checks
   * @param data physics data store containing player speeds
   */
  public PlayerPhysicsCache(Player player, PhysicsSystem system, PhysicsData data) {
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
    return String.format("PlayerPhysicsCache[player=%s, speed=%.2f, eligible=%b]", playerId, speed, !isIneligible);
  }
}
