package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.COMMAND_DISABLER_ALREADY_ADDED;
import static io.github.divinerealms.footcube.configs.Lang.COMMAND_DISABLER_LIST;
import static io.github.divinerealms.footcube.configs.Lang.COMMAND_DISABLER_SUCCESS;
import static io.github.divinerealms.footcube.configs.Lang.COMMAND_DISABLER_SUCCESS_REMOVE;
import static io.github.divinerealms.footcube.configs.Lang.COMMAND_DISABLER_WASNT_ADDED;
import static io.github.divinerealms.footcube.configs.Lang.OFF;
import static io.github.divinerealms.footcube.configs.Lang.ON;
import static io.github.divinerealms.footcube.configs.Lang.TOGGLES_HIT_DEBUG;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_COMMAND_DISABLER;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_HIT_DEBUG;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.utils.DisableCommands;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.command.CommandSender;

@CommandAlias("fca|fcadmin|footcubeadmin")
public class FCAdminDebugCommands extends BaseCommand {

  private final Logger logger;
  private final PhysicsData physicsData;
  private final DisableCommands disableCommands;

  public FCAdminDebugCommands(FCManager fcManager) {
    this.logger = fcManager.getLogger();
    this.physicsData = fcManager.getPhysicsData();
    this.disableCommands = fcManager.getDisableCommands();
  }

  @Subcommand("hitsdebug|hits")
  @CommandPermission(PERM_HIT_DEBUG)
  @Description("Toggle global hit debug visualization")
  public void onHitsDebug(CommandSender sender) {
    boolean status = physicsData.isHitDebugEnabled();
    physicsData.hitDebugEnabled = !status;
    logger.send(sender, TOGGLES_HIT_DEBUG, status ? OFF.toString() : ON.toString());
  }

  @Subcommand("commanddisabler add|cd add")
  @CommandPermission(PERM_COMMAND_DISABLER)
  @Syntax("<command>")
  @Description("Add command to blacklist during matches")
  public void onCommandDisablerAdd(CommandSender sender, String command) {
    if (disableCommands.addCommand(command)) {
      logger.send(sender, COMMAND_DISABLER_SUCCESS, command);
    } else {
      logger.send(sender, COMMAND_DISABLER_ALREADY_ADDED);
    }
  }

  @Subcommand("commanddisabler remove|cd remove")
  @CommandPermission(PERM_COMMAND_DISABLER)
  @Syntax("<command>")
  @Description("Remove command from blacklist")
  public void onCommandDisablerRemove(CommandSender sender, String command) {
    if (disableCommands.removeCommand(command)) {
      logger.send(sender, COMMAND_DISABLER_SUCCESS_REMOVE, command);
    } else {
      logger.send(sender, COMMAND_DISABLER_WASNT_ADDED);
    }
  }

  @Subcommand("commanddisabler list|cd list")
  @CommandPermission(PERM_COMMAND_DISABLER)
  @Description("List all blacklisted commands")
  public void onCommandDisablerList(CommandSender sender) {
    logger.send(sender, COMMAND_DISABLER_LIST);
    disableCommands.getCommands().forEach(c -> logger.send(sender, "&7" + c));
  }
}