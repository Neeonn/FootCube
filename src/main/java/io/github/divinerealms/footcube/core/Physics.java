package io.github.divinerealms.footcube.core;

import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.utils.KickResult;
import io.github.divinerealms.footcube.utils.PlayerSoundSettings;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Getter
public class Physics {
  private final FootCube plugin;
  private final Organization org;

  private final HashSet<Slime> cubes = new HashSet<>();
  private final HashSet<Slime> practiceCubes = new HashSet<>();
  private final Map<UUID, Vector> velocities = new HashMap<>();
  private final Map<UUID, Long> kicked = new HashMap<>();
  private final Map<UUID, Double> speed = new HashMap<>();
  private final Map<UUID, Double> charges = new HashMap<>();
  private final Map<UUID, Long> hitCooldowns = new HashMap<>();
  private final Map<UUID, PlayerSoundSettings> soundSettings = new ConcurrentHashMap<>();

  public static final long HIT_COOLDOWN_MS = 500;
  private static final double MAX_KP = 7.75;
  private static final double SOFT_CAP_MIN_FACTOR = 0.7;
  private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

  @Setter private boolean matchesEnabled = true;
  @Getter public boolean hitDebug = false;

  public Physics(FCManager fcManager) {
    this.plugin = fcManager.getPlugin();
    this.org = fcManager.getOrg();
  }

