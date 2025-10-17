package io.github.divinerealms.footcube.core;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.utils.KickResult;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_8_R3.EntitySlime;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.PathfinderGoalSelector;
import org.bukkit.*;
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

@Getter
public class Physics {
  private final Plugin plugin;
  private final Organization org;
  private final PlayerDataManager dataManager;
  private final FileConfiguration config;
  private final BukkitScheduler scheduler;

  private final HashSet<Slime> cubes = new HashSet<>();
  private final Set<UUID> inactiveCubesIds = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Vector> velocities = new HashMap<>();
  private final Map<UUID, Long> kicked = new HashMap<>();
  private final Map<UUID, Double> speed = new ConcurrentHashMap<>();
  private final Map<UUID, Double> charges = new ConcurrentHashMap<>();
  private final Map<UUID, Long> ballHitCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();
  private final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();

  private long chargedHitCooldown, regularHitCooldown, afkThreshold, speedCalcInterval;
  private double maxKP, softCapMinFactor, chargeMultiplier, basePower, chargeRecoveryRate;
  private double hitRadius, hitRadiusSquared, minRadius, bounceThreshold, movementThreshold;
  private double inactivityRadiusSquared;
  private double cubeJumpRightClick;
  private float soundVolume, soundPitch;

  private static final String CONFIG_SOUNDS_KICK_BASE = "sounds.kick";
  private static final String CONFIG_SOUNDS_GOAL_BASE = "sounds.goal";
  private static final String CONFIG_PARTICLES_BASE = "particles.";
  private static final double DISTANCE_PARTICLE_THRESHOLD_SQUARED = 32 * 32;
  private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

  private BukkitTask speedUpdateTask, activityUpdateTask;

  @Setter private boolean matchesEnabled = true;
  @Getter public boolean hitDebugEnabled = false;

  public Physics(FCManager fcManager) {
    this.plugin = fcManager.getPlugin();
    this.scheduler = plugin.getServer().getScheduler();
    this.org = fcManager.getOrg();
    this.dataManager = fcManager.getDataManager();
    this.config = fcManager.getConfigManager().getConfig("config.yml");
    this.reload();
    this.startSpeedCalculation();
    this.startActivityCheck();
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
      Field gField = PathfinderGoalSelector.class.getDeclaredField("b");
      gField.setAccessible(true);
      gField.set(nmsSlime.goalSelector, new java.util.LinkedList<>());
      gField.set(nmsSlime.targetSelector, new java.util.LinkedList<>());
    } catch (Exception exception) {
      plugin.getLogger().log(Level.SEVERE, "Error: ", exception);
    }

