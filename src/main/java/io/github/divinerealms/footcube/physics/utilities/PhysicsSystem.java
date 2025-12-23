package io.github.divinerealms.footcube.physics.utilities;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.actions.CubeSoundAction;
import io.github.divinerealms.footcube.physics.touch.CubeTouchInfo;
import io.github.divinerealms.footcube.physics.touch.CubeTouchType;
import io.github.divinerealms.footcube.tasks.CubeProcessTask;
import io.github.divinerealms.footcube.utils.Logger;
import net.minecraft.server.v1_8_R3.EntitySlime;
import net.minecraft.server.v1_8_R3.PathfinderGoalSelector;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftSlime;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_BYPASS_COOLDOWN;

public class PhysicsSystem {
  private final PhysicsData data;
  private final Logger logger;
  private final BukkitScheduler scheduler;
  private final Plugin plugin;
  private final PhysicsFormulae formulae;

  public PhysicsSystem(PhysicsData data, Logger logger, BukkitScheduler scheduler, Plugin plugin) {
    this.data = data;
    this.logger = logger;
    this.scheduler = scheduler;
    this.plugin = plugin;
    this.formulae = new PhysicsFormulae(logger);
  }

  /**
   * Checks if the cube was recently lifted by a player.
   * This check prevents {@link CubeProcessTask} from overwriting cube's Y level.
   *
   * @param cubeId UUID of the cube being checked.
   * @return {@code true} if cube was raised in the last 500ms, {@code false} otherwise.
   */
  public boolean wasRecentlyRaised(UUID cubeId) {
    long lastRaise = data.getRaised().getOrDefault(cubeId, 0L);
    return System.currentTimeMillis() - lastRaise < 500;
  }

  /**
   * Schedules the playback of queued sound events after physics updates.
   * Ensures that multiple cubes can trigger sounds in a single tick efficiently.
   */
  public void scheduleSound() {
    Queue<CubeSoundAction> queue = data.getSoundQueue();
    if (queue.isEmpty()) return;

    Queue<CubeSoundAction> toPlay = new ArrayDeque<>(queue);
    queue.clear();

    scheduler.runTask(plugin, () -> toPlay.forEach(action -> {
      Location location;
      Sound sound = action.getSound();
      float volume = action.getVolume();
      float pitch = action.getPitch();

      if (action.isPlayerTargeted()) {
        Player player = action.getPlayer();
        if (player == null || !player.isOnline()) return;

        location = player.getLocation();
        player.playSound(location, sound, volume, pitch);
      } else {
        location = action.getLocation();
        if (location == null) return;

        location.getWorld().playSound(location, sound, volume, pitch);
      }
    }));
  }

  /**
   * Schedules the removal of dead or invalid cubes from the world.
   * Ensures the cubes list remains synchronized and prevents entity leaks.
   */
  public void scheduleCubeRemoval() {
    if (data.getCubesToRemove().isEmpty()) return;

    Set<Slime> toRemove = new HashSet<>(data.getCubesToRemove());
    data.getCubesToRemove().clear();

    scheduler.runTaskLater(plugin, () -> toRemove.forEach(cube -> {
      data.getCubes().remove(cube);
      if (!cube.isDead()) cube.remove();
    }), CUBE_REMOVAL_DELAY_TICKS);
  }

  /**
   * Adds the specified location to the sound queue for further processing.
   * If the location is not null, it creates a clone of the location and queues it in the sound queue.
   *
   * @param location The location to be added to the sound queue. Must not be null.
   */
  public void queueSound(Location location, Sound sound, float volume, float pitch) {
    if (location == null || sound == null) return;
    data.getSoundQueue().offer(new CubeSoundAction(location, null, sound, volume, pitch));
  }

  /**
   * Adds a sound action to the sound queue for a specific player.
   * This method queues the specified sound to be played at the player's location with the given volume and pitch.
   * If the player or sound is null, the method exits without queuing any action.
   *
   * @param player The player at whose location the sound will be played. Must not be null.
   * @param sound The sound to be played. Must not be null.
   * @param volume The volume level of the sound. A float value where higher values indicate louder sounds.
   * @param pitch The pitch of the sound. A float value where higher values indicate a higher pitch.
   */
  public void queueSound(Player player, Sound sound, float volume, float pitch) {
    if (player == null || sound == null) return;
    data.getSoundQueue().offer(new CubeSoundAction(null, player, sound, volume, pitch));
  }

  /**
   * Adds the specified location to the sound queue for further processing.
   * This method queues a sound at the given location using default sound type, volume, and pitch values.
   *
   * @param location The location where the sound will be queued. Must not be null.
   */
  public void queueSound(Location location) {
    queueSound(location, Sound.SLIME_WALK, SOUND_VOLUME, SOUND_PITCH);
  }

