package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.CLEAR_ARENAS_SUCCESS;
import static io.github.divinerealms.footcube.configs.Lang.CLEAR_ARENAS_TYPE_SUCCESS;
import static io.github.divinerealms.footcube.configs.Lang.PRACTICE_AREA_SET;
import static io.github.divinerealms.footcube.configs.Lang.PREFIX_ADMIN;
import static io.github.divinerealms.footcube.configs.Lang.SETUP_ARENA_FIRST_SET;
import static io.github.divinerealms.footcube.configs.Lang.SETUP_ARENA_START;
import static io.github.divinerealms.footcube.configs.Lang.SETUP_ARENA_SUCCESS;
import static io.github.divinerealms.footcube.configs.Lang.SET_BLOCK_SUCCESS;
import static io.github.divinerealms.footcube.configs.Lang.SET_BLOCK_TOO_FAR;
import static io.github.divinerealms.footcube.configs.Lang.UNDO;
import static io.github.divinerealms.footcube.configs.Lang.USAGE;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.FOUR_V_FOUR;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.THREE_V_THREE;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.TWO_V_TWO;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_CLEAR_ARENAS;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_SETBUTON;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_SETUP_ARENA;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_SET_LOBBY;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_SET_PRACTICE_AREA;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.matchmaking.arena.ArenaManager;
import io.github.divinerealms.footcube.utils.Logger;
import java.util.Set;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.material.Button;
import org.bukkit.material.Wool;

@CommandAlias("fca|fcadmin|footcubeadmin")
public class FCAdminArenaCommands extends BaseCommand {

  private final Logger logger;
  private final ArenaManager arenaManager;
  private final ConfigManager configManager;
  private final FileConfiguration config;
  private final FileConfiguration practice;

  public FCAdminArenaCommands(FCManager fcManager) {
    this.logger = fcManager.getLogger();
    this.arenaManager = fcManager.getArenaManager();
    this.configManager = fcManager.getConfigManager();
    this.config = configManager.getConfig("config.yml");
    this.practice = configManager.getConfig("practice.yml");
  }

  @Subcommand("setuparena")
  @CommandPermission(PERM_SETUP_ARENA)
  @Syntax("<2v2|3v3|4v4>")
  @CommandCompletion("2v2|3v3|4v4")
  @Description("Start arena setup wizard")
  public void onSetupArena(Player player, String type) {
    int arenaType;
    switch (type.toLowerCase()) {
      case "2v2":
        arenaType = TWO_V_TWO;
        break;
      case "3v3":
        arenaType = THREE_V_THREE;
        break;
      case "4v4":
        arenaType = FOUR_V_FOUR;
        break;
      default:
        logger.send(player, "&cInvalid type. Use 2v2, 3v3, or 4v4.");
        return;
    }

    arenaManager.getSetupWizards().put(player, new ArenaManager.ArenaSetup(arenaType));
    logger.send(player, SETUP_ARENA_START);
  }

  @Subcommand("set")
  @CommandPermission(PERM_SETUP_ARENA)
  @Description("Set arena spawn point (use twice)")
  public void onSet(Player player) {
    ArenaManager.ArenaSetup setup = arenaManager.getSetupWizards().get(player);
    if (setup == null) {
      logger.send(player, PREFIX_ADMIN + "You are not setting up an arena.");
      return;
    }

    if (setup.getBlueSpawn() == null) {
      setup.setBlueSpawn(player.getLocation());
      logger.send(player, SETUP_ARENA_FIRST_SET);
    } else {
      arenaManager.createArena(setup.getType(), setup.getBlueSpawn(), player.getLocation());
      arenaManager.getSetupWizards().remove(player);
      logger.send(player, SETUP_ARENA_SUCCESS);
    }
  }

  @Subcommand("undo")
  @CommandPermission(PERM_SETUP_ARENA)
  @Description("Cancel arena setup")
  public void onUndo(Player player) {
    if (arenaManager.getSetupWizards().remove(player) != null) {
      logger.send(player, UNDO);
    } else {
      logger.send(player, PREFIX_ADMIN + "You are not setting up an arena.");
    }
  }