    cubes.add(cube);
    inactiveCubesIds.remove(cube.getUniqueId());
    return cube;
  }

  public void removeCubes() {
    List<Entity> entities = plugin.getServer().getWorlds().get(0).getEntities();
    for (Entity entity : entities) if (entity instanceof Slime) ((Slime) entity).setHealth(0);
  }

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

  public boolean canHitBall(Player player) {
    long now = System.currentTimeMillis();
    long cooldown = player.isSneaking() ? chargedHitCooldown : regularHitCooldown;
    long lastHit = ballHitCooldowns.getOrDefault(player.getUniqueId(), 0L);
    if (now - lastHit < cooldown) return false;
    ballHitCooldowns.put(player.getUniqueId(), now);
    return true;
  }

  public String onHitDebug(Player player, KickResult result) {
    return result.isChargedHit()
        ? Lang.HITDEBUG_CHARGED.replace(new String[]{
            player.getDisplayName(), (result.getFinalKickPower() != result.getBaseKickPower() ? "&c" : "&a") + String.format("%.2f", result.getFinalKickPower()),
            String.format("%.2f", result.getPower()), String.format("%.2f", result.getCharge())
        })
        : Lang.HITDEBUG_REGULAR.replace(new String[]{player.getDisplayName(), String.format("%.2f", result.getFinalKickPower())});
  }

  public void tick() {
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

        double distanceSq = getDistanceSquared(cube.getLocation(), player.getLocation());
        double distance = -1;

        if (distanceSq <= hitRadiusSquared) {
          distance = Math.sqrt(distanceSq);

          double speed = newV.length();
          if (distance <= minRadius && speed >= 0.5) newV.multiply(0.5 / speed);

          double power = this.speed.getOrDefault(player.getUniqueId(), 0D) / 3 + oldV.length() / 6;
          newV.add(player.getLocation().getDirection().setY(0).normalize().multiply(power));
          org.ballTouch(player);
          kicked = true;
          if (power > 0.15) sound = true;
        }

        double delta = (distance > -1) ? distance : getDistance(cube.getLocation(), player.getLocation());

        if (delta < newV.length() * 1.3) {
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
            double D = Math.abs(a * player.getLocation().getX() - player.getLocation().getZ() + b) / Math.sqrt(a * a + 1);

            if (D < minRadius) newV.multiply(delta / newV.length());
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

    if (!toRemove.isEmpty()) {
      Bukkit.getScheduler().runTaskLater(plugin, () -> toRemove.forEach(slime -> {
        cubes.remove(slime);
        if (!slime.isDead()) slime.remove();
      }), 20L);
    }
  }

  public void showCubeParticles() {
    Collection<? extends Player> onlinePlayers = plugin.getServer().getOnlinePlayers();
    if (onlinePlayers.isEmpty() || cubes.isEmpty()) return;

    for (Slime cube : cubes) {
      if (cube == null || cube.isDead() || cube.getLocation() == null) continue;
      if (inactiveCubesIds.contains(cube.getUniqueId())) continue;

      Location cubeLocation = cube.getLocation().clone().add(0, 0.25, 0);

      for (Player player : onlinePlayers) {
        PlayerSettings settings = getPlayerSettings(player);
        if (settings == null || !settings.isParticlesEnabled()) continue;

        if (cube.getLocation().distanceSquared(player.getLocation()) < DISTANCE_PARTICLE_THRESHOLD_SQUARED) continue;

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

  public PlayerSettings getPlayerSettings(Player player) {
    return playerSettings.get(player.getUniqueId());
  }

  public void preloadSettings(Player player, PlayerData playerData) {
    PlayerSettings settings = getPlayerSettings(player);
    if (settings == null) {
      settings = new PlayerSettings();
      playerSettings.put(player.getUniqueId(), settings);
    }

    if (playerData.has(CONFIG_PARTICLES_BASE + ".effect")) {
      String effect = (String) playerData.get(CONFIG_PARTICLES_BASE + ".effect");
      try {
        EnumParticle particle = EnumParticle.valueOf(effect.split(":")[0]);
        settings.setParticle(particle);

        if (particle == EnumParticle.REDSTONE && effect.contains(":")) {
          String colorName = effect.split(":")[1];
          try {
            settings.setCustomRedstoneColor(colorName);
          } catch (IllegalArgumentException ignored) {}
        }
      } catch (IllegalArgumentException exception) {
        plugin.getLogger().log(Level.WARNING, "Invalid particle effect found for player " + player.getName() + ": " + effect);
      }
    }

    if (playerData.has(CONFIG_SOUNDS_KICK_BASE + ".enabled")) settings.setKickSoundEnabled((Boolean) playerData.get(CONFIG_SOUNDS_KICK_BASE + ".enabled"));
    if (playerData.has(CONFIG_SOUNDS_KICK_BASE + ".sound")) settings.setKickSound(Sound.valueOf((String) playerData.get(CONFIG_SOUNDS_KICK_BASE + ".sound")));
    if (playerData.has(CONFIG_SOUNDS_GOAL_BASE + ".enabled")) settings.setGoalSoundEnabled((Boolean) playerData.get(CONFIG_SOUNDS_GOAL_BASE + ".enabled"));
    if (playerData.has(CONFIG_SOUNDS_GOAL_BASE + ".sound")) settings.setGoalSound(Sound.valueOf((String) playerData.get(CONFIG_SOUNDS_GOAL_BASE + ".sound")));
    if (playerData.has(CONFIG_PARTICLES_BASE + ".enabled")) settings.setParticlesEnabled((Boolean) playerData.get(CONFIG_PARTICLES_BASE + ".enabled"));
  }

  public void reload() {
    chargedHitCooldown = config.getLong("physics.cooldowns.charged-hit", 500);
    regularHitCooldown = config.getLong("physics.cooldowns.regular-hit", 150);
    afkThreshold = config.getLong("physics.afk-threshold", 60000);
    speedCalcInterval = config.getLong("physics.speed-calculation-interval", 5L);

    maxKP = config.getDouble("physics.kick-power.max", 6.75);
    softCapMinFactor = config.getDouble("physics.kick-power.soft-cap-min-factor", 0.8);
    chargeMultiplier = config.getDouble("physics.kick-power.charge-multiplier", 7.0);
    basePower = config.getDouble("physics.kick-power.base-power", 0.4);
    chargeRecoveryRate = config.getDouble("physics.charge-recovery-rate", 0.945);

    hitRadius = config.getDouble("physics.distance-thresholds.hit-radius", 1.2);
    hitRadiusSquared = hitRadius * hitRadius;
    minRadius = config.getDouble("physics.distance-thresholds.min-radius", 0.8);
    bounceThreshold = config.getDouble("physics.distance-thresholds.bounce-threshold", 0.3);
    movementThreshold = config.getDouble("physics.movement-threshold", 0.05D);

    double inactivityRadius = config.getDouble("physics.distance-thresholds.inactivity-radius", 100D);
    inactivityRadiusSquared = inactivityRadius * inactivityRadius;

    cubeJumpRightClick = config.getDouble("physics.jump", 0.7D);

    soundVolume = (float) config.getDouble("physics.sound.volume", 0.5);
    soundPitch = (float) config.getDouble("physics.sound.pitch", 1.0);

    startSpeedCalculation();
    startActivityCheck();
  }

  public void removePlayer(Player player) {
    UUID uuid = player.getUniqueId();
    playerSettings.remove(uuid);
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
    if (speedUpdateTask != null) speedUpdateTask.cancel();
    if (activityUpdateTask != null) activityUpdateTask.cancel();
    cubes.clear();
    inactiveCubesIds.clear();
    velocities.clear();
    kicked.clear();
    speed.clear();
    charges.clear();
    ballHitCooldowns.clear();
    playerSettings.clear();
    lastAction.clear();
  }
}