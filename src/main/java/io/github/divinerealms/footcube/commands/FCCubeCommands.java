package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.COMMAND_DISABLER_CANT_USE;
import static io.github.divinerealms.footcube.configs.Lang.CUBE_CLEAR;
import static io.github.divinerealms.footcube.configs.Lang.CUBE_CLEAR_ALL;
import static io.github.divinerealms.footcube.configs.Lang.CUBE_NO_CUBES;
import static io.github.divinerealms.footcube.configs.Lang.CUBE_SPAWN;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_CLEAR_CUBE;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_CUBE;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

@CommandAlias("fc|footcube")
public class FCCubeCommands extends BaseCommand {

  private final Logger logger;
  private final MatchManager matchManager;
  private final PhysicsData physicsData;
  private final PhysicsSystem system;

  public FCCubeCommands(FCManager fcManager) {
    this.logger = fcManager.getLogger();
    this.matchManager = fcManager.getMatchManager();
    this.physicsData = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
  }

  @Subcommand("cube")
  @CommandPermission(PERM_CUBE)
  @Description("Spawn a FootCube ball")
  @CommandAlias("cube")
  public void onCube(Player player) {
    if (player.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
      logger.send(player, "{prefix-admin}&cDifficulty ne sme biti na peaceful.");
      return;
    }

    if (matchManager.getMatch(player).isPresent()) {
      logger.send(player, COMMAND_DISABLER_CANT_USE);
      return;
    }

    if (system.cantSpawnYet(player)) {
      return;
    }

    Location loc = player.getLocation();
    Vector dir = loc.getDirection().normalize();
    Location spawnLoc;

    if (player.getGameMode() == GameMode.CREATIVE || player.isFlying()) {
      spawnLoc = getCreativeSpawnLocation(loc);
    } else {
      spawnLoc = getSurvivalSpawnLocation(loc, dir);
    }

    system.spawnCube(spawnLoc);
    system.setButtonCooldown(player);
    logger.send(player, CUBE_SPAWN);
  }

  @Subcommand("clearcube")
  @CommandPermission(PERM_CLEAR_CUBE)
  @Description("Remove the nearest cube")
  @CommandAlias("clearcube")
  public void onClearCube(Player player) {
    if (matchManager.getMatch(player).isPresent()) {
      logger.send(player, COMMAND_DISABLER_CANT_USE);
      return;
    }

    double closestDistance = 200;
    Slime closest = null;
    for (Slime cube : physicsData.getCubes()) {
      double distance = cube.getLocation().distance(player.getLocation());
      if (distance < closestDistance) {
        closestDistance = distance;
        closest = cube;
      }
    }

    if (closest != null) {
      closest.setHealth(0);
      logger.send(player, CUBE_CLEAR);
    } else {
      logger.send(player, CUBE_NO_CUBES);
    }
  }

  @Subcommand("clearcubes")
  @CommandPermission(PERM_CLEAR_CUBE)
  @Description("Remove all cubes")
  @CommandAlias("clearcubes")
  public void onClearCubes(Player player) {
    if (matchManager.getMatch(player).isPresent()) {
      logger.send(player, COMMAND_DISABLER_CANT_USE);
      return;
    }

    int count = 0;
    for (Slime cube : physicsData.getCubes()) {
      cube.setHealth(0);
      count++;
    }

    logger.send(player, CUBE_CLEAR_ALL, String.valueOf(count));
  }

  private Location getCreativeSpawnLocation(Location loc) {
    Location down = loc.clone();
    while (down.getY() > 0 && down.getBlock().getType() == Material.AIR) {
      down.subtract(0, 1, 0);
    }
    double x = Math.floor(down.getX()) + 0.5;
    double y = down.getBlockY() + 1.5;
    double z = Math.floor(down.getZ()) + 0.5;
    return new Location(down.getWorld(), x, y, z);
  }

  private Location getSurvivalSpawnLocation(Location loc, Vector dir) {
    Location spawnLoc = loc.clone().add(dir.multiply(2));
    spawnLoc.setY(loc.getY() + 2.5);
    return spawnLoc;
  }
}