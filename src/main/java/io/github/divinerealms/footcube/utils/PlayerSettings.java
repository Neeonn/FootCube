package io.github.divinerealms.footcube.utils;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Effect;
import org.bukkit.Sound;

@Setter
@Getter
public class PlayerSettings {
  private boolean kickSoundEnabled = true;
  private boolean goalSoundEnabled = true;
  private boolean particlesEnabled = true;

  private Sound kickSound = Sound.SUCCESSFUL_HIT;
  private Sound goalSound = Sound.FIREWORK_LARGE_BLAST;
  private Effect particle = Effect.HAPPY_VILLAGER;
}