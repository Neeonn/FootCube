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
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PlayerEvents implements Listener {
  private final Logger logger;
  private final Physics physics;
  private final Plugin plugin;
  private final Organization org;
  private final PlayerDataManager dataManager;
  private final DisableCommands disableCommands;

  private static final String PERM_BYPASS_DISABLED_COMMANDS = "footcube.bypass.disablecommands";
  
  public PlayerEvents(FCManager fcManager) {
    this.logger = fcManager.getLogger();
    this.physics = fcManager.getPhysics();
    this.plugin = fcManager.getPlugin();
    this.org = fcManager.getOrg();
    this.dataManager = fcManager.getDataManager();
    this.disableCommands = new DisableCommands(fcManager);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onDisabledCommand(PlayerCommandPreprocessEvent event) {
    Player player = event.getPlayer();

    if (player.hasPermission(PERM_BYPASS_DISABLED_COMMANDS)) return;
    if (!org.isInGame(player)) return;

    String raw = event.getMessage().toLowerCase().trim();
    if (raw.startsWith("/")) raw = raw.substring(1);

    String cmd = raw.split(" ")[0];
    if (cmd.contains(":")) cmd = cmd.split(":")[1];

    if (disableCommands.getCommands().contains(cmd)) {
      logger.send(player, Lang.COMMAND_DISABLER_CANT_USE.replace(null));
      event.setCancelled(true);
    }
  }
  
  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    player.setExp(0);
    org.clearInventory(player);

    plugin.getServer().getScheduler().runTask(plugin, () -> {
      PlayerData playerData = dataManager.get(player);
      dataManager.addDefaults(playerData);
      physics.preloadSettings(player, playerData);
    });
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();

    dataManager.unload(player);
    physics.removePlayer(player);

    Match match = MatchHelper.getMatch(org, player);
    if (match != null) MatchHelper.leaveMatch(org, player, match, logger, true);
  }

  @EventHandler
  public void onMove(PlayerMoveEvent event) {
    if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
    physics.recordPlayerAction(event.getPlayer());
  }

  @EventHandler
  public void playerSpeedConsumption(PlayerInteractEvent event) {
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
  public void playerChargeCalculator(PlayerToggleSneakEvent event) {
    if (event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;

    Player player = event.getPlayer();
    if (event.isSneaking()) {
      physics.getCharges().put(player.getUniqueId(), 0D);
      physics.recordPlayerAction(event.getPlayer());
    } else {
      player.setExp(0F);
      physics.getCharges().remove(player.getUniqueId());
    }
  }

  @EventHandler
  public void onItemDrop(PlayerDropItemEvent event) {
    if (org.isInGame(event.getPlayer())) event.setCancelled(true);
  }

  @EventHandler
  public void onItemPickup(PlayerPickupItemEvent event) {
    if (org.isInGame(event.getPlayer())) event.setCancelled(true);
  }

  @EventHandler
  public void onInventoryInteract(InventoryClickEvent event) {
    if (org.isInGame((Player) event.getWhoClicked())) event.setCancelled(true);
  }

  @EventHandler
  public void onHungerLoss(FoodLevelChangeEvent event) {
    event.setCancelled(true);
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    PlayerSettings settings = physics.getPlayerSettings(player);

    if (org.isInGame(player) || !settings.isBuildEnabled()) event.setCancelled(true);
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    PlayerSettings settings = physics.getPlayerSettings(player);

    if (org.isInGame(player) || !settings.isBuildEnabled()) event.setCancelled(true);
  }
}
