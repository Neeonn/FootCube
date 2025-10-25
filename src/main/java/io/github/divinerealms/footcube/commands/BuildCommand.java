package io.github.divinerealms.footcube.commands;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Organization;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BuildCommand implements CommandExecutor, TabCompleter {
  private final FCManager fcManager;
  private final Organization org;
  private final Logger logger;

  private static final String PERM_BUILD = "footcube.build";
  private static final String PERM_BUILD_OTHER = PERM_BUILD + ".other";

  public BuildCommand(FCManager fcManager) {
    this.fcManager = fcManager;
    this.org = fcManager.getOrg();
    this.logger = fcManager.getLogger();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      if (!(sender instanceof Player)) { logger.send(sender, Lang.INGAME_ONLY.replace(null)); return true; }
      if (!sender.hasPermission(PERM_BUILD)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_BUILD, label})); return true; }

      Player player = (Player) sender;
      PlayerSettings settings = fcManager.getPlayerSettings(player);

      if (org.isInGame(player)) { logger.send(sender, Lang.COMMAND_DISABLER_CANT_USE.replace(null)); return true; }

      settings.toggleBuild();
      String status = settings.isBuildEnabled() ? Lang.ON.replace(null) : Lang.OFF.replace(null);
      logger.send(player, Lang.SET_BUILD_MODE.replace(new String[]{status}));
      return true;
    }

    if (!sender.hasPermission(PERM_BUILD_OTHER)) { logger.send(sender, Lang.NO_PERM.replace(new String[]{PERM_BUILD, label + " <player>"})); return true; }

    Player target = Bukkit.getPlayerExact(args[0]);
    if (target == null) { logger.send(sender, Lang.PLAYER_NOT_FOUND.replace(null)); return true; }

    PlayerSettings settings = fcManager.getPlayerSettings(target);
    if (org.isInGame(target)) { logger.send(sender, Lang.TEAM_ALREADY_IN_GAME.replace(new String[]{target.getDisplayName()})); return true; }

    settings.toggleBuild();
    String status = settings.isBuildEnabled() ? Lang.ON.replace(null) : Lang.OFF.replace(null);
    logger.send(target, Lang.SET_BUILD_MODE.replace(new String[]{status}));
    logger.send(sender, Lang.SET_BUILD_MODE_OTHER.replace(new String[]{target.getDisplayName(), status}));
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.hasPermission(PERM_BUILD_OTHER)) return Collections.emptyList();

    List<String> completions = new ArrayList<>();

    if (args.length == 1) {
      fcManager.getCachedPlayers().forEach(player -> completions.add(player.getName()));
    }

    if (!completions.isEmpty()) {
      String lastWord = args[args.length - 1].toLowerCase();
      completions.removeIf(s -> !s.toLowerCase().startsWith(lastWord));
      completions.sort(String.CASE_INSENSITIVE_ORDER);
    }

    return completions;
  }
}
