package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.ADMIN_STATSET;
import static io.github.divinerealms.footcube.configs.Lang.CLEAR_STATS_SUCCESS;
import static io.github.divinerealms.footcube.configs.Lang.FORCE_LEAVE;
import static io.github.divinerealms.footcube.configs.Lang.PLAYER_NOT_FOUND;
import static io.github.divinerealms.footcube.configs.Lang.PREFIX_ADMIN;
import static io.github.divinerealms.footcube.configs.Lang.STATSSET_IS_NOT_A_NUMBER;
import static io.github.divinerealms.footcube.configs.Lang.STATSSET_NOT_A_STAT;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_ADMIN;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_CLEAR_STATS;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_FORCE_LEAVE;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_STAT_SET;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import io.github.divinerealms.footcube.FootCube;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.utils.Logger;
import java.util.Arrays;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("fca|fcadmin|footcubeadmin")
public class FCAdminPlayerCommands extends BaseCommand {

  private final FCManager fcManager;
  private final FootCube plugin;
  private final Logger logger;
  private final MatchManager matchManager;
  private final PlayerDataManager dataManager;

  public FCAdminPlayerCommands(FCManager fcManager) {
    this.fcManager = fcManager;
    this.plugin = fcManager.getPlugin();
    this.logger = fcManager.getLogger();
    this.matchManager = fcManager.getMatchManager();
    this.dataManager = fcManager.getDataManager();
  }

  @Subcommand("statset")
  @CommandPermission(PERM_STAT_SET)
  @Syntax("<player> <stat> <amount|clear>")
  @CommandCompletion("@players wins|matches|ties|goals|assists|owngoals|winstreak|bestwinstreak|all")
  @Description("Set player statistics")
  public void onStatSet(CommandSender sender, Player target, String stat, String amountStr) {
    PlayerData playerData = dataManager.get(target);
    if (playerData == null) {
      logger.send(sender, PLAYER_NOT_FOUND);
      return;
    }

    boolean clear = amountStr.equalsIgnoreCase("clear");
    int amount = 0;

    if (!clear) {
      try {
        amount = Integer.parseInt(amountStr);
      } catch (NumberFormatException e) {
        logger.send(sender, STATSSET_IS_NOT_A_NUMBER, amountStr);
        return;
      }
    }

    String statLower = stat.toLowerCase();
    String[] validStats = {"wins", "matches", "ties", "goals", "assists",
        "owngoals", "winstreak", "bestwinstreak"};

    if (Arrays.asList(validStats).contains(statLower)) {
      playerData.set(statLower, clear ? 0 : amount);
    } else if (statLower.equals("all")) {
      int finalAmount = clear ? 0 : amount;
      Arrays.asList(validStats).forEach(s -> playerData.set(s, finalAmount));
    } else {
      logger.send(sender, STATSSET_NOT_A_STAT, stat);
      return;
    }

    dataManager.savePlayerData(target.getName());
    logger.send(sender, ADMIN_STATSET, stat, target.getName(), String.valueOf(amount));
  }

  @CommandAlias("forceleave|fl")
  @Subcommand("forceleave|fl")
  @CommandPermission(PERM_FORCE_LEAVE)
  @Syntax("<player>")
  @CommandCompletion("@players")
  @Description("Force a player to leave their match")
  public void onForceLeave(CommandSender sender, Player target) {
    matchManager.leaveMatch(target);
    logger.send(sender, FORCE_LEAVE, target.getDisplayName());
  }

  @CommandAlias("refreshprefix|rp")
  @Subcommand("refreshprefix|rp")
  @CommandPermission(PERM_ADMIN)
  @Syntax("<player>")
  @CommandCompletion("@players")
  @Description("Refresh player's prefix cache")
  public void onRefreshPrefix(CommandSender sender, Player target) {
    fcManager.cachePrefixedName(target);
    logger.send(sender, PREFIX_ADMIN + "Refreshing prefix for " + target.getName() + "...");

    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      String refreshed = fcManager.getPrefixedName(target.getUniqueId());
      logger.send(sender, PREFIX_ADMIN + "Refreshed: " + refreshed);
    }, 20L);
  }

  @Subcommand("clear stats")
  @CommandPermission(PERM_CLEAR_STATS)
  @Description("Clear all player statistics")
  public void onClearStats(CommandSender sender) {
    dataManager.clearAllStats();
    logger.send(sender, CLEAR_STATS_SUCCESS);
  }
}