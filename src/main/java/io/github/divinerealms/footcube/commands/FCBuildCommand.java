package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.COMMAND_DISABLER_CANT_USE;
import static io.github.divinerealms.footcube.configs.Lang.OFF;
import static io.github.divinerealms.footcube.configs.Lang.ON;
import static io.github.divinerealms.footcube.configs.Lang.SET_BUILD_MODE;
import static io.github.divinerealms.footcube.configs.Lang.SET_BUILD_MODE_OTHER;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ALREADY_IN_GAME;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_BUILD;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_BUILD_OTHER;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("build")
public class FCBuildCommand extends BaseCommand {

  private final FCManager fcManager;
  private final Logger logger;
  private final MatchManager matchManager;

  public FCBuildCommand(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.matchManager = fcManager.getMatchManager();
  }

  @Default
  @CommandPermission(PERM_BUILD)
  @Description("Toggle your build mode")
  public void onBuild(Player player) {
    if (matchManager.getMatch(player).isPresent()) {
      logger.send(player, COMMAND_DISABLER_CANT_USE);
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    settings.toggleBuild();
    logger.send(player, SET_BUILD_MODE, settings.isBuildEnabled()
        ? ON.toString()
        : OFF.toString());
  }

  @Default
  @CommandPermission(PERM_BUILD_OTHER)
  @CommandCompletion("@players")
  @Syntax("<player>")
  @Description("Toggle build mode for another player")
  public void onBuildOther(CommandSender sender, Player target) {
    if (matchManager.getMatch(target).isPresent()) {
      logger.send(sender, TEAM_ALREADY_IN_GAME, target.getDisplayName());
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(target);
    settings.toggleBuild();
    String status = settings.isBuildEnabled()
        ? ON.toString()
        : OFF.toString();

    logger.send(target, SET_BUILD_MODE, status);
    logger.send(sender, SET_BUILD_MODE_OTHER, target.getDisplayName(), status);
  }
}