package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.MATCHES_LIST_NO_MATCHES;
import static io.github.divinerealms.footcube.configs.Lang.MATCHMAN_FORCE_END;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_MATCHMAN;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.utils.Logger;
import java.util.Optional;
import org.bukkit.entity.Player;

@CommandAlias("matchman|mm")
@CommandPermission(PERM_MATCHMAN)
@Description("Manipulate FootCube matches")
public class MatchManCommands extends BaseCommand {

  private final Logger logger;
  private final MatchManager matchManager;

  public MatchManCommands(FCManager fcManager) {
    this.logger = fcManager.getLogger();
    this.matchManager = fcManager.getMatchManager();
  }

  @Subcommand("start")
  @Description("Force start current match")
  public void onMatchStart(Player player) {
    matchManager.forceStartMatch(player);
  }

  @Subcommand("end")
  @Description("Force end current match")
  public void onMatchEnd(Player player) {
    Optional<Match> matchOptional = matchManager.getMatch(player);

    if (matchOptional.isPresent()) {
      Match match = matchOptional.get();

      if (match.getPhase() == MatchPhase.LOBBY || match.getPhase() == MatchPhase.STARTING) {
        matchManager.endMatch(match);
      } else {
        match.setPhase(MatchPhase.ENDED);
      }

      logger.send(player, MATCHMAN_FORCE_END,
          match.getArena().getType() + "v" + match.getArena().getType());
    } else {
      logger.send(player, MATCHES_LIST_NO_MATCHES);
    }
  }
}