package io.github.divinerealms.footcube.core;

import io.github.divinerealms.footcube.FootCube;
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
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftSlime;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

@Getter
public class Physics {
  private final FootCube plugin;
  private final Organization org;
  private final PlayerDataManager dataManager;

  private final HashSet<Slime> cubes = new HashSet<>();
  private final Map<UUID, Vector> velocities = new HashMap<>();
  private final Map<UUID, Long> kicked = new HashMap<>();
  private final Map<UUID, Double> speed = new HashMap<>();
  private final Map<UUID, Double> charges = new HashMap<>();
  private final Map<UUID, Long> ballHitCooldowns = new HashMap<>();
  private final Map<UUID, Long> lastAction = new HashMap<>();
  private final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();

  private static final long CHARGED_HIT_COOLDOWN = 500L;
  private static final long REGULAR_HIT_COOLDOWN = 150L;
  private static final long AFK_THRESHOLD = 60_000L;
  private static final double MAX_KP = 7.25;
  private static final double SOFT_CAP_MIN_FACTOR = 0.75;
  private static final String CONFIG_SOUNDS_KICK_BASE = "sounds.kick";
  private static final String CONFIG_SOUNDS_GOAL_BASE = "sounds.goal";
  private static final String CONFIG_PARTICLES_BASE = "particles.";
  private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

  @Setter private boolean matchesEnabled = true;
  @Getter public boolean hitDebugEnabled = false;

  public Physics(FCManager fcManager) {
    this.plugin = fcManager.getPlugin();
    this.org = fcManager.getOrg();
    this.dataManager = fcManager.getDataManager();
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
    return cube;
  }

  public void removeCubes() {
    List<Entity> entities = plugin.getServer().getWorlds().get(0).getEntities();
    for (Entity entity : entities) if (entity instanceof Slime) ((Slime) entity).setHealth(0);
  }

