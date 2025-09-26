package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.core.Physics;
import io.github.divinerealms.footcube.utils.KickResult;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSoundSettings;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

public class SlimeKick implements Listener {
  private final Physics physics;
  private final Organization org;
  private final Logger logger;

  public SlimeKick(FCManager fcManager) {
    this.physics = fcManager.getPhysics();
    this.org = fcManager.getOrg();
    this.logger = fcManager.getLogger();
  }

  @EventHandler
  public void onDamage(EntityDamageEvent event) {
    if (event.getEntity() instanceof Slime && physics.getCubes().contains((Slime) event.getEntity())) event.setCancelled(true);
  }

  @EventHandler
  public void onSlamSlime(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Slime)) return;
    Slime cube = (Slime) event.getEntity();
    if (!physics.getCubes().contains(cube)) return;

    if (!(event.getDamager() instanceof Player)) return;
    Player player = (Player) event.getDamager();
    if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

    event.setCancelled(true);
    if (player.getGameMode() != GameMode.SURVIVAL) return;
    if (player.isSneaking() && !physics.isCooldownReady(player)) return; // Handle hit cooldown if player is crouching
    if (physics.getDistance(player.getLocation(), cube.getLocation()) > 4.0) return; // Max Reach

    org.ballTouch(player);

    KickResult kickResult = physics.calculateKickPower(player);
    Vector kick = player.getLocation().getDirection().normalize().multiply(kickResult.getFinalKickPower()).setY(0.3);
    cube.setVelocity(player.isSneaking() ? kick : cube.getVelocity().add(kick)); // Regular Kick stacks velocity, Charged Kick sets velocity.

    cube.getWorld().playSound(cube.getLocation(), Sound.SLIME_WALK, 0.5F, 1.0F);
    PlayerSoundSettings settings = physics.getSettings(player);
    if (settings.isKickEnabled()) player.playSound(player.getLocation(), settings.getKickSound(), 1.5F, 1.5F);

    if (physics.isHitDebug()) logger.send("footcube.admin.hitdebug", physics.onHitDebug(player, kickResult));
  }
}
