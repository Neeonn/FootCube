package io.github.divinerealms.footcube.commands;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.util.MatchUtils;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

import static io.github.divinerealms.footcube.configs.Lang.*;

public class MatchesCommand implements CommandExecutor {
  private final FCManager fcManager;
  private final Logger logger;
  private final PhysicsSystem system;

  public MatchesCommand(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.system = fcManager.getPhysicsSystem();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    MatchManager matchManager = fcManager.getMatchManager();
    List<String> output = MatchUtils.getFormattedMatches(matchManager.getData().getMatches());

    if (!output.isEmpty()) {
      logger.send(sender, MATCHES_LIST_HEADER);
      output.forEach(msg -> logger.send(sender, msg));
      logger.send(sender, MATCHES_LIST_FOOTER);
    } else {
      logger.send(sender, MATCHES_LIST_NO_MATCHES);
    }

    if (sender instanceof Player) {
      system.recordPlayerAction((Player) sender);
    }
    return true;
  }
}
