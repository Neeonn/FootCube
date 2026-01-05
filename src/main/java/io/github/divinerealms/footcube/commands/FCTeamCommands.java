package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.FC_DISABLED;
import static io.github.divinerealms.footcube.configs.Lang.JOIN_ALREADYINGAME;
import static io.github.divinerealms.footcube.configs.Lang.JOIN_INVALIDTYPE;
import static io.github.divinerealms.footcube.configs.Lang.OR;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ACCEPT_OTHER;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ACCEPT_SELF;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ALREADY_IN_GAME;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ALREADY_IN_TEAM;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_ALREADY_IN_TEAM_2;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_DECLINE_OTHER;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_DECLINE_SELF;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_NOT_ONLINE;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_NO_REQUEST;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_WANTS_TO_TEAM_OTHER;
import static io.github.divinerealms.footcube.configs.Lang.TEAM_WANTS_TO_TEAM_SELF;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.FOUR_V_FOUR;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.THREE_V_THREE;
import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.TWO_V_TWO;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_PLAY;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.matchmaking.MatchManager;
import io.github.divinerealms.footcube.matchmaking.team.TeamManager;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.entity.Player;

@CommandAlias("fc|footcube")
public class FCTeamCommands extends BaseCommand {

  private final FCManager fcManager;
  private final Logger logger;
  private final MatchManager matchManager;
  private final TeamManager teamManager;

  public FCTeamCommands(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.matchManager = fcManager.getMatchManager();
    this.teamManager = matchManager.getTeamManager();
  }

  @Subcommand("team accept|t accept")
  @CommandPermission(PERM_PLAY)
  @Description("Accept a team invitation")
  public void onTeamAccept(Player player) {
    if (!matchManager.getData().isMatchesEnabled()) {
      logger.send(player, FC_DISABLED);
      return;
    }

    if (teamManager.isInTeam(player)) {
      logger.send(player, TEAM_ALREADY_IN_TEAM);
      return;
    }

    if (teamManager.noInvite(player)) {
      logger.send(player, TEAM_NO_REQUEST);
      return;
    }

    Player target = teamManager.getInviter(player);
    String targetName = target != null && target.isOnline() ? target.getName() : "";

    if (target == null || !target.isOnline()) {
      logger.send(player, TEAM_NOT_ONLINE, targetName);
      return;
    }

    if (teamManager.isInTeam(target)) {
      logger.send(player, TEAM_ALREADY_IN_TEAM_2, target.getName());
      teamManager.removeInvite(player);
      return;
    }

    int mType = teamManager.getInviteMatchType(player);
    teamManager.createTeam(target, player, mType);
    logger.send(player, TEAM_ACCEPT_SELF, target.getName());
    logger.send(target, TEAM_ACCEPT_OTHER, player.getName());

    matchManager.joinQueue(player, mType);
    teamManager.removeInvite(player);
  }

  @Subcommand("team decline|t decline")
  @CommandPermission(PERM_PLAY)
  @Description("Decline a team invitation")
  public void onTeamDecline(Player player) {
    if (!matchManager.getData().isMatchesEnabled()) {
      logger.send(player, FC_DISABLED);
      return;
    }

    if (teamManager.noInvite(player)) {
      logger.send(player, TEAM_NO_REQUEST);
      return;
    }

    Player target = teamManager.getInviter(player);
    if (target != null && target.isOnline()) {
      logger.send(target, TEAM_DECLINE_OTHER, player.getName());
    }

    logger.send(player, TEAM_DECLINE_SELF);
    teamManager.removeInvite(player);
  }

  @Subcommand("team|t")
  @CommandPermission(PERM_PLAY)
  @Syntax("<2v2|3v3|4v4> <player>")
  @CommandCompletion("2v2|3v3|4v4 @players")
  @Description("Invite a player to team up")
  public void onTeamInvite(Player player, String matchType, Player target) {
    if (!matchManager.getData().isMatchesEnabled()) {
      logger.send(player, FC_DISABLED);
      return;
    }

    if (teamManager.isInTeam(player)) {
      logger.send(player, TEAM_ALREADY_IN_TEAM);
      return;
    }

    if (target == null) {
      logger.send(player, TEAM_NOT_ONLINE, matchType);
      return;
    }

    if (teamManager.isInTeam(target)) {
      logger.send(player, TEAM_ALREADY_IN_TEAM_2, target.getName());
      return;
    }

    if (fcManager.getMatchSystem().isInAnyQueue(player)) {
      logger.send(player, JOIN_ALREADYINGAME);
      return;
    }

    if (fcManager.getMatchSystem().isInAnyQueue(target)) {
      logger.send(player, TEAM_ALREADY_IN_GAME, target.getName());
      return;
    }

    if (matchManager.getMatch(player).isPresent()) {
      logger.send(player, JOIN_ALREADYINGAME);
      return;
    }

    if (matchManager.getMatch(target).isPresent()) {
      logger.send(player, TEAM_ALREADY_IN_GAME, target.getName());
      return;
    }

    int inviteType;
    switch (matchType.toLowerCase()) {
      case "2v2":
        inviteType = TWO_V_TWO;
        break;
      case "3v3":
        inviteType = THREE_V_THREE;
        break;
      case "4v4":
        inviteType = FOUR_V_FOUR;
        break;
      default:
        logger.send(player, JOIN_INVALIDTYPE, matchType, OR.toString());
        return;
    }

    teamManager.invite(player, target, inviteType);
    logger.send(player, TEAM_WANTS_TO_TEAM_SELF, target.getName(), matchType);
    logger.send(target, TEAM_WANTS_TO_TEAM_OTHER, player.getName(), matchType);
  }
}