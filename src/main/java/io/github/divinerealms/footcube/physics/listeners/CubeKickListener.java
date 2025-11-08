package io.github.divinerealms.footcube.physics.listeners;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.core.TouchType;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.touch.CubeTouchInfo;
import io.github.divinerealms.footcube.physics.touch.CubeTouchType;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.physics.utilities.PlayerKickResult;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_CLEAR_CUBE;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_HIT_DEBUG;

public class CubeKickListener implements Listener {
  private final FCManager fcManager;
  private final Organization org;

  private final BukkitScheduler scheduler;
  private final Plugin plugin;
  private final Logger logger;

  private final PhysicsSystem system;
  private final PhysicsData data;

  public CubeKickListener(FCManager fcManager) {
    this.fcManager = fcManager;
    this.org = fcManager.getOrg();

    this.scheduler = fcManager.getScheduler();
    this.plugin = fcManager.getPlugin();
    this.logger = fcManager.getLogger();

    this.system = fcManager.getPhysicsSystem();
    this.data = fcManager.getPhysicsData();
  }

  /**
   * Handles custom hit detection for cube entities when attacked by players.
   * <p>Replaces standard attack behavior with physics-driven logic such as applying
   * kick velocity, cooldown tracking, and custom hit effects.</p>
   *
   * @param event the {@link EntityDamageByEntityEvent} triggered when one entity damages another
   */
  @EventHandler
  public void leftClick(EntityDamageByEntityEvent event) {
    long start = System.nanoTime();
    try {
      if (!(event.getEntity() instanceof Slime)) return;
      if (!(event.getDamager() instanceof Player)) return;
      if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
      if (!data.getCubes().contains((Slime) event.getEntity())) return;

      Slime cube = (Slime) event.getEntity();
      Player player = (Player) event.getDamager();
      UUID playerId = player.getUniqueId();

      // Creative players can remove cubes directly.
      if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission(PERM_CLEAR_CUBE)) {
        cube.setHealth(0); logger.send(player, Lang.CUBE_CLEAR.replace(null)); return;
      }

      // Prevent AFK or unauthorized players from interacting.
      if (system.notAllowedToInteract(player)) return;

      // Calculate kick result and enforce cooldown.
      PlayerKickResult kickResult = system.calculateKickPower(player);
      Map<CubeTouchType, CubeTouchInfo> touches = data.getLastTouches().computeIfAbsent(playerId, key -> new ConcurrentHashMap<>());
      CubeTouchType kickType = kickResult.isChargedHit() ? CubeTouchType.CHARGED_KICK : CubeTouchType.REGULAR_KICK;
      if (touches.containsKey(kickType)) return;

      // Compute final kick direction and apply impulse.
      Location playerLocation = player.getLocation();
      Vector kick = player.getLocation().getDirection().normalize().multiply(kickResult.getFinalKickPower()).setY(KICK_VERTICAL_BOOST);

      cube.setVelocity(cube.getVelocity().add(kick));
      system.queueSound(cube.getLocation(), Sound.SLIME_WALK, 0.75F, 1.0F);

      // Register player hit cooldown and record interaction.
      touches.put(kickType, new CubeTouchInfo(System.currentTimeMillis(), kickType));
      system.recordPlayerAction(player);
      org.ballTouch(player, TouchType.HIT);

      // Schedule post-processing for player sound feedback and debug info.
      // #TODO: Change to lastTouches.
      // if (data.getCubeHits().contains(playerId)) system.showHits(player, kickResult);
      scheduler.runTask(plugin, () -> {
        PlayerSettings settings = fcManager.getPlayerSettings(player);
        if (settings != null && settings.isKickSoundEnabled()) system.queueSound(player, settings.getKickSound(), SOUND_VOLUME, SOUND_PITCH);
        if (data.isHitDebugEnabled()) logger.send(PERM_HIT_DEBUG, playerLocation, 100, system.onHitDebug(player, kickResult));
      });

      event.setCancelled(true);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      if (ms > DEBUG_ON_MS) logger.send("group.fcfa", "{prefix-admin}&dCubeKickListener &ftook &e" + ms + "ms");
    }
  }
}