  public double getDistance(Location locA, Location locB) {
    double dx = locA.getX() - locB.getX();
    double dy = (locA.getY() - 1) - locB.getY() - 0.25 - 1.25;
    if (dy < 0) dy = 0;
    double dz = locA.getZ() - locB.getZ();

    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  public KickResult calculateKickPower(Player player) {
    double charge = 1 + charges.getOrDefault(player.getUniqueId(), 0D) * 7;
    double speed = this.speed.getOrDefault(player.getUniqueId(), 1D);
    double power = speed * 2 + 0.4;
    double baseKickPower = player.isSneaking() ? charge * power : power;
    double finalKickPower = capKickPower(baseKickPower);

    return new KickResult(power, charge, baseKickPower, finalKickPower);
  }

  private double capKickPower(double baseKickPower) {
    if (baseKickPower <= MAX_KP) return baseKickPower;
    double minRandomKP = MAX_KP * SOFT_CAP_MIN_FACTOR;
    return RANDOM.nextDouble(minRandomKP, MAX_KP);
  }

  public boolean canHitBall(Player player) {
    long now = System.currentTimeMillis();
    long cooldown = player.isSneaking() ? CHARGED_HIT_COOLDOWN : REGULAR_HIT_COOLDOWN;
    long lastHit = ballHitCooldowns.getOrDefault(player.getUniqueId(), 0L);
    if (now - lastHit < cooldown) return false;
    ballHitCooldowns.put(player.getUniqueId(), now);
    return true;
  }

  public String onHitDebug(Player player, KickResult result) {
    if (result.getCharge() > 1D) {
      return Lang.HITDEBUG_CHARGED.replace(new String[]{
          player.getDisplayName(), (result.getFinalKickPower() != result.getBaseKickPower() ? "&c" : "&a") + String.format("%.2f", result.getFinalKickPower()),
          String.format("%.2f", result.getPower()), String.format("%.2f", result.getCharge())
      });
    } else {
      return Lang.HITDEBUG_REGULAR.replace(new String[]{player.getDisplayName(), String.format("%.2f", result.getFinalKickPower())});
    }
  }

  public void tick() {
    long now = System.currentTimeMillis();
    kicked.entrySet().removeIf(uuidLongEntry -> now > uuidLongEntry.getValue() + 1000L);

    for (Map.Entry<UUID, Double> entry : charges.entrySet()) {
      Player player = plugin.getServer().getPlayer(entry.getKey());
      if (player == null) continue;

      double charge = entry.getValue();
      double nextCharge = 1 - (1 - charge) * 0.945;
      charges.put(entry.getKey(), nextCharge);
      player.setExp((float) nextCharge);
    }

    List<Slime> toRemove = new ArrayList<>();

    for (Slime cube : cubes) {
      if (cubes.isEmpty()) continue;

      if (cube.isDead()) {
        toRemove.add(cube);
        continue;
      }

      UUID id = cube.getUniqueId();
      Vector oldV = velocities.getOrDefault(id, cube.getVelocity().clone());
      Vector newV = cube.getVelocity().clone();

      boolean sound = false;
      boolean kicked = false;

      List<Entity> nearby = cube.getNearbyEntities(2, 1, 2);
      for (Entity entity : nearby) {
        if (!(entity instanceof Player)) continue;
        Player player = (Player) entity;
        if (player.getGameMode() != GameMode.SURVIVAL) continue;
        if (isAFK(player)) continue;

        double distance = getDistance(cube.getLocation(), player.getLocation());
        if (distance <= 1.2) {
          double speed = newV.length();
          if (distance <= 0.8 && speed >= 0.5) newV.multiply(0.5 / speed);

          double power = this.speed.getOrDefault(player.getUniqueId(), 1D) / 3 + oldV.length() / 6;
          newV.add(player.getLocation().getDirection().setY(0).normalize().multiply(power));
          org.ballTouch(player);
          kicked = true;
          if (power > 0.15) sound = true;
        }

        double delta = getDistance(cube.getLocation(), player.getLocation());
        if (delta < newV.length() * 1.3) {
          Vector loc = cube.getLocation().toVector();
          Vector nextLoc = (new Vector(loc.getX(), loc.getY(), loc.getZ())).add(newV);

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

            if (D < 0.8) newV.multiply(delta / newV.length());
          }
        }
      }

      if (newV.getX() == 0) {
        newV.setX(-oldV.getX() * 0.8);
        if (Math.abs(oldV.getX()) > 0.3) sound = true;
      } else if (!kicked && Math.abs(oldV.getX() - newV.getX()) < 0.1) newV.setX(oldV.getX() * 0.98);

      if (newV.getZ() == 0) {
        newV.setZ(-oldV.getZ() * 0.8);
        if (Math.abs(oldV.getZ()) > 0.3) sound = true;
      } else if (!kicked && Math.abs(oldV.getZ() - newV.getZ()) < 0.1) newV.setZ(oldV.getZ() * 0.98);

      if (newV.getY() < 0 && oldV.getY() < 0 && oldV.getY() < newV.getY() - 0.05) {
        newV.setY(-oldV.getY() * 0.8);
        if (Math.abs(oldV.getY()) > 0.3) sound = true;
      }

      if (sound) cube.getWorld().playSound(cube.getLocation(), Sound.SLIME_WALK, 0.5F, 1F);

      cube.setVelocity(newV);
      velocities.put(id, newV);
    }

    if (!toRemove.isEmpty()) {
      toRemove.forEach(slime -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
        cubes.remove(slime);
        if (!slime.isDead()) slime.remove();
      }, 20L));
    }
  }

  public void showCubeParticles() {
    for (Slime cube : cubes) {
      if (cube == null || cube.isDead() || cube.getLocation() == null) continue;

      for (Entity entity : cube.getNearbyEntities(100, 100, 100)) {
        if (!(entity instanceof Player)) continue;
        Player player = (Player) entity;
        PlayerSettings settings = getPlayerSettings(player);
        if (settings == null || !settings.isParticlesEnabled()) continue;

        double distance = getDistance(cube.getLocation(), player.getLocation());
        if (distance < 32) continue;

        Location cubeLocation = cube.getLocation().clone().add(0, 0.25, 0);
        EnumParticle particle = settings.getParticle();
        if (particle == EnumParticle.REDSTONE) {
          PlayerData playerData = dataManager.get(player);
          if (playerData != null) {
            Object effectObj = playerData.get("particles.effect");
            if (effectObj != null) {
              String effect = effectObj.toString();
              if (effect.contains(":")) {
                String colorName = effect.split(":")[1];
                try {
                  settings.setCustomRedstoneColor(colorName);
                } catch (IllegalArgumentException ignored) { }
              }
            }
          }

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

    if (playerData.has(CONFIG_SOUNDS_KICK_BASE + ".enabled")) settings.setKickSoundEnabled((Boolean) playerData.get(CONFIG_SOUNDS_KICK_BASE + ".enabled"));
    if (playerData.has(CONFIG_SOUNDS_KICK_BASE + ".sound")) settings.setKickSound(Sound.valueOf((String) playerData.get(CONFIG_SOUNDS_KICK_BASE + ".sound")));
    if (playerData.has(CONFIG_SOUNDS_GOAL_BASE + ".enabled")) settings.setGoalSoundEnabled((Boolean) playerData.get(CONFIG_SOUNDS_GOAL_BASE + ".enabled"));
    if (playerData.has(CONFIG_SOUNDS_GOAL_BASE + ".sound")) settings.setGoalSound(Sound.valueOf((String) playerData.get(CONFIG_SOUNDS_GOAL_BASE + ".sound")));
    if (playerData.has(CONFIG_PARTICLES_BASE + ".enabled")) settings.setParticlesEnabled((Boolean) playerData.get(CONFIG_PARTICLES_BASE + ".enabled"));
    if (playerData.has(CONFIG_PARTICLES_BASE + ".effect")) settings.setParticle(EnumParticle.valueOf((String) playerData.get(CONFIG_PARTICLES_BASE + ".effect")));
  }

  public void removePlayer(Player player) {
    playerSettings.remove(player.getUniqueId());
  }

  public void recordPlayerAction(Player player) {
    lastAction.put(player.getUniqueId(), System.currentTimeMillis());
  }

  public boolean isAFK(Player player) {
    long last = lastAction.getOrDefault(player.getUniqueId(), 0L);
    return System.currentTimeMillis() - last > AFK_THRESHOLD;
  }

  public void cleanup() {
    cubes.clear();
    velocities.clear();
    kicked.clear();
    speed.clear();
    charges.clear();
    ballHitCooldowns.clear();
    playerSettings.clear();
    lastAction.clear();
  }
}