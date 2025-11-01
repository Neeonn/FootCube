package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.core.Physics;
import io.github.divinerealms.footcube.core.PhysicsUtil;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
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

public class SignManipulation implements Listener {
  private final FCManager fcManager;
  private final Logger logger;
  private final Organization org;
  private final Physics physics;
  private final PhysicsUtil physicsUtil;
  private final FileConfiguration arenas;

  private static final String PERM_PLAY = "footcube.play";

  public SignManipulation(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.org = fcManager.getOrg();
    this.physics = fcManager.getPhysics();
    this.physicsUtil = fcManager.getPhysicsUtil();
    ConfigManager configManager = fcManager.getConfigManager();
    this.arenas = configManager.getConfig("arenas.yml");
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

      MaterialData data = below.getState().getData();
      if (!(data instanceof Wool)) return;

      DyeColor color = ((Wool) data).getColor();
      Location playerLocation = player.getLocation();
      switch (color) {
        case LIME:
          if (physicsUtil.cantSpawnYet(player)) return;
          Collection<Entity> nearbyEntities = playerLocation.getWorld().getNearbyEntities(playerLocation, 100, 100, 100);
          if (nearbyEntities.stream().filter(entity -> entity instanceof Slime).count() < 10) {
            physicsUtil.spawnCube(playerLocation.add(new Vector(0.5, 0.5, 0.5)));
            physicsUtil.setButtonCooldown(player);
            logger.send(player, Lang.CUBE_SPAWN.replace(null));
          } else logger.send(player, Lang.ALREADY_ENOUGH_CUBES.replace(null));
          break;

        case RED:
          double closestDistance = 30;
          int removed = 0;
          for (Slime cube : new HashSet<>(physics.getCubes())) {
            if (cube == null || cube.isDead()) continue;

            if (cube.getLocation().distance(playerLocation) <= closestDistance) {
              cube.setHealth(0);
              removed++;
            }
          }

          if (removed > 0) logger.send(player, Lang.CLEARED_CUBES.replace(new String[]{String.valueOf(removed)}));
          else logger.send(player, Lang.CUBE_NO_CUBES.replace(null));
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
          if (!player.hasPermission(PERM_PLAY)) {
            logger.send(player, Lang.NO_PERM.replace(new String[]{PERM_PLAY, "fc join"}));
            return;
          }
          if (org.isBanned(player)) return;
          if (org.isInGame(player)) {
            logger.send(player, Lang.JOIN_INTEAM.replace(null));
            return;
          }
          if (!physics.isMatchesEnabled()) {
            logger.send(player, Lang.FC_DISABLED.replace(null));
            return;
          }

          String arenaType = ChatColor.stripColor(sign.getLine(2)).toLowerCase();

          switch (arenaType) {
            case "2v2":
              if (arenas.getInt("arenas.2v2.amount") != 0) {
                org.getMatches2v2()[org.getLobby2v2()].join(player, false);
                org.getWaitingPlayers().put(player.getName(), 2);
                org.removeTeam(player);
              } else logger.send(player, Lang.JOIN_NOARENA.replace(null));
              break;
            case "3v3":
              if (arenas.getInt("arenas.3v3.amount") != 0) {
                org.getMatches3v3()[org.getLobby3v3()].join(player, false);
                org.getWaitingPlayers().put(player.getName(), 3);
                org.removeTeam(player);
              } else logger.send(player, Lang.JOIN_NOARENA.replace(null));
              break;
            case "4v4":
              if (arenas.getInt("arenas.4v4.amount") != 0) {
                org.getMatches4v4()[org.getLobby4v4()].join(player, false);
                org.getWaitingPlayers().put(player.getName(), 4);
                org.removeTeam(player);
              } else logger.send(player, Lang.JOIN_NOARENA.replace(null));
              break;
          }
          break;

        case "stats":
          org.checkStats(player.getName(), player);
          break;

        case "cube":
          if (physicsUtil.cantSpawnYet(player)) return;
          Location location = player.getLocation();
          Collection<Entity> nearbyEntities = location.getWorld().getNearbyEntities(location, 100, 100, 100);
          if (nearbyEntities.stream().filter(entity -> entity instanceof Slime).count() < 10) {
            physicsUtil.spawnCube(player.getLocation().add(new Vector(0.5, 0.5, 0.5)));
            physicsUtil.setButtonCooldown(player);
            logger.send(player, Lang.CUBE_SPAWN.replace(null));
          } else logger.send(player, Lang.ALREADY_ENOUGH_CUBES.replace(null));
          break;

        case "balance":
          logger.send(player, Lang.BALANCE.replace(new String[]{String.valueOf(fcManager.getEconomy().getBalance(player))}));
          break;

        case "highscores":
          org.updateHighScores(player);
          break;

        case "matches":
          List<String> matches = org.getMatches();

          if (!matches.isEmpty()) {
            logger.send(player, Lang.MATCHES_LIST_HEADER.replace(null));
            matches.forEach(msg -> logger.send(player, msg));
            logger.send(player, Lang.MATCHES_LIST_FOOTER.replace(null));
          } else logger.send(player, Lang.MATCHES_LIST_NO_MATCHES.replace(null));
          break;
      }
    }
  }
}
