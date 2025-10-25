package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.core.Physics;
import io.github.divinerealms.footcube.utils.KickResult;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import java.util.UUID;

public class BallEvents implements Listener {
  private final FCManager fcManager;
  private final Physics physics;
  private final Organization org;
  private final Logger logger;
  private final Plugin plugin;
  private final BukkitScheduler scheduler;

  private static final String PERM_HIT_DEBUG = "footcube.admin.hitdebug";

  public BallEvents(FCManager fcManager) {
    this.fcManager = fcManager;
    this.physics = fcManager.getPhysics();
    this.org = fcManager.getOrg();
    this.logger = fcManager.getLogger();
    this.plugin = fcManager.getPlugin();
    this.scheduler = plugin.getServer().getScheduler();
  }

  @EventHandler
  public void disableDamage(EntityDamageEvent event) {
    if (event.getEntity() instanceof Slime && physics.getCubes().contains((Slime) event.getEntity())) event.setCancelled(true);
  }

  @EventHandler
  public void hitDetection(EntityDamageByEntityEvent event) {
    if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
    if (!(event.getEntity() instanceof Slime)) return;

    Slime cube = (Slime) event.getEntity();
    if (!physics.getCubes().contains(cube)) return;

    if (!(event.getDamager() instanceof Player)) return;
    Player player = (Player) event.getDamager();
    UUID playerId = player.getUniqueId();

    event.setCancelled(true);

    if (player.getGameMode() == GameMode.CREATIVE) { cube.setHealth(0); logger.send(player, Lang.CUBE_CLEAR.replace(null)); return; }
    if (physics.notAllowedToInteract(player)) return;

    KickResult kickResult = physics.calculateKickPower(player);
    boolean onCooldown = !physics.canHitBall(player);

    if (physics.getCubeHits().contains(playerId)) physics.showHits(player, kickResult);
    if (onCooldown) return;

    physics.getBallHitCooldowns().put(playerId, System.currentTimeMillis());
    physics.recordPlayerAction(player);
    org.ballTouch(player);

    Vector kick = player.getLocation().getDirection().normalize().multiply(kickResult.getFinalKickPower()).setY(0.3);
    cube.setVelocity(kickResult.isChargedHit() ? kick : cube.getVelocity().add(kick));

    scheduler.runTask(plugin, () -> {
      PlayerSettings settings = fcManager.getPlayerSettings(player);
      cube.getWorld().playSound(cube.getLocation(), Sound.SLIME_WALK, 0.5F, 1F);
      if (settings != null && settings.isKickSoundEnabled()) player.playSound(player.getLocation(), settings.getKickSound(), 1.5F, 1.5F);
    });

    if (physics.isHitDebugEnabled()) logger.send(PERM_HIT_DEBUG, physics.onHitDebug(player, kickResult));
  }

  @EventHandler
  public void rightClick(PlayerInteractEntityEvent event) {
    if (!(event.getRightClicked() instanceof Slime)) return;

    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    Slime cube = (Slime) event.getRightClicked();

    if (physics.notAllowedToInteract(player) || physics.isAFK(player)) return;
    if (!physics.getCubes().contains(cube)) return;
    if (physics.getKicked().containsKey(playerId)) return;

    physics.getKicked().put(playerId, System.currentTimeMillis());
    physics.recordPlayerAction(player);
    org.ballTouch(player);

    Vector rise = new Vector(0, physics.getCubeJumpRightClick(), 0);
    cube.setVelocity(cube.getVelocity().add(rise));
    scheduler.runTask(plugin, () -> cube.getWorld().playSound(cube.getLocation(), Sound.SLIME_WALK, 1F, 1F));
  }
}
