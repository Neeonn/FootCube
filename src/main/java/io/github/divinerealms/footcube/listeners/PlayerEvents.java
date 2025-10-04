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
import org.bukkit.*;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class PlayerEvents implements Listener {
  private final FCManager fcManager;
  private final Logger logger;
  private final Physics physics;
  private final Organization org;
  private final PlayerDataManager dataManager;
  private final DisableCommands disableCommands;

  private static final String PERM_BYPASS_DISABLED_COMMANDS = "footcube.bypass.disablecommands";
  private static final String CONFIG_SOUNDS_KICK_BASE = "sounds.kick";
  private static final String CONFIG_SOUNDS_GOAL_BASE = "sounds.goal";
  
  public PlayerEvents(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.physics = fcManager.getPhysics();
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
    if (player.getExp() > 0) player.setExp(0);
    org.clearInventory(player);

    Bukkit.getScheduler().runTaskLaterAsynchronously(fcManager.getPlugin(), () -> {
      PlayerData playerData = dataManager.get(player);
      dataManager.addDefaults(playerData);
      handleSounds(player, playerData);
    }, 20L);
  }

  private void handleSounds(Player player, PlayerData playerData) {
    PlayerSettings settings = physics.getPlayerSettings(player);
    if (playerData.has(CONFIG_SOUNDS_KICK_BASE + ".enabled")) settings.setKickSoundEnabled((Boolean) playerData.get(CONFIG_SOUNDS_KICK_BASE + ".enabled"));
    if (playerData.has(CONFIG_SOUNDS_KICK_BASE + ".sound")) settings.setKickSound(Sound.valueOf((String) playerData.get(CONFIG_SOUNDS_KICK_BASE + ".sound")));
    if (playerData.has(CONFIG_SOUNDS_GOAL_BASE + ".enabled")) settings.setGoalSoundEnabled((Boolean) playerData.get(CONFIG_SOUNDS_GOAL_BASE + ".enabled"));
    if (playerData.has(CONFIG_SOUNDS_GOAL_BASE + ".sound")) settings.setGoalSound(Sound.valueOf((String) playerData.get(CONFIG_SOUNDS_GOAL_BASE + ".sound")));
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
    physics.getBallHitCooldowns().remove(uuid);
    physics.getPlayerSettings().remove(uuid);
    physics.getVelocities().remove(uuid);

    Match match = MatchHelper.getMatch(org, player);
    if (match != null) MatchHelper.leaveMatch(org, player, match, logger, true);
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

    if (org.isInGame(player) || !settings.isBuildEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    PlayerSettings settings = physics.getPlayerSettings(player);

    if (org.isInGame(player) || !settings.isBuildEnabled()) {
      event.setCancelled(true);
    }
  }
}
