package io.github.divinerealms.footcube.physics.actions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Encapsulates a sound effect action within the physics system.
 * <p>
 * A {@code SoundAction} defines a sound event at a specific location, with optional player
 * targeting. It includes the sound type, playback volume, and pitch. If {@code player} is non-null,
 * the sound is only sent to that player; otherwise, it is broadcast to all nearby listeners.
 * </p>
 *
 * <p>This design allows flexible and fine-grained control over sound feedback, ensuring that
 * auditory cues
 * are consistent with the player's experience and interaction with physics objects.</p>
 *
 * @see Sound
 */
@Getter
@AllArgsConstructor
public class CubeSoundAction {

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
