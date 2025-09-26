package io.github.divinerealms.footcube.utils;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Sound;

@Setter
@Getter
public class PlayerSoundSettings {
  private boolean kickEnabled = true;
  private boolean goalEnabled = true;

  private Sound kickSound = Sound.SUCCESSFUL_HIT;
  private Sound goalSound = Sound.FIREWORK_LARGE_BLAST;
}