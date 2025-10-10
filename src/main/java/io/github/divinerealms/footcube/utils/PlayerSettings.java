package io.github.divinerealms.footcube.utils;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Color;
import org.bukkit.Sound;

import java.util.*;
import java.util.stream.Collectors;

@Setter
@Getter
public class PlayerSettings {
  private boolean kickSoundEnabled = true;
  private boolean goalSoundEnabled = true;
  private boolean particlesEnabled = true;
  private boolean buildEnabled = false;

  private Sound kickSound = Sound.SUCCESSFUL_HIT;
  private Sound goalSound = Sound.FIREWORK_LARGE_BLAST;
  private EnumParticle particle = EnumParticle.VILLAGER_HAPPY;

  private Color redstoneColor = Color.WHITE;

  private static final Map<String, Color> COLOR_MAP;
  static {
    Map<String, Color> map = new HashMap<>();
    map.put("RED", Color.fromRGB(255, 0, 0));
    map.put("YELLOW", Color.fromRGB(255, 255, 0));
    map.put("ORANGE", Color.fromRGB(255, 165, 0));
    map.put("WHITE", Color.fromRGB(255, 255, 255));
    map.put("BLACK", Color.fromRGB(30, 30, 30));
    map.put("PURPLE", Color.fromRGB(128, 0, 128));
    map.put("PINK", Color.fromRGB(255, 105, 180));
    map.put("MAGENTA", Color.fromRGB(255, 0, 255));
    map.put("GREEN", Color.fromRGB(50, 205, 50));
    COLOR_MAP = Collections.unmodifiableMap(map);
  }

  public void setCustomRedstoneColor(String colorName) {
    Color color = COLOR_MAP.get(colorName.toUpperCase());
    if (color == null) {
      throw new IllegalArgumentException("Invalid REDSTONE color: " + colorName);
    }
    this.redstoneColor = color;
  }

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

  public static List<String> getAllowedParticles() {
    return Arrays.stream(EnumParticle.values()).filter(p -> !DISALLOWED_PARTICLES.contains(p)).map(EnumParticle::name).collect(Collectors.toList());
  }

  public static List<String> getAllowedColorNames() {
    return new ArrayList<>(COLOR_MAP.keySet());
  }

  public static final List<Sound> ALLOWED_KICK_SOUNDS = Arrays.asList(
      Sound.CLICK,
      Sound.NOTE_STICKS,
      Sound.NOTE_PLING,
      Sound.ITEM_PICKUP,
      Sound.DIG_STONE,
      Sound.DIG_WOOD,
      Sound.DIG_GRASS,
      Sound.SUCCESSFUL_HIT
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

  public void toggleBuild() {
    this.buildEnabled = !this.buildEnabled;
  }
}