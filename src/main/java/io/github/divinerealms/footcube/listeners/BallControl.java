package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.core.Physics;
import io.github.divinerealms.footcube.utils.KickResult;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

public class BallControl implements Listener {
  private final Physics physics;
  private final Organization org;
  private final Logger logger;

  private static final String PERM_HIT_DEBUG = "footcube.admin.hitdebug";

  public BallControl(FCManager fcManager) {
    this.physics = fcManager.getPhysics();
    this.org = fcManager.getOrg();
    this.logger = fcManager.getLogger();
  }

  @EventHandler
  public void disableBallDamage(EntityDamageEvent event) {
    if (event.getEntity() instanceof Slime && physics.getCubes().contains((Slime) event.getEntity())) event.setCancelled(true);
  }

  @EventHandler
  public void ballHitDetection(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Slime)) return;
    Slime cube = (Slime) event.getEntity();
    if (!physics.getCubes().contains(cube)) return;

    if (!(event.getDamager() instanceof Player)) return;
    Player player = (Player) event.getDamager();
    if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

    event.setCancelled(true);

    if (player.getGameMode() == GameMode.CREATIVE) {
      cube.setHealth(0);
      logger.send(player, Lang.CUBE_CLEAR.replace(null));
    }

    if (player.getGameMode() != GameMode.SURVIVAL) return;
    if (!physics.canHitBall(player)) return; // Handle hit cooldown

    org.ballTouch(player); // Register Cube Hit in FC Matches

    KickResult kickResult = physics.calculateKickPower(player);
    if (kickResult.getPower() > 1D && player.getLocation().distanceSquared(cube.getLocation()) > 4D) return;
    Vector kick = player.getLocation().getDirection().normalize().multiply(kickResult.getFinalKickPower()).setY(0.3);
    cube.setVelocity(kickResult.getCharge() > 1D ? kick : cube.getVelocity().add(kick));

    cube.getWorld().playSound(cube.getLocation(), Sound.SLIME_WALK, 0.5F, 1.0F);
    PlayerSettings settings = physics.getPlayerSettings(player);
    if (settings.isKickSoundEnabled()) player.playSound(player.getLocation(), settings.getKickSound(), 1.5F, 1.5F);

    if (physics.isHitDebug()) logger.send(PERM_HIT_DEBUG, physics.onHitDebug(player, kickResult));
    physics.recordPlayerAction(player);
  }

  @EventHandler
  public void ballRightClick(PlayerInteractEntityEvent event) {
    if (!(event.getRightClicked() instanceof Slime)) return;
    if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
    if (!physics.getCubes().contains((Slime) event.getRightClicked())) return;
    if (physics.getKicked().containsKey(event.getPlayer().getUniqueId())) return;

    Player player = event.getPlayer();
    long now = System.currentTimeMillis();

    Slime cube = (Slime) event.getRightClicked();
    cube.setVelocity(cube.getVelocity().add(new Vector(0.0, 0.7, 0.0)));
    physics.getKicked().put(player.getUniqueId(), now);

    org.ballTouch(player);
    cube.getWorld().playSound(cube.getLocation(), Sound.SLIME_WALK, 1.0F, 1.0F);
    physics.recordPlayerAction(event.getPlayer());
  }

  @EventHandler
  public void playerSpeedCalculator(PlayerMoveEvent event) {
    if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
    Location to = event.getTo();
    Location from = event.getFrom();

    double dx = to.getX() - from.getX();
    double dy = (to.getY() - from.getY()) / 2.0;
    double dz = to.getZ() - from.getZ();

    double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
    physics.getSpeed().put(event.getPlayer().getUniqueId(), speed);
    physics.recordPlayerAction(event.getPlayer());
  }

  @EventHandler
  public void playerChargeCalculator(PlayerToggleSneakEvent event) {
    if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;

    Player player = event.getPlayer();
    if (event.isSneaking()) {
      physics.getCharges().put(player.getUniqueId(), 0.0);
      physics.recordPlayerAction(event.getPlayer());
    } else {
      player.setExp(0.0F);
      physics.getCharges().remove(player.getUniqueId());
    }
  }
}