  /**
   * Calculates the final kick power based on player speed and current charge level.
   * @param player Player who initiated the calculation (who kicked the ball).
   * @return Player's kick power
   */
  public PlayerKickResult calculateKickPower(Player player) {
    long start = System.nanoTime();
    try {
      boolean isCharged = player.isSneaking();
      double charge = CHARGE_BASE_VALUE + data.getCharges().getOrDefault(player.getUniqueId(), 0D) * CHARGE_MULTIPLIER;
      double speed = data.getSpeed().getOrDefault(player.getUniqueId(), MIN_SPEED_FOR_DAMPENING);
      double power = isCharged ? speed * KICK_POWER_SPEED_MULTIPLIER + CHARGED_BASE_POWER : speed * KICK_POWER_SPEED_MULTIPLIER + REGULAR_BASE_POWER;
      double baseKickPower = charge * power;
      double finalKickPower = formulae.capKickPower(baseKickPower);

      return new PlayerKickResult(power, charge, baseKickPower, finalKickPower, isCharged);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsSystem#calculateKickPower() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Removes all Slime entities in the main world.
   * Used only on plugin reload.
   */
  public void removeCubes() {
    long start = System.nanoTime();
    try {
      List<Entity> entities = plugin.getServer().getWorlds().get(0).getEntities();
      for (Entity entity : entities) {
        if (entity instanceof Slime) {
          ((Slime) entity).setHealth(0);
          if (!data.getCubes().contains(entity)) entity.remove();
        }
      }
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsSystem#removeCubes() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Spawns a new ball at the given location and disables its AI.
   * @param location The location to spawn the cube.
   * @return The spawned entity.
   */
  public Slime spawnCube(Location location) {
    long start = System.nanoTime();
    try {
      Slime cube = (Slime) location.getWorld().spawnEntity(location, EntityType.SLIME);
      cube.setRemoveWhenFarAway(false);
      cube.setSize(SLIME_SIZE);
      // Permanent jump effect that stops the cube from hopping.
      cube.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, JUMP_POTION_DURATION, JUMP_POTION_AMPLIFIER, true), true);

      // NMS Hack to prevent the ball from trying to reach the player.
      EntitySlime nmsSlime = ((CraftSlime) cube).getHandle();
      try {
        Field bField = PathfinderGoalSelector.class.getDeclaredField("b");
        bField.setAccessible(true);
        bField.set(nmsSlime.goalSelector, new LinkedList<>());
        bField.set(nmsSlime.targetSelector, new LinkedList<>());
      } catch (Exception exception) {
        plugin.getLogger().log(Level.SEVERE, "Error injecting NMS Pathfinder Goals:", exception);
      }

      data.getCubes().add(cube);
      return cube;
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsSystem#spawnCube() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Checks if the given player is not allowed to interact based on their game mode.
   *
   * @param player The player to check. Must not be null.
   * @return True if the player is not in survival game mode, otherwise false.
   */
  public boolean notAllowedToInteract(Player player) {
    return player.getGameMode() != GameMode.SURVIVAL;
  }

  /**
   * Determines if the specified player is currently away-from-keyboard (AFK)
   * based on their last recorded action time.
   *
   * @param player The player to check for AFK status. Must not be null.
   * @return True if the player is considered AFK (time since last action exceeds the set threshold), false otherwise.
   */
  public boolean isAFK(Player player) {
    long last = data.getLastAction().getOrDefault(player.getUniqueId(), 0L);
    return System.currentTimeMillis() - last > AFK_THRESHOLD;
  }

  /**
   * Records the most recent action performed by a player by updating the timestamp of their last action.
   *
   * @param player The player whose action is being recorded. Must not be null.
   */
  public void recordPlayerAction(Player player) {
    data.getLastAction().put(player.getUniqueId(), System.currentTimeMillis());
  }

  /**
   * Removes a player and cleans up associated data in various system states.
   * This includes clearing cached player data, settings, physics data,
   * cooldowns, and actions associated with the specified player.
   *
   * @param player The player to be removed. Must not be null.
   */
  public void removePlayer(Player player) {
    long start = System.nanoTime();
    try {
      UUID uuid = player.getUniqueId();

      data.getSpeed().remove(uuid);
      data.getCharges().remove(uuid);
      data.getLastAction().remove(uuid);
      data.getCubeHits().remove(uuid);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsSystem#removePlayer() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Displays hit-related information to the player and manages the cooldown logic for hits
   * based on whether the hit is a charged or regular hit. This method also logs any performance
   * overhead if the execution time exceeds a millisecond.
   *
   * @param player The player who performed the hit. This parameter must not be null.
   * @param kickResult The result of the kick action, containing details such as kick power,
   *                   charge level, and whether the hit was charged. This parameter must not be null.
   */
  public void showHits(Player player, PlayerKickResult kickResult) {
    long start = System.nanoTime();
    try {
      UUID playerId = player.getUniqueId();
      boolean isChargedHit = kickResult.isChargedHit();
      double finalKickPower = kickResult.getFinalKickPower();
      CubeTouchType type = isChargedHit ? CubeTouchType.CHARGED_KICK : CubeTouchType.REGULAR_KICK;

      Map<CubeTouchType, CubeTouchInfo> touches = data.getLastTouches().get(playerId);
      long lastHitTime = 0L;
      if (touches != null && touches.containsKey(type)) lastHitTime = touches.get(type).getTimestamp();

      long cooldownDuration = isChargedHit ? CHARGED_KICK_COOLDOWN : REGULAR_KICK_COOLDOWN;
      long currentTime = System.currentTimeMillis();
      long timeElapsed = currentTime - lastHitTime;
      long timeRemainingMillis = Math.max(0, cooldownDuration - timeElapsed);

      String timeFormatted = String.format("%.1f", timeRemainingMillis / 1000.0);
      String color = timeRemainingMillis > 50 ? "&c" : "&a";

      logger.sendActionBar(player, (isChargedHit
          ? Lang.HITDEBUG_PLAYER_CHARGED.replace(new String[]{
          String.format("%.2f", finalKickPower),
          String.format("%.2f", kickResult.getPower()),
          String.format("%.2f", kickResult.getCharge())
      })
          : Lang.HITDEBUG_PLAYER_REGULAR.replace(new String[]{String.format("%.2f", finalKickPower)})
      ) + Lang.HITDEBUG_PLAYER_COOLDOWN.replace(new String[]{color, timeFormatted}));
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsSystem#showHits() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Generates a debug message for a player's hit action based on whether the hit
   * is charged or regular. The method formats and returns a string with details
   * including the player's name, kick power, base power, and charge level. Logs
   * execution time if it exceeds a defined threshold.
   *
   * @param player The player who performed the hit. This parameter must not be null.
   * @param result The result of the hit action, which contains information such as kick power,
   *               charge level, and whether the hit was charged. This parameter must not be null.
   * @return A string containing the formatted debug information about the hit action.
   */
  public String onHitDebug(Player player, PlayerKickResult result) {
    long start = System.nanoTime();
    try {
      String coloredKickPower = result.getFinalKickPower() != result.getBaseKickPower() ? "&c" : "&a";
      return result.isChargedHit()
          ? Lang.HITDEBUG_CHARGED.replace(new String[]{
          player.getDisplayName(), coloredKickPower + String.format("%.2f", result.getFinalKickPower()),
          String.format("%.2f", result.getBaseKickPower()), String.format("%.2f", result.getPower()), String.format("%.2f", result.getCharge())
      })
          : Lang.HITDEBUG_REGULAR.replace(new String[]{player.getDisplayName(), String.format("%.2f", result.getFinalKickPower())});
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsSystem#onHitDebug() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Checks if a player is currently under a cooldown preventing cube (ball) spawning.
   * If the cooldown is active, a message is sent to the player and {@code true} is returned.
   *
   * @param player The player attempting to spawn a cube.
   * @return True if the player must wait before spawning again, false otherwise.
   */
  public boolean cantSpawnYet(Player player) {
    long start = System.nanoTime();
    try {
      if (player.hasPermission(PERM_BYPASS_COOLDOWN)) return false;
      UUID playerId = player.getUniqueId();
      long now = System.currentTimeMillis(), last = data.getButtonCooldowns().getOrDefault(playerId, 0L), diff = now - last;
      if (diff < SPAWN_COOLDOWN_MS) {
        long remainingMs = SPAWN_COOLDOWN_MS - diff, seconds = remainingMs / 1000;
        logger.send(player, Lang.BLOCK_INTERACT_COOLDOWN.replace(new String[]{Utilities.formatTime(seconds)}));
        return true;
      }
      return false;
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dPhysicsSystem#cantSpawnYet() &ftook &e" + ms + "ms");
    }
  }

  /**
   * Sets a cooldown timer preventing the player from spawning another cube immediately.
   * Used to rate-limit spawn button presses.
   *
   * @param player The player who triggered the spawn action.
   */
  public void setButtonCooldown(Player player) {
    data.getButtonCooldowns().put(player.getUniqueId(), System.currentTimeMillis());
  }
}
