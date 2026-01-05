package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_FOOTER;
import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_HEADER;
import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_NO_MATCHES;
import static io.github.divinerealms.footcube.matchmaking.util.MatchUtils.getFormattedMatches;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.utils.Logger;
import java.util.List;
import org.bukkit.command.CommandSender;

@CommandAlias("matches|queues|q")
public class FCMatchesCommand extends BaseCommand {

  private final Logger logger;
  private final MatchManager matchManager;

  public FCMatchesCommand(FCManager fcManager) {
    this.logger = fcManager.getLogger();
    this.matchManager = fcManager.getMatchManager();
  }

  @Default
  @CatchUnknown
  @Description("View all active matches")
  public void onMatches(CommandSender sender) {
    List<String> output = getFormattedMatches(matchManager.getData().getMatches());

    if (!output.isEmpty()) {
      logger.send(sender, MATCHES_LIST_HEADER);
      output.forEach(msg -> logger.send(sender, msg));
      logger.send(sender, MATCHES_LIST_FOOTER);
    } else {
      logger.send(sender, MATCHES_LIST_NO_MATCHES);
    }
  }
}