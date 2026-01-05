package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.utils.Permissions.PERM_ADMIN;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpCommand;

@CommandAlias("fca|fcadmin|footcubeadmin")
@CommandPermission(PERM_ADMIN)
@Description("FootCube Admin Commands")
public class FCAdminCommand extends BaseCommand {

  @Default
  @CatchUnknown
  @HelpCommand
  @Description("Show admin commands help")
  public void onDefault(CommandHelp help) {
    help.showHelp();
  }
}