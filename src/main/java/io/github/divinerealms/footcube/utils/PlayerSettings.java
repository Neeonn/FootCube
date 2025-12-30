package io.github.divinerealms.footcube.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Color;
import org.bukkit.Sound;

@Setter
@Getter
public class PlayerSettings {

  public static final Set<EnumParticle> DISALLOWED_PARTICLES = EnumSet.of(
      EnumParticle.BARRIER,
      EnumParticle.ITEM_CRACK,
      EnumParticle.BLOCK_CRACK,
      EnumParticle.BLOCK_DUST,
      EnumParticle.MOB_APPEARANCE,
      EnumParticle.FOOTSTEP,
      EnumParticle.SUSPENDED,
      EnumParticle.SUSPENDED_DEPTH,
      EnumParticle.WATER_WAKE,
      EnumParticle.SPELL_MOB_AMBIENT,
      EnumParticle.TOWN_AURA,
      EnumParticle.ITEM_TAKE,
      EnumParticle.EXPLOSION_HUGE,
      EnumParticle.SMOKE_LARGE,
      EnumParticle.FIREWORKS_SPARK,
      EnumParticle.CLOUD,
      EnumParticle.EXPLOSION_LARGE,
      EnumParticle.EXPLOSION_NORMAL
  );
  public static final List<Sound> ALLOWED_KICK_SOUNDS = Arrays.asList(
      Sound.CLICK,
      Sound.NOTE_STICKS,
      Sound.NOTE_PLING,
      Sound.ITEM_PICKUP,
      Sound.DIG_STONE,
      Sound.DIG_WOOD,
      Sound.DIG_GRASS,
      Sound.SUCCESSFUL_HIT,
      Sound.SLIME_WALK,
      Sound.SLIME_WALK2
  );
  public static final List<Sound> ALLOWED_GOAL_SOUNDS = Arrays.asList(
      Sound.LEVEL_UP,
      Sound.ANVIL_LAND,
      Sound.ANVIL_USE,
      Sound.EXPLODE,
      Sound.FIREWORK_LAUNCH,
      Sound.FIREWORK_BLAST,
      Sound.FIREWORK_BLAST2,
      Sound.FIREWORK_TWINKLE,
      Sound.WITHER_DEATH,
      Sound.ENDERDRAGON_GROWL
  );
  private static final Map<String, Color> COLOR_MAP;

  static {
    COLOR_MAP = Map.of(
        "RED", Color.fromRGB(255, 0, 0),
        "YELLOW", Color.fromRGB(255, 255, 0),
        "ORANGE", Color.fromRGB(255, 165, 0),
        "WHITE", Color.fromRGB(255, 255, 255),
        "BLACK", Color.fromRGB(30, 30, 30),
        "PURPLE", Color.fromRGB(128, 0, 128),
        "PINK", Color.fromRGB(255, 105, 180),
        "MAGENTA", Color.fromRGB(255, 0, 255),
        "GREEN", Color.fromRGB(50, 205, 50)
    );
  }

  private boolean kickSoundEnabled = true;
  private boolean goalSoundEnabled = true;
  private boolean particlesEnabled = true;
  private boolean buildEnabled = false;
  private Sound kickSound = Sound.SUCCESSFUL_HIT;
  private Sound goalSound = Sound.FIREWORK_LARGE_BLAST;
  private EnumParticle particle = EnumParticle.VILLAGER_HAPPY;
  private Color redstoneColor = Color.WHITE;
  private String goalMessage = "default"; // "default", "simple", "epic", "minimal"

  public static List<String> getAllowedParticles() {
    List<String> allowed = new ArrayList<>();
    EnumParticle[] particles = EnumParticle.values();

    for (EnumParticle p : particles) {
      if (p != null && !DISALLOWED_PARTICLES.contains(p)) {
        allowed.add(p.name());
      }
    }

    return allowed;
  }

  public static List<String> getAllowedColorNames() {
    return new ArrayList<>(COLOR_MAP.keySet());
  }

  public void setCustomRedstoneColor(String colorName) {
    Color color = COLOR_MAP.get(colorName.toUpperCase());
    if (color == null) {
      throw new IllegalArgumentException("Invalid REDSTONE color: " + colorName);
    }
    this.redstoneColor = color;
  }

  public void toggleBuild() {
    this.buildEnabled = !this.buildEnabled;
  }
}