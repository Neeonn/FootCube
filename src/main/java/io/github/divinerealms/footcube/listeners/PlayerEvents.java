package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.player.MatchPlayer;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
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

import java.util.*;

import static io.github.divinerealms.footcube.configs.Lang.COMMAND_DISABLER_CANT_USE;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_DISBANDED;
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
    if (event.getMessage().equalsIgnoreCase("/tab reload")) {
      fcManager.reloadTabAPI();
    }

    if (player.hasPermission(PERM_BYPASS_DISABLED_COMMANDS)) {
      return;
    }

    if (matchManager.getMatch(player).isEmpty()) {
      return;
    }

    String raw = event.getMessage().toLowerCase().trim();
    if (raw.startsWith("/")) {
      raw = raw.substring(1);
    }

    String cmd = raw.split(" ")[0];
    if (cmd.contains(":")) {
      cmd = cmd.split(":")[1];
    }

    if (disableCommands.getCommands().contains(cmd)) {
      logger.send(player, COMMAND_DISABLER_CANT_USE);
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    final UUID playerUuid = player.getUniqueId();

    player.setExp(0);
    player.setLevel(0);
    system.recordPlayerAction(player);

    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
      Player asyncPlayer = plugin.getServer().getPlayer(playerUuid);
      if (asyncPlayer == null || !asyncPlayer.isOnline()) {
        return;
      }

      PlayerData playerData = dataManager.get(asyncPlayer);
      dataManager.addDefaults(playerData);
      fcManager.preloadSettings(asyncPlayer, playerData);
      fcManager.getCachedPlayers().add(asyncPlayer);
      fcManager.cachePrefixedName(asyncPlayer);
    });
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    if (player == null) {
      return;
    }

    dataManager.unload(player);
    system.removePlayer(player);
    fcManager.getCachedPlayers().remove(player);
    fcManager.getPlayerSettings().remove(player.getUniqueId());
    fcManager.getCachedPrefixedNames().remove(player.getUniqueId());

    Collection<Queue<Player>> playerQueues = fcManager.getMatchData().getPlayerQueues().values();
    for (Queue<Player> queue : playerQueues) {
      if (queue != null) {
        queue.remove(player);
      }
    }

    List<Match> matches = fcManager.getMatchData().getMatches();
    if (matches != null) {
      for (Match match : matches) {
        if (match != null && match.getPhase() == MatchPhase.LOBBY) {
          List<MatchPlayer> players = match.getPlayers();
          if (players != null) {
            players.removeIf(mp -> mp == null
                                   || mp.getPlayer() == null
                                   || mp.getPlayer().equals(player));
          }
        }
        fcManager.getScoreboardManager().updateScoreboard(match);
      }
    }

    if (teamManager.isInTeam(player)) {
      Team team = teamManager.getTeam(player);
      if (team != null && team.getMembers() != null) {
        for (Player p : team.getMembers()) {
          if (p != null && p.isOnline() && !p.equals(player)) {
            logger.send(p, TEAM_DISBANDED, player.getName());
          }
        }
        teamManager.disbandTeam(team);
      }
    }

    Optional<Match> matchOpt = matchManager.getMatch(player);
    if (matchOpt.isPresent()) {
      Match match = matchOpt.get();
      MatchPlayer matchPlayer = null;
      List<MatchPlayer> players = match.getPlayers();
      if (players != null) {
        for (MatchPlayer mp : players) {
          if (mp != null && mp.getPlayer() != null
              && mp.getPlayer().equals(player)) {
            matchPlayer = mp;
            break;
          }
        }
      }

      if (matchPlayer != null) {
        int playerScore = matchPlayer.getTeamColor() == TeamColor.RED
                          ? match.getScoreRed()
                          : match.getScoreBlue();
        int opponentScore = matchPlayer.getTeamColor() == TeamColor.RED
                            ? match.getScoreBlue()
                            : match.getScoreRed();

        if (match.getPhase() == MatchPhase.IN_PROGRESS && playerScore < opponentScore) {
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
    if (matchManager.getMatch(player).isPresent()) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onItemPickup(PlayerPickupItemEvent event) {
    Player player = event.getPlayer();
    if (matchManager.getMatch(player).isPresent()) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onInventoryInteract(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) {
      return;
    }

    Player player = (Player) event.getWhoClicked();

    if (matchManager.getMatch(player).isPresent()) {
      event.setCancelled(true);
    }
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
    if (settings != null && !settings.isBuildEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (matchManager.getMatch(player).isPresent()) {
      event.setCancelled(true);
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    if (settings != null && !settings.isBuildEnabled()) {
      event.setCancelled(true);
    }
  }
}
