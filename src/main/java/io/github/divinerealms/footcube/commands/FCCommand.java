package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.BANNER_PLAYER;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Subcommand;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.utils.Logger;
import java.util.List;
import java.util.StringJoiner;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

@CommandAlias("fc|footcube")
@Description("FootCube base command.")
public class FCCommand extends BaseCommand {

  private final FCManager fcManager;
  private final Logger logger;

  public FCCommand(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
  }

  @Default
  @CatchUnknown
  public void onDefault(CommandSender sender) {
    sendBanner(sender);
  }

  @Subcommand("help|h")
  @HelpCommand
  @Description("This page.")
  public void onHelp(CommandSender sender, CommandHelp help) {
    help.showHelp();
  }

  private void sendBanner(CommandSender sender) {
    Plugin plugin = fcManager.getPlugin();

    if (sender instanceof Player) {
      StringJoiner joiner = new StringJoiner(", ");
      List<String> authors = plugin.getDescription().getAuthors();
      if (authors != null) {
        for (String author : authors) {
          joiner.add(author);
        }
      }

      logger.send(sender, BANNER_PLAYER, plugin.getName(),
          plugin.getDescription().getVersion(), joiner.toString());
    } else {
      fcManager.sendBanner();
      logger.send(sender, "&aKucajte &e/fc help &aza listu dostupnih komandi.");
    }
  }
}