  @Subcommand("clear arenas")
  @CommandPermission(PERM_CLEAR_ARENAS)
  @Description("Clear all arenas")
  public void onClearArenas(Player player) {
    arenaManager.clearArenas();
    logger.send(player, CLEAR_ARENAS_SUCCESS);
  }

  @Subcommand("clear arenas 2v2")
  @CommandPermission(PERM_CLEAR_ARENAS)
  @Description("Clear all 2v2 arenas")
  public void onClearArenas2v2(Player player) {
    arenaManager.clearArenaType(2);
    logger.send(player, CLEAR_ARENAS_TYPE_SUCCESS, "2v2");
  }

  @Subcommand("clear arenas 3v3")
  @CommandPermission(PERM_CLEAR_ARENAS)
  @Description("Clear all 3v3 arenas")
  public void onClearArenas3v3(Player player) {
    arenaManager.clearArenaType(3);
    logger.send(player, CLEAR_ARENAS_TYPE_SUCCESS, "3v3");
  }

  @Subcommand("clear arenas 4v4")
  @CommandPermission(PERM_CLEAR_ARENAS)
  @Description("Clear all 4v4 arenas")
  public void onClearArenas4v4(Player player) {
    arenaManager.clearArenaType(4);
    logger.send(player, CLEAR_ARENAS_TYPE_SUCCESS, "4v4");
  }

  @Subcommand("setlobby")
  @CommandPermission(PERM_SET_LOBBY)
  @Description("Set lobby spawn location")
  public void onSetLobby(Player player) {
    config.set("lobby", player.getLocation());
    configManager.saveConfig("config.yml");
    logger.send(player, PRACTICE_AREA_SET, "lobby",
        String.valueOf(player.getLocation().getX()),
        String.valueOf(player.getLocation().getY()),
        String.valueOf(player.getLocation().getZ())
    );
  }

  @Subcommand("setpracticearea|spa")
  @CommandPermission(PERM_SET_PRACTICE_AREA)
  @Syntax("<name>")
  @Description("Set practice area location")
  public void onSetPracticeArea(Player player, String name) {
    practice.set("practice-areas." + name, player.getLocation());
    configManager.saveConfig("practice.yml");
    logger.send(player, PRACTICE_AREA_SET, name,
        String.valueOf(player.getLocation().getX()),
        String.valueOf(player.getLocation().getY()),
        String.valueOf(player.getLocation().getZ())
    );
  }

  @Subcommand("setbutton|sb")
  @CommandPermission(PERM_SETBUTON)
  @Syntax("<spawn|clearcube>")
  @CommandCompletion("spawn|clearcube")
  @Description("Set button for cube spawn or clear")
  public void onSetButton(Player player, String buttonType) {
    Block targetBlock = player.getTargetBlock((Set<Material>) null, 5);
    if (targetBlock == null || targetBlock.getType() == Material.AIR) {
      logger.send(player, SET_BLOCK_TOO_FAR);
      return;
    }

    targetBlock.setType(Material.WOOL);
    BlockState targetBlockState = targetBlock.getState();

    switch (buttonType.toLowerCase()) {
      case "spawn":
        targetBlockState.setData(new Wool(DyeColor.LIME));
        break;
      case "clearcube":
        targetBlockState.setData(new Wool(DyeColor.RED));
        break;
      default:
        logger.send(player, USAGE, "fca setbutton <spawn|clearcube>");

        return;
    }

    targetBlockState.update(true);

    Block aboveTargetBlock = targetBlock.getRelative(BlockFace.UP);
    aboveTargetBlock.setType(Material.STONE_BUTTON);
    BlockState aboveTargetBlockState = aboveTargetBlock.getState();
    Button buttonData = new Button();
    buttonData.setFacingDirection(BlockFace.UP);
    aboveTargetBlockState.setData(buttonData);
    aboveTargetBlockState.update(true);

    logger.send(player, SET_BLOCK_SUCCESS, buttonType);
  }
}