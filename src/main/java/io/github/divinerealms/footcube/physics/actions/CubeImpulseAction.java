package io.github.divinerealms.footcube.physics.actions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

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
public class CubeImpulseAction {
  private final Slime cube;
  private final Vector velocity;
}
