package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.BAN_REMAINING;
import static io.github.divinerealms.footcube.configs.Lang.NOT_BANNED;
import static io.github.divinerealms.footcube.configs.Lang.PLAYER_BANNED;
import static io.github.divinerealms.footcube.configs.Lang.PLAYER_UNBANNED;
import static io.github.divinerealms.footcube.configs.Lang.USAGE;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_BAN;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_UNBAN;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.ban.BanManager;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("fca|fcadmin|footcubeadmin")
public class FCAdminBanCommands extends BaseCommand {

  private final Logger logger;
  private final BanManager banManager;

  public FCAdminBanCommands(FCManager fcManager) {
    this.logger = fcManager.getLogger();
    this.banManager = fcManager.getBanManager();
  }

  @Subcommand("ban")
  @CommandPermission(PERM_BAN)
  @Syntax("<player> <time>")
  @CommandCompletion("@players 10s|30s|5min|10min|30min|1h")
  @Description("Ban a player from matchmaking")
  public void onBan(CommandSender sender, Player target, String timeStr) {
    try {
      int seconds = Utilities.parseTime(timeStr);
      if (seconds <= 0) {
        logger.send(sender, USAGE, "fca ban <player> <time>");
        return;
      }

      banManager.banPlayer(target, seconds * 1000L);
      logger.send(sender, PLAYER_BANNED, target.getDisplayName(),
          Utilities.formatTime(seconds));
    } catch (NumberFormatException e) {
      logger.send(sender, USAGE, "fca ban <player> <time>");
    }
  }

  @Subcommand("unban")
  @CommandPermission(PERM_UNBAN)
  @Syntax("<player>")
  @CommandCompletion("@players")
  @Description("Unban a player")
  public void onUnban(CommandSender sender, Player target) {
    banManager.unbanPlayer(target);
    logger.send(sender, PLAYER_UNBANNED, target.getDisplayName());
  }

  @Subcommand("checkban")
  @CommandPermission(PERM_BAN)
  @Syntax("<player>")
  @CommandCompletion("@players")
  @Description("Check if a player is banned")
  public void onCheckBan(CommandSender sender, Player target) {
    if (banManager.isBanned(target)) {
      long banTime = banManager.getBannedPlayers().get(target.getUniqueId());
      long secondsLeft = (banTime - System.currentTimeMillis()) / 1000L;
      logger.send(sender, BAN_REMAINING, target.getDisplayName(),
          Utilities.formatTime(secondsLeft));
    } else {
      logger.send(sender, NOT_BANNED, target.getDisplayName());
    }
  }
}