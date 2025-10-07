package io.github.divinerealms.footcube.commands;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class MatchesCommand implements CommandExecutor {
  private final FCManager fcManager;
  private final Logger logger;

  public MatchesCommand(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    Organization org = fcManager.getOrg();
    List<String> output = org.getMatches();

    if (!output.isEmpty()) {
      logger.send(sender, Lang.MATCHES_LIST_HEADER.replace(null));
      output.forEach(msg -> logger.send(sender, msg));
      logger.send(sender, Lang.MATCHES_LIST_FOOTER.replace(null));
    } else {
      logger.send(sender, Lang.MATCHES_LIST_NO_MATCHES.replace(null));
    }

    return true;
  }
}