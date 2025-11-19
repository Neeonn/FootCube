package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.team.Team;
import io.github.divinerealms.footcube.matchmaking.team.TeamManager;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.DisableCommands;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

import static io.github.divinerealms.footcube.utils.Permissions.PERM_BYPASS_DISABLED_COMMANDS;

public class PlayerEvents implements Listener {
  private final FCManager fcManager;
  private final Logger logger;
  private final Plugin plugin;
  private final MatchManager matchManager;
  private final TeamManager teamManager;
  private final PlayerDataManager dataManager;
  private final DisableCommands disableCommands;
  private final PhysicsSystem system;

  public PlayerEvents(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.plugin = fcManager.getPlugin();
    this.matchManager = fcManager.getMatchManager();
    this.teamManager = matchManager.getTeamManager();
    this.dataManager = fcManager.getDataManager();
    this.disableCommands = fcManager.getDisableCommands();
    this.system = fcManager.getPhysicsSystem();
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onDisabledCommand(PlayerCommandPreprocessEvent event) {
    Player player = event.getPlayer();
    if (event.getMessage().equalsIgnoreCase("/tab reload")) fcManager.reloadTabAPI();

    if (player.hasPermission(PERM_BYPASS_DISABLED_COMMANDS)) return;
    if (!matchManager.getMatch(player).isPresent()) return;

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
    final UUID playerUuid = player.getUniqueId();

    player.setExp(0);
    player.setLevel(0);
    fcManager.getCachedPlayers().add(player);
    system.recordPlayerAction(player);

    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
      Player asyncPlayer = plugin.getServer().getPlayer(playerUuid);
      if (asyncPlayer == null || !asyncPlayer.isOnline()) return;

      PlayerData playerData = dataManager.get(asyncPlayer);
      dataManager.addDefaults(playerData);
      fcManager.preloadSettings(asyncPlayer, playerData);
    });
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    dataManager.unload(player);
    system.removePlayer(player);
    fcManager.getCachedPlayers().remove(player);
    fcManager.getPlayerSettings().remove(player.getUniqueId());

    if (teamManager.isInTeam(player)) {
      Team team = teamManager.getTeam(player);
      if (team != null) {
        team.getMembers().stream()
            .filter(p -> p != null && p.isOnline() && !p.equals(player))
            .forEach(p -> logger.send(p, Lang.TEAM_DISBANDED.replace(new String[]{player.getName()})));
        teamManager.disbandTeam(team);
      }
    }

    Optional<Match> matchOpt = matchManager.getMatch(player);
    if (matchOpt.isPresent()) {
      Match match = matchOpt.get();
      Optional<MatchPlayer> matchPlayerOpt = match.getPlayers().stream().filter(p -> p.getPlayer().equals(player)).findFirst();
      if (matchPlayerOpt.isPresent()) {
        MatchPlayer matchPlayer = matchPlayerOpt.get();

        int playerScore = matchPlayer.getTeamColor() == io.github.divinerealms.footcube.matchmaking.player.TeamColor.RED ? match.getScoreRed() : match.getScoreBlue();
        int opponentScore = matchPlayer.getTeamColor() == io.github.divinerealms.footcube.matchmaking.player.TeamColor.RED ? match.getScoreBlue() : match.getScoreRed();
        if (match.getPhase() == io.github.divinerealms.footcube.matchmaking.MatchPhase.IN_PROGRESS && playerScore < opponentScore) {
          fcManager.getEconomy().withdrawPlayer(player, 200);
          matchManager.getBanManager().banPlayer(player, 30 * 60 * 1000);
        }
      }
      matchManager.leaveMatch(player);
    }
  }

  @EventHandler
  public void onItemDrop(PlayerDropItemEvent event) {
    Player player = event.getPlayer();
    if (matchManager.getMatch(player).isPresent()) event.setCancelled(true);
  }

  @EventHandler
  public void onItemPickup(PlayerPickupItemEvent event) {
    Player player = event.getPlayer();
    if (matchManager.getMatch(player).isPresent()) event.setCancelled(true);
  }

  @EventHandler
  public void onInventoryInteract(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) return;
    Player player = (Player) event.getWhoClicked();

    if (matchManager.getMatch(player).isPresent()) event.setCancelled(true);
  }

  @EventHandler
  public void onHungerLoss(FoodLevelChangeEvent event) {
    event.setCancelled(true);
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (matchManager.getMatch(player).isPresent()) {
      event.setCancelled(true);
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    if (settings != null && !settings.isBuildEnabled()) event.setCancelled(true);
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (matchManager.getMatch(player).isPresent()) {
      event.setCancelled(true);
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    if (settings != null && !settings.isBuildEnabled()) event.setCancelled(true);
  }
}
