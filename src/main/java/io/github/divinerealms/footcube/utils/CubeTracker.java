package io.github.divinerealms.footcube.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CubeTracker implements Runnable {
  private final Plugin plugin;
  private final ProtocolManager protocolManager;

  private final Map<UUID, Set<UUID>> manuallyTrackedPlayers = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, Integer>> fakeEntityIds = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, Location>> lastSentLocations = new ConcurrentHashMap<>();

  private final Collection<Slime> activeCubes;

  private static final int MAX_VISUAL_RANGE = 64;
  private static final int DEFAULT_TRACK_LIMIT = 32;
  private static final double TELEPORT_DISTANCE_THRESHOLD = 0.05; // minimum movement before sending update

  private int taskId = -1;
  private long tickCounter = 0L;
  private final Random random = new Random();

  public CubeTracker(Plugin plugin, Collection<Slime> activeCubes) {
    this.plugin = plugin;
    this.activeCubes = activeCubes;
    this.protocolManager = ProtocolLibrary.getProtocolManager();
  }

  public void startTracking() {
    if (taskId != -1) return;
    taskId = Bukkit.getScheduler().runTaskTimer(plugin, this, 2L, 2L).getTaskId(); // every 2 ticks = 0.1s
    plugin.getLogger().info("CubeTracker started (smooth mode, 64-block range).");
  }

  public void stopTracking() {
    if (taskId == -1) return;
    Bukkit.getScheduler().cancelTask(taskId);
    taskId = -1;

    fakeEntityIds.forEach((cubeId, map) -> map.forEach((playerId, fakeId) -> {
      Player player = Bukkit.getPlayer(playerId);
      if (player != null && player.isOnline()) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutEntityDestroy(fakeId));
      }
    }));

    manuallyTrackedPlayers.clear();
    fakeEntityIds.clear();
    lastSentLocations.clear();
    plugin.getLogger().info("CubeTracker stopped and cleared.");
  }

  public void registerCube(Slime cube) {
    manuallyTrackedPlayers.putIfAbsent(cube.getUniqueId(), ConcurrentHashMap.newKeySet());
    fakeEntityIds.putIfAbsent(cube.getUniqueId(), new ConcurrentHashMap<>());
    lastSentLocations.putIfAbsent(cube.getUniqueId(), new ConcurrentHashMap<>());
  }

  public void unregisterCube(Slime cube) {
    if (cube == null) return;
    manuallyTrackedPlayers.remove(cube.getUniqueId());
    Map<UUID, Integer> ids = fakeEntityIds.remove(cube.getUniqueId());
    if (ids != null) {
      ids.forEach((playerId, fakeId) -> {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
          ((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutEntityDestroy(fakeId));
        }
      });
    }
    lastSentLocations.remove(cube.getUniqueId());
  }

  private int getOrCreateFakeId(Slime cube, Player player) {
    return fakeEntityIds
        .computeIfAbsent(cube.getUniqueId(), k -> new ConcurrentHashMap<>())
        .computeIfAbsent(player.getUniqueId(), k -> random.nextInt(Integer.MAX_VALUE));
  }

  @Override
  public void run() {
    tickCounter++;

    for (Slime cube : new ArrayList<>(activeCubes)) {
      if (cube == null || cube.isDead()) {
        unregisterCube(cube);
        continue;
      }

      Location cubeLoc = cube.getLocation();
      Set<UUID> currentlyTracked = manuallyTrackedPlayers
          .getOrDefault(cube.getUniqueId(), ConcurrentHashMap.newKeySet());
      Set<UUID> nextTracked = ConcurrentHashMap.newKeySet();

      for (Player player : Bukkit.getOnlinePlayers()) {
        if (!player.isOnline() || !player.getWorld().equals(cubeLoc.getWorld())) continue;

        double distance = player.getLocation().distance(cubeLoc);
        boolean isManuallyTracked = currentlyTracked.contains(player.getUniqueId());

        if (distance <= DEFAULT_TRACK_LIMIT) {
          if (isManuallyTracked) sendDestroyPacket(player, cube);
          continue;
        }

        if (distance <= MAX_VISUAL_RANGE) {
          if (!isManuallyTracked) sendSpawnPacket(player, cube);

          // Adaptive update rate based on distance
          int interval = getTeleportInterval(distance);

          if (tickCounter % interval == 0) {
            if (hasMovedSignificantly(player, cube, cubeLoc)) {
              sendTeleportPacket(player, cube);
              lastSentLocations.computeIfAbsent(cube.getUniqueId(), k -> new ConcurrentHashMap<>())
                  .put(player.getUniqueId(), cubeLoc.clone());
            }
          }
          nextTracked.add(player.getUniqueId());
        } else if (isManuallyTracked) {
          sendDestroyPacket(player, cube);
        }
      }

      manuallyTrackedPlayers.put(cube.getUniqueId(), nextTracked);
    }
  }

  private int getTeleportInterval(double distance) {
    if (distance < 40) return 1; // close = update every tick
    if (distance < 55) return 2;
    return 3; // far = update every 3 ticks (0.15s)
  }

  private boolean hasMovedSignificantly(Player player, Slime cube, Location newLoc) {
    Location lastLoc = lastSentLocations
        .computeIfAbsent(cube.getUniqueId(), k -> new ConcurrentHashMap<>())
        .get(player.getUniqueId());
    if (lastLoc == null) return true;
    return lastLoc.distanceSquared(newLoc) > TELEPORT_DISTANCE_THRESHOLD * TELEPORT_DISTANCE_THRESHOLD;
  }

  private void sendSpawnPacket(Player player, Slime cube) {
    try {
      int fakeId = getOrCreateFakeId(cube, player);
      Location loc = cube.getLocation();

      PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY_LIVING);
      packet.getIntegers()
          .write(0, fakeId)
          .write(1, 55)
          .write(2, (int) (loc.getX() * 32.0))
          .write(3, (int) (loc.getY() * 32.0))
          .write(4, (int) (loc.getZ() * 32.0));

      packet.getBytes()
          .write(0, (byte) (loc.getYaw() * 256 / 360))
          .write(1, (byte) (loc.getPitch() * 256 / 360))
          .write(2, (byte) (loc.getYaw() * 256 / 360));

      WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(cube);
      packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());

      protocolManager.sendServerPacket(player, packet);
      lastSentLocations.computeIfAbsent(cube.getUniqueId(), k -> new ConcurrentHashMap<>())
          .put(player.getUniqueId(), loc.clone());
    } catch (Exception e) {
      plugin.getLogger().log(Level.WARNING, "Spawn packet failed for " + player.getName(), e);
    }
  }

  private void sendTeleportPacket(Player player, Slime cube) {
    try {
      int fakeId = getOrCreateFakeId(cube, player);
      Location loc = cube.getLocation();

      PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
      packet.getIntegers()
          .write(0, fakeId)
          .write(1, (int) (loc.getX() * 32.0))
          .write(2, (int) (loc.getY() * 32.0))
          .write(3, (int) (loc.getZ() * 32.0));

      packet.getBytes()
          .write(0, (byte) (loc.getYaw() * 256 / 360))
          .write(1, (byte) (loc.getPitch() * 256 / 360));

      protocolManager.sendServerPacket(player, packet);
    } catch (Exception e) {
      plugin.getLogger().log(Level.WARNING, "Teleport packet failed for " + player.getName(), e);
    }
  }

  private void sendDestroyPacket(Player player, Slime cube) {
    Map<UUID, Integer> ids = fakeEntityIds.get(cube.getUniqueId());
    if (ids == null) return;
    Integer fakeId = ids.remove(player.getUniqueId());
    if (fakeId == null) return;
    ((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutEntityDestroy(fakeId));
    Map<UUID, Location> locs = lastSentLocations.get(cube.getUniqueId());
    if (locs != null) locs.remove(player.getUniqueId());
  }
}
