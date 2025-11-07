package io.github.divinerealms.footcube.physics;

import io.github.divinerealms.footcube.physics.actions.CubeImpulseAction;
import io.github.divinerealms.footcube.physics.actions.CubeSoundAction;
import io.github.divinerealms.footcube.physics.touch.CubeTouchInfo;
import io.github.divinerealms.footcube.physics.touch.CubeTouchType;
import lombok.Getter;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Getter
public class PhysicsData {
  private final Set<Slime> cubes = ConcurrentHashMap.newKeySet();
  private final Set<Slime> cubesToRemove = ConcurrentHashMap.newKeySet();

  private final Map<UUID, Vector> velocities = new ConcurrentHashMap<>();
  private final Map<UUID, Long> raised = new ConcurrentHashMap<>();
  private final Map<UUID, Double> speed = new ConcurrentHashMap<>();
  private final Map<UUID, Double> charges = new ConcurrentHashMap<>();
  private final Map<UUID, Long> kicked = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();
  private final Set<UUID> cubeHits = ConcurrentHashMap.newKeySet();
  private final Queue<CubeSoundAction> soundQueue = new ConcurrentLinkedQueue<>();
  private final Queue<CubeImpulseAction> hitQueue = new ConcurrentLinkedQueue<>();
  private final Map<UUID, Long> buttonCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Map<CubeTouchType, CubeTouchInfo>> lastTouches = new ConcurrentHashMap<>();

  public boolean matchesEnabled = true;
  public boolean hitDebugEnabled = false;
  public int tickCounter = 0;
}