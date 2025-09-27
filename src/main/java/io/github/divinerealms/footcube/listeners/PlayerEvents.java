package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Match;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.core.Physics;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.utils.DisableCommands;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.MatchHelper;
import io.github.divinerealms.footcube.utils.PlayerSoundSettings;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

public class PlayerEvents implements Listener {
  private final FCManager fcManager;
  private final Logger logger;
  private final Physics physics;
  private final Organization org;
  private final PlayerDataManager dataManager;
  private final DisableCommands disableCommands;
  
  public PlayerEvents(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.physics = fcManager.getPhysics();
    this.org = fcManager.getOrg();
    this.dataManager = fcManager.getDataManager();
    this.disableCommands = new DisableCommands(fcManager);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPreprocess(PlayerCommandPreprocessEvent event) {
    Player player = event.getPlayer();

    if (player.hasPermission("footcube.bypass.disablecommands")) return;
    if (org.isInGame(player)) {
      String raw = event.getMessage().toLowerCase().trim();
      if (raw.startsWith("/")) raw = raw.substring(1);

      String cmd = raw.split(" ")[0];
      if (cmd.contains(":")) cmd = cmd.split(":")[1];

      if (disableCommands.getCommands().contains(cmd)) {
        logger.send(player, Lang.COMMAND_DISABLER_CANT_USE.replace(null));
        event.setCancelled(true);
      }
    }
  }
  
  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    if (player.getExp() > 0) player.setExp(0);
    org.clearInventory(player);

    Bukkit.getScheduler().runTaskAsynchronously(fcManager.getPlugin(), () -> {
      PlayerData playerData = dataManager.get(player);
      dataManager.addDefaults(playerData);

      Bukkit.getScheduler().runTaskLater(fcManager.getPlugin(), () -> handleSounds(player, playerData), 20L);
    });
  }

  private void handleSounds(Player player, PlayerData playerData) {
    PlayerSoundSettings settings = physics.getSettings(player);
    if (playerData.has("sounds.kick.enabled")) settings.setKickEnabled((Boolean) playerData.get("sounds.kick.enabled"));
    if (playerData.has("sounds.kick.sound")) settings.setKickSound(Sound.valueOf((String) playerData.get("sounds.kick.sound")));
    if (playerData.has("sounds.goal.enabled")) settings.setGoalEnabled((Boolean) playerData.get("sounds.goal.enabled"));
    if (playerData.has("sounds.goal.sound")) settings.setGoalSound(Sound.valueOf((String) playerData.get("sounds.goal.sound")));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();

    dataManager.unload(player);
    physics.removePlayer(player);

    physics.getSpeed().remove(uuid);
    physics.getCharges().remove(uuid);
    physics.getKicked().remove(uuid);
    physics.getHitCooldowns().remove(uuid);
    physics.getSoundSettings().remove(uuid);
    physics.getVelocities().remove(uuid);

    Match match = MatchHelper.getMatch(org, player);
    if (match != null) MatchHelper.leaveMatch(org, player, match, logger, true);
  }

  @EventHandler
  public void onMove(PlayerMoveEvent event) {
    if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
    Location to = event.getTo();
    Location from = event.getFrom();
    double dx = to.getX() - from.getX();
    double dy = (to.getY() - from.getY()) / 2.0;
    double dz = to.getZ() - from.getZ();
    double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
    physics.getSpeed().put(event.getPlayer().getUniqueId(), speed);
  }

  @EventHandler
  public void onRightClick(PlayerInteractEntityEvent event) {
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
  }

  @EventHandler
  public void onSneak(PlayerToggleSneakEvent event) {
    if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
    Player player = event.getPlayer();
    if (event.isSneaking()) {
      physics.getCharges().put(player.getUniqueId(), (double) 0.0F);
    } else {
      player.setExp(0.0F);
      physics.getCharges().remove(player.getUniqueId());
    }
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent event) {
    if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
    Action action = event.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

    Player player = event.getPlayer();
    Match match = MatchHelper.getMatch(org, player);

    if (match == null || !match.isInMatch(player)) return;
    if (player.getItemInHand() == null || player.getItemInHand().getType() != Material.SUGAR) return;

    player.setItemInHand(new ItemStack(Material.AIR));
    org.getLogger().send(player, Lang.SPEED_USAGE.replace(new String[]{"", "5", "40"}));
    match.sugarCooldown.put(player, System.currentTimeMillis());
    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 5 * 20, 1));
    event.setCancelled(true);
  }

  @EventHandler
  public void onItemDrop(PlayerDropItemEvent event) {
    Player player = event.getPlayer();
    if (org.isInGame(player)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onItemPickup(PlayerPickupItemEvent event) {
    Player player = event.getPlayer();
    if (org.isInGame(player)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onInventoryInteract(InventoryClickEvent event) {
    Player player = (Player) event.getWhoClicked();
    if (org.isInGame(player)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onHungerLoss(FoodLevelChangeEvent event) {
    event.setCancelled(true);
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    if (org.isInGame(event.getPlayer())) event.setCancelled(true);
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    if (org.isInGame(event.getPlayer())) event.setCancelled(true);
  }
}