  public Slime spawnCube(Location location) {
    Slime cube = (Slime) location.getWorld().spawnEntity(location, EntityType.SLIME);
    cube.setRemoveWhenFarAway(false);
    cube.setSize(1);
    cube.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, -3, true), true);
    cubes.add(cube);
    return cube;
  }

  public void removeCubes() {
    List<Entity> entities = plugin.getServer().getWorlds().get(0).getEntities();
    for (Entity entity : entities) {
      if (entity instanceof Slime) {
        cubes.remove(entity);
        ((Slime) entity).setHealth(0.0);
        entity.remove();
      }
    }
  }

  public double getDistance(Location locA, Location locB) {
    locA.add(0.0, -1.0, 0.0);
    double dx = Math.abs(locA.getX() - locB.getX());
    double dy = Math.abs(locA.getY() - locB.getY() - 0.25) - 1.25;
    if (dy < 0.0) dy = 0.0;
    double dz = Math.abs(locA.getZ() - locB.getZ());
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  public KickResult calculateKickPower(Player player) {
    double charge = 1.0 + charges.getOrDefault(player.getUniqueId(), 0.0) * 7.0;
    double speed = this.speed.getOrDefault(player.getUniqueId(), 1.0);
    double power = speed * 2.0 + 0.4;
    double baseKickPower = player.isSneaking() ? charge * power : power;
    double finalKickPower = capKickPower(baseKickPower);

    return new KickResult(power, charge, baseKickPower, finalKickPower);
  }

  private double capKickPower(double baseKickPower) {
    if (baseKickPower <= MAX_KP) return baseKickPower;
    double minRandomKP = MAX_KP * SOFT_CAP_MIN_FACTOR;
    return RANDOM.nextDouble(minRandomKP, MAX_KP); // 5.425 - 7.75
  }

  public boolean isCooldownReady(Player player) {
    long now = System.currentTimeMillis();
    long lastHit = hitCooldowns.getOrDefault(player.getUniqueId(), 0L);
    if (now - lastHit < HIT_COOLDOWN_MS) return false;
    hitCooldowns.put(player.getUniqueId(), now);
    return true;
  }

  public String onHitDebug(Player player, KickResult result) {
    if (player.isSneaking()) {
      return Lang.HITDEBUG_CHARGED.replace(new String[]{
          player.getDisplayName(), (result.getFinalKickPower() != result.getBaseKickPower() ? "&c" : "&a") + String.format("%.2f", result.getFinalKickPower()),
          String.format("%.2f", result.getPower()), String.format("%.2f", result.getCharge())
      });
    } else {
      return Lang.HITDEBUG_REGULAR.replace(new String[]{player.getDisplayName(), String.format("%.2f", result.getFinalKickPower())});
    }
  }

  public void update() {
    long now = System.currentTimeMillis();
    kicked.entrySet().removeIf(uuidLongEntry -> now > uuidLongEntry.getValue() + 1000L);

    for (Map.Entry<UUID, Double> entry : charges.entrySet()) {
      Player player = plugin.getServer().getPlayer(entry.getKey());
      double charge = entry.getValue();
      double nextCharge = 1.0 - (1.0 - charge) * (0.95 - 0.005);
      charges.put(entry.getKey(), nextCharge);
      player.setExp((float) nextCharge);
    }

    Set<Slime> snapshot = new HashSet<>(cubes);
    List<Slime> toRemove = new ArrayList<>();

    for (Slime cube : snapshot) {
      UUID id = cube.getUniqueId();

      if (cube.isDead()) {
        toRemove.add(cube);
        practiceCubes.remove(cube);
        continue;
      }

      Vector oldV = velocities.getOrDefault(id, cube.getVelocity().clone());
      Vector newV = cube.getVelocity().clone();

      boolean sound = false;
      boolean kicked = false;

      for (Entity entity : cube.getNearbyEntities(2, 2, 2)) {
        if (!(entity instanceof Player)) continue;
        Player player = (Player) entity;
        if (player.getGameMode() != GameMode.SURVIVAL) continue;

        double distance = getDistance(cube.getLocation(), player.getLocation());
        if (distance < 1.2) {
          double speed = newV.length();
          if (distance < 0.8 && speed > 0.5) newV.multiply(0.5 / speed);

          double power = this.speed.getOrDefault(player.getUniqueId(), 1.0) / 3.0 + oldV.length() / 6.0;
          newV.add(player.getLocation().getDirection().setY(0).normalize().multiply(power));
          org.ballTouch(player);
          kicked = true;
          if (power > 0.15) sound = true;
        }
      }

      if (newV.getX() == 0.0) {
        newV.setX(-oldV.getX() * 0.8);
        if (Math.abs(oldV.getX()) > 0.3) sound = true;
      } else if (!kicked && Math.abs(oldV.getX() - newV.getX()) < 0.1) newV.setX(oldV.getX() * 0.98);

      if (newV.getZ() == 0.0) {
        newV.setZ(-oldV.getZ() * 0.8);
        if (Math.abs(oldV.getZ()) > 0.3) sound = true;
      } else if (!kicked && Math.abs(oldV.getZ() - newV.getZ()) < 0.1) newV.setZ(oldV.getZ() * 0.98);

      if (newV.getY() < 0.0 && oldV.getY() < 0.0 && oldV.getY() < newV.getY() - 0.05) {
        newV.setY(-oldV.getY() * 0.8);
        if (Math.abs(oldV.getY()) > 0.3) sound = true;
      }

      if (sound) cube.getWorld().playSound(cube.getLocation(), Sound.SLIME_WALK, 0.5F, 1.0F);

      for (Entity entity : cube.getNearbyEntities(2, 2, 2)) {
        if (!(entity instanceof Player)) continue;
        Player player = (Player) entity;
        if (player.getGameMode() != GameMode.SURVIVAL) continue;

        double delta = getDistance(cube.getLocation(), player.getLocation());
        if (delta < newV.length() * 1.3) {
          Vector loc = cube.getLocation().toVector();
          Vector nextLoc = (new Vector(loc.getX(), loc.getY(), loc.getZ())).add(newV);
          boolean rightDirection = true;
          Vector pDir = new Vector(player.getLocation().getX() - loc.getX(), 0.0, player.getLocation().getZ() - loc.getZ());
          Vector cDir = (new Vector(newV.getX(), 0.0, newV.getZ())).normalize();
          int px = 1;
          if (pDir.getX() < 0.0) px = -1;

          int pz = 1;
          if (pDir.getZ() < 0.0) pz = -1;

          int cx = 1;
          if (cDir.getX() < 0.0) cx = -1;

          int cz = 1;
          if (cDir.getZ() < 0.0) cz = -1;

          if (px != cx && pz != cz || (px != cx || pz != cz) && (!(cx * pDir.getX() > (cx * cz * px) * cDir.getZ()) || !(cz * pDir.getZ() > (cz * cx * pz) * cDir.getX()))) {
            rightDirection = false;
          }

          if (rightDirection && loc.getY() < player.getLocation().getY() + 2.0 && loc.getY() > player.getLocation().getY() - 1.0 && nextLoc.getY() < player.getLocation().getY() + 2.0 && nextLoc.getY() > player.getLocation().getY() - 1.0) {
            double a = newV.getZ() / newV.getX();
            double b = loc.getZ() - a * loc.getX();
            double c = player.getLocation().getX();
            double d = player.getLocation().getZ();
            double D = Math.abs(a * c - d + b) / Math.sqrt(a * a + 1.0);
            if (D < 0.8) newV.multiply(delta / newV.length());
          }
        }
      }

      cube.setMaxHealth(20.0);
      cube.setHealth(20.0);
      cube.setVelocity(newV);
      velocities.put(id, newV);
    }

    for (Slime removeCube : toRemove) {
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        cubes.remove(removeCube);
        if (!removeCube.isDead()) removeCube.remove();
      }, 20L);
    }
  }

  public PlayerSoundSettings getSettings(Player player) {
    return soundSettings.computeIfAbsent(player.getUniqueId(), k -> new PlayerSoundSettings());
  }

  public void removePlayer(Player player) {
    soundSettings.remove(player.getUniqueId());
  }
}
