package io.github.divinerealms.footcube.commands;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Match;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.MatchHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MatchesCommand implements CommandExecutor {
  private final FCManager fcManager;
  private final Logger logger;

  public MatchesCommand(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) args = new String[]{"list"};

    String sub = args[0].toLowerCase();
    Organization org = fcManager.getOrg();

    if (sub.equals("list")) {
      List<String> output = new ArrayList<>();
      boolean firstBlock = true;

      for (String type : Arrays.asList("2v2", "3v3", "4v4")) {
        MatchHelper.ArenaData data = MatchHelper.getArenaData(org, type);
        if (data == null || data.matches == null) continue;

        for (int i = 0; i < data.matches.length; i++) {
          Match match = data.matches[i];
          if (match == null) continue;

          List<String> redPlayers = match.redPlayers.stream()
              .filter(Objects::nonNull)
              .map(Player::getName)
              .collect(Collectors.toList());

          List<String> bluePlayers = match.bluePlayers.stream()
              .filter(Objects::nonNull)
              .map(Player::getName)
              .collect(Collectors.toList());

          if (match.phase == 1 && redPlayers.isEmpty() && bluePlayers.isEmpty()) continue;

          if (!firstBlock) output.add("");
          firstBlock = false;

          String timeDisplay;
          if (match.phase == 1) {
            timeDisplay = Lang.MATCHES_LIST_WAITING.replace(null);
          } else if (match.phase == 2) {
            timeDisplay = Lang.MATCHES_LIST_STARTING.replace(new String[]{String.valueOf(match.countdown)});
          } else if (match.phase == 4) {
            timeDisplay = Lang.MATCHES_LIST_CONTINUING.replace(new String[]{String.valueOf(match.countdown)});
          } else {
            int remaining = match.time != null ? match.time.getScore() : -1;
            timeDisplay = remaining >= 0 ? remaining + "s" : "N/A";
          }

          if (match.phase == 1) {
            output.add(Lang.MATCHES_LIST_LOBBY.replace(new String[]{type, String.valueOf(i + 1)}));
            output.add(Lang.MATCHES_LIST_REDPLAYERS.replace(new String[]{redPlayers.isEmpty() ? "/" : String.join(", ", redPlayers)}));
            output.add(Lang.MATCHES_LIST_BLUEPLAYERS.replace(new String[]{bluePlayers.isEmpty() ? "/" : String.join(", ", bluePlayers)}));
            output.add(Lang.MATCHES_LIST_STATUS.replace(new String[]{timeDisplay}));
          } else {
            output.add(Lang.MATCHES_LIST_MATCH.replace(new String[]{type, String.valueOf(i + 1)}));
            output.add(Lang.MATCHES_LIST_RESULT.replace(new String[]{String.valueOf(match.scoreRed), String.valueOf(match.scoreBlue)})
                + " | " + Lang.MATCHES_LIST_TIMELEFT.replace(new String[]{timeDisplay}));
            output.add(Lang.MATCHES_LIST_REDPLAYERS.replace(new String[]{redPlayers.isEmpty() ? "/" : String.join(", ", redPlayers)}));
            output.add(Lang.MATCHES_LIST_BLUEPLAYERS.replace(new String[]{bluePlayers.isEmpty() ? "/" : String.join(", ", bluePlayers)}));
          }
        }
      }

      if (!output.isEmpty()) {
        logger.send(sender, Lang.MATCHES_LIST_HEADER.replace(null));
        output.forEach(msg -> logger.send(sender, msg));
        logger.send(sender, Lang.MATCHES_LIST_FOOTER.replace(null));
      } else {
        logger.send(sender, Lang.MATCHES_LIST_NO_MATCHES.replace(null));
      }
    } else {
      logger.send(sender, Lang.PREFIX.replace(null) + "&cUnknown subcommand. Use /matches list");
    }

    return true;
  }
}