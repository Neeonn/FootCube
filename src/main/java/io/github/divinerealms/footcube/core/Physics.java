package io.github.divinerealms.footcube.core;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.utils.KickResult;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftSlime;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Physics {
  private final FCManager fcManager;
  private final Plugin plugin;
  private final Organization org;
  private final Logger logger;
  private final FileConfiguration config;
  private final BukkitScheduler scheduler;

  @Getter private final HashSet<Slime> cubes = new HashSet<>();
  private final Set<UUID> inactiveCubesIds = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Vector> velocities = new HashMap<>();
  @Getter private final Map<UUID, Long> kicked = new HashMap<>();
  private final Map<UUID, Double> speed = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Double> charges = new ConcurrentHashMap<>();
  @Getter private final Map<UUID, Long> ballHitCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();

  private long chargedHitCooldown, regularHitCooldown, afkThreshold, speedCalcInterval;
  private double maxKP, softCapMinFactor, chargeMultiplier, basePower, chargeRecoveryRate;
  private double hitRadius;
  private double hitRadiusSquared;
  private double minRadius;
  private double minRadiusSquared;
  private double bounceThreshold;
  private double inactivityRadiusSquared;
  @Getter private double cubeJumpRightClick;
  private float soundVolume, soundPitch;

  private static final double DISTANCE_PARTICLE_THRESHOLD_SQUARED = 32 * 32;
  private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

  private BukkitTask speedUpdateTask, activityUpdateTask, physicsTask, glowTask;

  @Getter @Setter private boolean matchesEnabled = true;
  @Getter public boolean hitDebugEnabled = false;

  public Physics(FCManager fcManager) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.scheduler = plugin.getServer().getScheduler();
    this.org = fcManager.getOrg();
    this.logger = fcManager.getLogger();
    this.config = fcManager.getConfigManager().getConfig("config.yml");
    this.reload();
  }

  private void startPhysicsTask() {
    if (physicsTask != null) physicsTask.cancel();
    physicsTask = scheduler.runTaskTimer(plugin, this::tick, 1L, 1L);
  }

  private void startGlowTask() {
    if (glowTask != null) glowTask.cancel();
    glowTask = scheduler.runTaskTimer(plugin, this::showCubeParticles, 5L, 5L);
  }

  private void startActivityCheck() {
    if (activityUpdateTask != null) activityUpdateTask.cancel();

    activityUpdateTask = scheduler.runTaskTimerAsynchronously(plugin, () -> {
      if (cubes.isEmpty()) { inactiveCubesIds.clear(); return; }

      List<UUID> activeCubes = new ArrayList<>();
      Collection<? extends Player> onlinePlayers = plugin.getServer().getOnlinePlayers();
      double threshold = inactivityRadiusSquared;

      for (Slime cube : cubes) {
        if (cube.isDead()) continue;

        boolean isNearPlayer = onlinePlayers.stream().anyMatch(player -> player.getLocation().distanceSquared(cube.getLocation()) <= threshold);
        if (isNearPlayer) activeCubes.add(cube.getUniqueId());
      }

      Set<UUID> allCubeIds = cubes.stream().map(Entity::getUniqueId).collect(Collectors.toSet());
      inactiveCubesIds.clear();
      allCubeIds.stream().filter(id -> !activeCubes.contains(id)).forEach(inactiveCubesIds::add);
    }, 20L, 20L * 5);
  }

  private void startSpeedCalculation() {
    if (speedUpdateTask != null) speedUpdateTask.cancel();

    speedUpdateTask = scheduler.runTaskTimer(plugin, () -> {
      for (Player player : plugin.getServer().getOnlinePlayers()) {
        UUID uuid = player.getUniqueId();
        if (player.getGameMode() != GameMode.SURVIVAL) { lastLocations.remove(uuid); continue; }

        Location current = player.getLocation();
        Location last = lastLocations.put(uuid, current);

        if (last == null) continue;

        double dx = current.getX() - last.getX();
        double dy = (current.getY() - last.getY()) / 2;
        double dz = current.getZ() - last.getZ();

        double distanceTraveled = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double normalizedSpeed = distanceTraveled / speedCalcInterval;
        speed.put(uuid, normalizedSpeed);
      }
    }, 1L, speedCalcInterval);
  }

  public Slime spawnCube(Location location) {
    Slime cube = (Slime) location.getWorld().spawnEntity(location, EntityType.SLIME);
    cube.setRemoveWhenFarAway(false);
    cube.setSize(1);
    cube.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, -3, true), true);

    EntitySlime nmsSlime = ((CraftSlime) cube).getHandle();
    try {
      Field bField = PathfinderGoalSelector.class.getDeclaredField("b");
      bField.setAccessible(true);
      bField.set(nmsSlime.goalSelector, new LinkedList<>());
      bField.set(nmsSlime.targetSelector, new LinkedList<>());
    } catch (Exception exception) {
      plugin.getLogger().log(Level.SEVERE, "Error injecting NMS Pathfinder Goals:", exception);
    }

    cubes.add(cube);
    inactiveCubesIds.remove(cube.getUniqueId());
    return cube;
  }

  public void removeCubes() {
    List<Entity> entities = plugin.getServer().getWorlds().get(0).getEntities();
    for (Entity entity : entities) if (entity instanceof Slime) ((Slime) entity).setHealth(0);
  }

  // *** DEPRECATED ***
  @SuppressWarnings("unused")
  public double getDistance(Location locA, Location locB) {
    return Math.sqrt(getDistanceSquared(locA, locB));
  }

  public double getDistanceSquared(Location locA, Location locB) {
    double dx = locA.getX() - locB.getX();
    double dy = (locA.getY() - 1) - locB.getY() - 1.5;
    if (dy < 0) dy = 0;
    double dz = locA.getZ() - locB.getZ();

    return dx * dx + dy * dy + dz * dz;
  }

  public KickResult calculateKickPower(Player player) {
    boolean isCharged = player.isSneaking();
    double charge = 1 + charges.getOrDefault(player.getUniqueId(), 0D) * chargeMultiplier;
    double speed = this.speed.getOrDefault(player.getUniqueId(), 0.5D);
    double power = speed * 2 + basePower;
    double baseKickPower = isCharged ? charge * power : power;
    double finalKickPower = capKickPower(baseKickPower);

    return new KickResult(power, charge, baseKickPower, finalKickPower, isCharged);
  }

  private double capKickPower(double baseKickPower) {
    if (baseKickPower <= maxKP) return baseKickPower;
    double minRandomKP = maxKP * softCapMinFactor;
    return RANDOM.nextDouble(minRandomKP, maxKP);
  }

  public String onHitDebug(Player player, KickResult result) {
    return result.isChargedHit()
        ? Lang.HITDEBUG_CHARGED.replace(new String[]{
            player.getDisplayName(), (result.getFinalKickPower() != result.getBaseKickPower() ? "&c" : "&a") + String.format("%.2f", result.getFinalKickPower()),
            String.format("%.2f", result.getPower()), String.format("%.2f", result.getCharge())
        })
        : Lang.HITDEBUG_REGULAR.replace(new String[]{player.getDisplayName(), String.format("%.2f", result.getFinalKickPower())});
  }

  public boolean canHitBall(Player player) {
    long now = System.currentTimeMillis();
    long cooldown = player.isSneaking() ? chargedHitCooldown : regularHitCooldown;
    long lastHit = ballHitCooldowns.getOrDefault(player.getUniqueId(), 0L);

    return now - lastHit >= cooldown;
  }

  // *** EXPERIMENTAL NEW FEATURE, NOT USED ATM ***
  @SuppressWarnings("unused")
  public void showCooldownFeedback(Player player) {
    long now = System.currentTimeMillis();
    long cooldownDuration = player.isSneaking() ? chargedHitCooldown : regularHitCooldown;
    long lastHit = ballHitCooldowns.getOrDefault(player.getUniqueId(), 0L);

    long timeRemainingMillis = cooldownDuration - (now - lastHit);
    long timeRemaining = Math.max(0, timeRemainingMillis);

    scheduler.runTask(plugin, () -> {
      logger.sendActionBar(player, Lang.HIT_COOLDOWN_INDICATION.replace(new String[]{String.valueOf(timeRemaining)}));
      Location baseLoc = player.getLocation().clone().add(0, 1.5, 0);
      Location particleLocation = baseLoc.add(player.getLocation().getDirection().normalize().multiply(1.0));
      Utilities.sendParticle(player, EnumParticle.SMOKE_NORMAL, particleLocation, 0.1F, 0.1F, 0.1F, 0.01F, 5);
      player.playSound(player.getLocation(), Sound.NOTE_SNARE_DRUM, 2.0F, 0.8F);
    });
  }

  private void tick() {
    long now = System.currentTimeMillis();
    kicked.entrySet().removeIf(uuidLongEntry -> now > uuidLongEntry.getValue() + 1000L);

    for (Map.Entry<UUID, Double> entry : charges.entrySet()) {
      UUID uuid = entry.getKey();
      Player player = plugin.getServer().getPlayer(uuid);
      if (player == null || !player.isOnline()) { charges.remove(uuid); continue; }

      Double charge = entry.getValue();
      double nextCharge = 1 - (1 - charge) * chargeRecoveryRate;
      charges.put(uuid, nextCharge);
      player.setExp((float) nextCharge);
    }

    List<Slime> toRemove = new ArrayList<>();
    if (cubes.isEmpty()) return;

    for (Slime cube : cubes) {
      if (cube.isDead()) {
        toRemove.add(cube);
        continue;
      }

      UUID id = cube.getUniqueId();
      if (inactiveCubesIds.contains(id)) continue;

      Vector oldV = velocities.getOrDefault(id, cube.getVelocity());
      Vector newV = cube.getVelocity().clone();

      boolean sound = false;
      boolean kicked = false;

      List<Entity> nearby = cube.getNearbyEntities(hitRadius + 0.1, 1, hitRadius + 0.1);
      for (Entity entity : nearby) {
        if (!(entity instanceof Player)) continue;
        Player player = (Player) entity;
        if (player.getGameMode() != GameMode.SURVIVAL) continue;
        if (isAFK(player)) continue;

        double distanceSquared = getDistanceSquared(cube.getLocation(), player.getLocation());
        double distance = -1;

        if (distanceSquared <= hitRadiusSquared) {
          distance = Math.sqrt(distanceSquared);

          double speed = newV.length();
          if (distance <= minRadius && speed >= 0.5) newV.multiply(0.5 / speed);

          double power = this.speed.getOrDefault(player.getUniqueId(), 0D) / 3 + oldV.length() / 6;
          newV.add(player.getLocation().getDirection().setY(0).normalize().multiply(power));
          org.ballTouch(player);
          kicked = true;
          if (power > 0.15) sound = true;
        }

        double newVSquared = newV.lengthSquared();
        double proximityThresholdSquared = newVSquared * 1.69;

        if (distanceSquared < proximityThresholdSquared) {
          double delta = (distance > -1) ? distance : Math.sqrt(distanceSquared);
          double newVLength = Math.sqrt(newVSquared);

          Vector loc = cube.getLocation().toVector();
          Vector nextLoc = loc.clone().add(newV);

          boolean rightDirection = true;
          Vector pDir = new Vector(player.getLocation().getX() - loc.getX(), 0, player.getLocation().getZ() - loc.getZ());
          Vector cDir = (new Vector(newV.getX(), 0, newV.getZ())).normalize();

          int px = pDir.getX() < 0 ? -1 : 1;
          int pz = pDir.getZ() < 0 ? -1 : 1;
          int cx = cDir.getX() < 0 ? -1 : 1;
          int cz = cDir.getZ() < 0 ? -1 : 1;

          if (px != cx && pz != cz
              || (px != cx || pz != cz) && (!(cx * pDir.getX() > (cx * cz * px) * cDir.getZ())
              || !(cz * pDir.getZ() > (cz * cx * pz) * cDir.getX()))) {
            rightDirection = false;
          }

          if (rightDirection && loc.getY() < player.getLocation().getY() + 2
              && loc.getY() > player.getLocation().getY() - 1
              && nextLoc.getY() < player.getLocation().getY() + 2
              && nextLoc.getY() > player.getLocation().getY() - 1) {
            double a = newV.getZ() / newV.getX();
            double b = loc.getZ() - a * loc.getX();
            double numerator = a * player.getLocation().getX() - player.getLocation().getZ() + b;
            double numeratorSquared = numerator * numerator;
            double denominatorSquared = a * a + 1;

            if (numeratorSquared < minRadiusSquared * denominatorSquared) newV.multiply(delta / newVLength);
          }
        }
      }

      if (newV.getX() == 0) {
        newV.setX(-oldV.getX() * 0.8);
        if (Math.abs(oldV.getX()) > bounceThreshold) sound = true;
      } else if (!kicked && Math.abs(oldV.getX() - newV.getX()) < 0.1) newV.setX(oldV.getX() * 0.98);

      if (newV.getZ() == 0) {
        newV.setZ(-oldV.getZ() * 0.8);
        if (Math.abs(oldV.getZ()) > bounceThreshold) sound = true;
      } else if (!kicked && Math.abs(oldV.getZ() - newV.getZ()) < 0.1) newV.setZ(oldV.getZ() * 0.98);

      if (newV.getY() < 0 && oldV.getY() < 0 && oldV.getY() < newV.getY() - 0.05) {
        newV.setY(-oldV.getY() * 0.8);
        if (Math.abs(oldV.getY()) > bounceThreshold) sound = true;
      }

      if (sound) scheduler.runTask(plugin, () -> cube.getWorld().playSound(cube.getLocation(), Sound.SLIME_WALK, soundVolume, soundPitch));

      cube.setVelocity(newV);
      velocities.put(id, newV);
    }

    scheduleCubeRemoval(toRemove);
  }

  private void scheduleCubeRemoval(List<Slime> toRemove) {
    if (!toRemove.isEmpty()) {
      scheduler.runTaskLater(plugin, () -> toRemove.forEach(cube -> {
        cubes.remove(cube);
        if (!cube.isDead()) cube.remove();
      }), 20L);
    }
  }

  private void showCubeParticles() {
    Collection<? extends Player> onlinePlayers = plugin.getServer().getOnlinePlayers();
    if (onlinePlayers.isEmpty() || cubes.isEmpty()) return;

    for (Slime cube : cubes) {
      if (cube == null || cube.isDead() || cube.getLocation() == null) continue;
      if (inactiveCubesIds.contains(cube.getUniqueId())) continue;

      Location cubeLocation = cube.getLocation().clone().add(0, 0.25, 0);

      for (Player player : onlinePlayers) {
        if (cube.getLocation().distanceSquared(player.getLocation()) < DISTANCE_PARTICLE_THRESHOLD_SQUARED) continue;

        PlayerSettings settings = fcManager.getPlayerSettings(player);
        if (settings == null || !settings.isParticlesEnabled()) continue;

        EnumParticle particle = settings.getParticle();
        if (particle == EnumParticle.REDSTONE) {
          Color color = settings.getRedstoneColor();
          Utilities.sendParticle(player, EnumParticle.REDSTONE, cubeLocation, 0.01F, 0.01F, 0.01F, 0.01F, 8, color);
        } else {
          Utilities.sendParticle(player, particle, cubeLocation, 0.01F, 0.01F, 0.01F, 0.1F, 10);
        }
      }
    }
  }

  public void cleanupTasks() {
    if (physicsTask != null) { physicsTask.cancel(); physicsTask = null; }
    if (glowTask != null) { glowTask.cancel(); glowTask = null; }
    if (speedUpdateTask != null) { speedUpdateTask.cancel(); speedUpdateTask = null; }
    if (activityUpdateTask != null) { activityUpdateTask.cancel(); activityUpdateTask = null; }
  }

  public void reload() {
    cleanupTasks();

    chargedHitCooldown = config.getLong("physics.cooldowns.charged-hit", 500);
    regularHitCooldown = config.getLong("physics.cooldowns.regular-hit", 150);
    afkThreshold = config.getLong("physics.afk-threshold", 60000);
    speedCalcInterval = config.getLong("physics.speed-calculation-interval", 5);

    maxKP = config.getDouble("physics.kick-power.max", 6.75);
    softCapMinFactor = config.getDouble("physics.kick-power.soft-cap-min-factor", 0.8);
    chargeMultiplier = config.getDouble("physics.kick-power.charge-multiplier", 7.0);
    basePower = config.getDouble("physics.kick-power.base-power", 0.4);
    chargeRecoveryRate = config.getDouble("physics.charge-recovery-rate", 0.945);

    hitRadius = config.getDouble("physics.distance-thresholds.hit-radius", 1.2);
    hitRadiusSquared = hitRadius * hitRadius;
    minRadius = config.getDouble("physics.distance-thresholds.min-radius", 0.8);
    minRadiusSquared = minRadius * minRadius;
    bounceThreshold = config.getDouble("physics.distance-thresholds.bounce-threshold", 0.3);

    double inactivityRadius = config.getDouble("physics.distance-thresholds.inactivity-radius", 100);
    inactivityRadiusSquared = inactivityRadius * inactivityRadius;

    cubeJumpRightClick = config.getDouble("physics.jump", 0.7);

    soundVolume = (float) config.getDouble("physics.sound.volume", 0.5);
    soundPitch = (float) config.getDouble("physics.sound.pitch", 1.0);

    startPhysicsTask();
    startGlowTask();
    startSpeedCalculation();
    startActivityCheck();
  }

  public void removePlayer(Player player) {
    UUID uuid = player.getUniqueId();
    fcManager.getPlayerSettings().remove(uuid);
    speed.remove(uuid);
    lastLocations.remove(uuid);
    charges.remove(uuid);
    kicked.remove(uuid);
    ballHitCooldowns.remove(uuid);
    lastAction.remove(uuid);
  }

  public void recordPlayerAction(Player player) {
    lastAction.put(player.getUniqueId(), System.currentTimeMillis());
  }

  public boolean isAFK(Player player) {
    long last = lastAction.getOrDefault(player.getUniqueId(), 0L);
    return System.currentTimeMillis() - last > afkThreshold;
  }

  public void cleanup() {
    cleanupTasks();

    cubes.clear();
    inactiveCubesIds.clear();
    velocities.clear();
    kicked.clear();
    speed.clear();
    charges.clear();
    ballHitCooldowns.clear();
    fcManager.getPlayerSettings().clear();
    lastLocations.clear();
    lastAction.clear();
  }
}