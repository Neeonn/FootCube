package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.util.MatchUtils;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Wool;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static io.github.divinerealms.footcube.configs.Lang.*;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.*;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_PLAY;

public class SignManipulation implements Listener {
  private final FCManager fcManager;
  private final Logger logger;
  private final MatchManager matchManager;
  private final PhysicsData data;
  private final PhysicsSystem system;

  public SignManipulation(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.matchManager = fcManager.getMatchManager();
    this.data = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
  }

  @EventHandler
  public void onSignChange(SignChangeEvent event) {
    Player player = event.getPlayer();

    if (event.getLine(0) != null && event.getLine(0).equalsIgnoreCase("[FootCube]")
        && !player.hasPermission("footcube.admin")) return;

    if (event.getLine(0) != null && event.getLine(0).equalsIgnoreCase("[fc]")) {
      switch (event.getLine(1).toLowerCase()) {
        case "join":
          String arena = event.getLine(2).toLowerCase();
          event.setLine(0, "[FootCube]");
          event.setLine(1, ChatColor.AQUA + "join");

          switch (arena) {
            case "2v2":
              event.setLine(2, ChatColor.GREEN + "2v2");
              break;

            case "3v3":
              event.setLine(2, ChatColor.GREEN + "3v3");
              break;

            case "4v4":
              event.setLine(2, ChatColor.GREEN + "4v4");
              break;
          }

          event.setLine(3, "");
          break;

        case "stats":
          event.setLine(0, "[FootCube]");
          event.setLine(1, ChatColor.AQUA + "stats");
          event.setLine(2, "See how much");
          event.setLine(3, "you score & win");
          break;

        case "cube":
          event.setLine(0, "[FootCube]");
          event.setLine(1, ChatColor.AQUA + "cube");
          event.setLine(2, "Spawn a");
          event.setLine(3, "cube");
          break;

        case "balance":
        case "money":
          event.setLine(0, "[FootCube]");
          event.setLine(1, ChatColor.AQUA + "balance");
          event.setLine(2, "Check your");
          event.setLine(3, "balance");
          break;

        case "highscores":
        case "best":
          event.setLine(0, "[FootCube]");
          event.setLine(1, ChatColor.AQUA + "highscores");
          event.setLine(2, "Check all");
          event.setLine(3, "highscores");
          break;

        case "matches":
          event.setLine(0, "[FootCube]");
          event.setLine(1, ChatColor.AQUA + "matches");
          event.setLine(2, "See currently");
          event.setLine(3, "active matches");
          break;
      }
    }
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    Action action = event.getAction();

    if (action != Action.RIGHT_CLICK_BLOCK) return;

    Block block = event.getClickedBlock();
    if (block == null) return;

    if (block.getType() == Material.STONE_BUTTON) {
      Block below = block.getRelative(BlockFace.DOWN);
      if (below == null || below.getType() != Material.WOOL) return;

      MaterialData materialData = below.getState().getData();
      if (!(materialData instanceof Wool)) return;

      DyeColor color = ((Wool) materialData).getColor();
      Location playerLocation = player.getLocation();
      switch (color) {
        case LIME:
          if (system.cantSpawnYet(player)) return;
          Collection<Entity> nearbyEntities = playerLocation.getWorld().getNearbyEntities(playerLocation, 100, 100, 100);
          int slimeCount = 0;
          if (nearbyEntities != null) {
            for (Entity entity : nearbyEntities) {
              if (entity instanceof Slime) slimeCount++;
            }
          }

          if (slimeCount < 10) {
            system.spawnCube(playerLocation.add(new Vector(0.5, 0.5, 0.5)));
            system.setButtonCooldown(player);
            logger.send(player, CUBE_SPAWN);
          } else logger.send(player, ALREADY_ENOUGH_CUBES);
          break;

        case RED:
          double closestDistance = 30;
          int removed = 0;
          for (Slime cube : new HashSet<>(data.getCubes())) {
            if (cube == null || cube.isDead()) continue;

            if (cube.getLocation().distance(playerLocation) <= closestDistance) {
              cube.setHealth(0);
              removed++;
            }
          }

          if (removed > 0) logger.send(player, CLEARED_CUBES, String.valueOf(removed));
          else logger.send(player, CUBE_NO_CUBES);
          break;

        default: break;
      }
    }

    if (block.getType() == Material.SIGN_POST || block.getType() == Material.WALL_SIGN) {
      Sign sign = (Sign) block.getState();
      if (sign.getLine(0) == null || !sign.getLine(0).equalsIgnoreCase("[FootCube]")) return;

      String line1 = ChatColor.stripColor(sign.getLine(1));
      switch (line1.toLowerCase()) {
        case "join":
          if (!player.hasPermission(PERM_PLAY)) { logger.send(player, NO_PERM, PERM_PLAY, "fc join"); return; }
          if (!matchManager.getData().isMatchesEnabled()) { logger.send(player, FC_DISABLED); return; }
          if (matchManager.getBanManager().isBanned(player)) return;
          if (matchManager.getBanManager().isBanned(player)) return;
          if (matchManager.getMatch(player).isPresent()) { logger.send(player, JOIN_ALREADYINGAME); return; }

          String arenaType = ChatColor.stripColor(sign.getLine(2)).toLowerCase();
          int type;
          switch (arenaType) {
            case "2v2": type = TWO_V_TWO; break;
            case "3v3": type = THREE_V_THREE; break;
            case "4v4": type = FOUR_V_FOUR; break;
            default: return;
          }

          matchManager.joinQueue(player, type);
          break;

        case "stats":
          fcManager.getMatchSystem().checkStats(player.getName(), player);
          break;

        case "cube":
          if (system.cantSpawnYet(player)) return;
          Location location = player.getLocation();
          Collection<Entity> nearbyEntities = location.getWorld().getNearbyEntities(location, 100, 100, 100);
          int slimeCount = 0;
          if (nearbyEntities != null) {
            for (Entity entity : nearbyEntities) {
              if (entity instanceof Slime) slimeCount++;
            }
          }

          if (slimeCount < 10) {
            system.spawnCube(player.getLocation().add(new Vector(0.5, 0.5, 0.5)));
            system.setButtonCooldown(player);
            logger.send(player, CUBE_SPAWN);
          } else logger.send(player, ALREADY_ENOUGH_CUBES);
          break;

        case "balance":
          logger.send(player, BALANCE, String.valueOf(fcManager.getEconomy().getBalance(player)));
          break;

        case "highscores":
          fcManager.getHighscoreManager().showHighScores(player);
          break;

        case "matches":
          List<String> matches = MatchUtils.getFormattedMatches(matchManager.getData().getMatches());
          if (!matches.isEmpty()) {
            logger.send(player, MATCHES_LIST_HEADER);
            matches.forEach(msg -> logger.send(player, msg));
            logger.send(player, MATCHES_LIST_FOOTER);
          } else logger.send(player, MATCHES_LIST_NO_MATCHES);
          break;
      }
    }
  }
}
