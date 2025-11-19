package io.github.divinerealms.footcube.matchmaking.arena;

import lombok.Data;
import org.bukkit.Location;

@Data
public class Arena {
  private final int id;
  private final int type;
  private final Location blueSpawn;
  private final Location redSpawn;
  private final Location center;
  private final boolean isXAxis;
  private final boolean redIsGreater;
}
