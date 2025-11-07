package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Match;
import io.github.divinerealms.footcube.core.MatchHelper;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.DisableCommands;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
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

import java.util.UUID;

import static io.github.divinerealms.footcube.utils.Permissions.PERM_BYPASS_DISABLED_COMMANDS;

/**
 * The {@code PlayerEvents} class listens for and manages all player-related events
 * within the Footcube plugin. It ensures controlled player behavior within matches,
 * synchronizes data loading/unloading, restricts disallowed actions, and integrates
 * custom gameplay logic such as speed boosts and charge mechanics.
 *
 * <p>This class acts as the central hub for handling player lifecycle and in-game
 * interactions that may affect physics or organizational gameplay. It prioritizes
 * security, fairness, and game flow integrity by enforcing server rules and
 * player-specific settings.</p>
 */
public class PlayerEvents implements Listener {
  private final FCManager fcManager;
  private final Logger logger;
  private final Plugin plugin;
  private final Organization org;
  private final PlayerDataManager dataManager;
  private final DisableCommands disableCommands;

  private final PhysicsSystem system;
  
  public PlayerEvents(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.plugin = fcManager.getPlugin();
    this.org = fcManager.getOrg();
    this.dataManager = fcManager.getDataManager();
    this.disableCommands = fcManager.getDisableCommands();

    this.system = fcManager.getPhysicsSystem();
  }

  /**
   * Cancels the execution of restricted commands for players currently engaged in matches,
   * unless they have the bypass permission.
   *
   * @param event the {@link PlayerCommandPreprocessEvent} triggered before a command executes
   */
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

  /**
   * Handles player initialization upon joining the server. This includes clearing inventory,
   * resetting experience, caching the player, and asynchronously loading persistent data.
   *
   * @param event the {@link PlayerJoinEvent} triggered when a player joins
   */
  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    final UUID playerUuid = player.getUniqueId();

    // Reset basic state and cache player reference.
    player.setExp(0);
    player.setLevel(0);
    org.clearInventory(player);
    fcManager.getCachedPlayers().add(player);
    fcManager.getPhysicsSystem().recordPlayerAction(player);

    // Load persistent data asynchronously to avoid main-thread blocking.
    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
      Player asyncPlayer = plugin.getServer().getPlayer(playerUuid);
      if (asyncPlayer == null || !asyncPlayer.isOnline()) return;

      PlayerData playerData = dataManager.get(asyncPlayer);
      dataManager.addDefaults(playerData);
      fcManager.preloadSettings(asyncPlayer, playerData);
    });
  }

  /**
   * Handles cleanup when a player leaves the server, including removing from physics tracking
   * and gracefully exiting ongoing matches.
   *
   * @param event the {@link PlayerQuitEvent} triggered when a player disconnects
   */
  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();

    dataManager.unload(player);
    system.removePlayer(player);
    fcManager.getCachedPlayers().remove(player);
    fcManager.getPlayerSettings().remove(player.getUniqueId());

    // If the player was in a match, handle safe removal.
    Match match = MatchHelper.getMatch(org, player);
    if (match != null) {
      int playerScore, opponentScore;

      if (match.redPlayers.contains(player)) {
        playerScore = match.scoreRed;
        opponentScore = match.scoreBlue;
      } else {
        playerScore = match.scoreBlue;
        opponentScore = match.scoreRed;
      }

      boolean freeLeave = match.phase == 2 || playerScore == opponentScore || playerScore > opponentScore;
      if (!freeLeave) {
        fcManager.getEconomy().withdrawPlayer(player, 200);
        long banUntil = System.currentTimeMillis() + (30 * 60 * 1000);
        org.getLeaveCooldowns().put(player.getUniqueId(), banUntil);
        logger.send(player, Lang.LEAVE_LOSING.replace(null));
      }

      MatchHelper.leaveMatch(org, player, match, logger, false);
    }
  }

  /**
   * Handles player interaction with items to trigger temporary speed boosts (via sugar item use).
   *
   * @param event the {@link PlayerInteractEvent} triggered on right-click
   */
  @EventHandler
  public void playerSpeedConsumption(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    if (system.notAllowedToInteract(player)) return;

    Action action = event.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

    Match match = MatchHelper.getMatch(org, player);

    if (match == null || !match.isInMatch(player)) return;
    if (player.getItemInHand() == null || player.getItemInHand().getType() != Material.SUGAR) return;

    // Consume sugar and apply speed boost.
    player.setItemInHand(new ItemStack(Material.AIR));
    org.getLogger().send(player, Lang.SPEED_USAGE.replace(new String[]{"", "5", "40"}));
    match.sugarCooldown.put(player, System.currentTimeMillis());
    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 5 * 20, 1));
    event.setCancelled(true);
  }

  /** Prevents item drops during a match to maintain fair play. */
  @EventHandler
  public void onItemDrop(PlayerDropItemEvent event) {
    if (org.isInGame(event.getPlayer())) event.setCancelled(true);
  }

  /** Prevents item pickups during matches. */
  @EventHandler
  public void onItemPickup(PlayerPickupItemEvent event) {
    if (org.isInGame(event.getPlayer())) event.setCancelled(true);
  }

  /** Disables inventory interactions while in a match to prevent unintended item use. */
  @EventHandler
  public void onInventoryInteract(InventoryClickEvent event) {
    if (org.isInGame((Player) event.getWhoClicked())) event.setCancelled(true);
  }

  /** Disables hunger depletion to maintain consistent match conditions. */
  @EventHandler
  public void onHungerLoss(FoodLevelChangeEvent event) {
    event.setCancelled(true);
  }

  /**
   * Prevents block placement during matches or for players without build permission.
   *
   * @param event the {@link BlockPlaceEvent} triggered when a block is placed
   */
  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (org.isInGame(player)) event.setCancelled(true);

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    if (settings != null && !settings.isBuildEnabled()) event.setCancelled(true);
  }

  /**
   * Prevents block breaking during matches or for players without build permission.
   *
   * @param event the {@link BlockBreakEvent} triggered when a block is broken
   */
  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (org.isInGame(player)) event.setCancelled(true);

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    if (settings != null && !settings.isBuildEnabled()) event.setCancelled(true);
  }
}
