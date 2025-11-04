package io.github.divinerealms.footcube.configs;

import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

@Getter
public enum Lang {
  PREFIX("prefix", "&7[&eFC&7]"),
  PREFIX_ADMIN("prefix-admin", "&7[&cAdmin&7]"),

  BANNER_PLAYER("banner.player", String.join(System.lineSeparator(),
      "&9        __",
      "&3     .&9'&f\"'..\"&9'&3.      &e&l{0}&7, version: &6{1}",
      "&b    :.&f_.\"\"._&b.:     &7Authors: &d&l{2}",
      "&3    : &r &f\\_/&3 &3 :",
      "&b     '.&f/  \\&b.'      &9&nhttps://github.com/Neeonn/FootCube",
      "&9        \"\"",
      "&r &r",
      "{prefix}&aKucajte &e/fc help &aza listu dostupnih komandi.")),

  NO_PERM("no-perm", "{prefix}&cNemate dozvolu (&4{0}&c) za komandu &6/&e{1}&c!"),
  UNKNOWN_COMMAND("unknown-command", "{prefix}&cNepoznata komanda."),
  USAGE("usage", "{prefix}Usage: /{0}"),
  PLAYER_NOT_FOUND("player-not-found", "&cPlayer not found."),
  ON("toggle.on", "&aON"),
  OFF("toggle.off", "&cOFF"),
  HIT_COOLDOWN_INDICATION("hit-cooldown-indication", "&6Ne možete udariti loptu još &e{0}ms"),

  RELOAD("reload", "{prefix-admin}FootCube reloaded!"),
  FC_TOGGLE("toggle.status", "{prefix-admin}FootCube Matches turned {0}"),
  ADMIN_STATSET("statset", "{prefix-admin}Updated {0} for player {1} to {2}"),

  MATCHMAN_FORCE_START("matchman.force-start", "{prefix-admin}Pokrenut {0} meč."),
  MATCHMAN_FORCE_END("matchman.force-end", "{prefix-admin}Zaustavljen {0} meč."),
  MATCHMAN_SPEED("matchman.speed", "{prefix-admin}Stvoren speed buff."),

  FORCE_LEAVE("force-leave", "{prefix-admin}Izbačen igrač {0}&f iz svih mečeva/lobbya."),

  HITDEBUG_PLAYER_CHARGED("debug.hits-player.charged", "&8[&dCharged&8] &a{0}KP &8[&e&o{1}PW&8, &d&o{2}CH&8]"),
  HITDEBUG_PLAYER_REGULAR("debug.hits-player.regular", "&8[&aRegular&8] &a{0}KP"),
  HITDEBUG_PLAYER_COOLDOWN("debug.hits-player.cooldown", "&8 [&fCD: {0}{1}ms&8]"),
  HITDEBUG_CHARGED("debug.hits.charged", "{prefix}&dCharged &8| {0} &8| {1}KP &8| &e{2}PW&7, &d{3}CH"),
  HITDEBUG_REGULAR("debug.hits.regular", "{prefix}&aRegular &8| {0} &8| &a{1}KP"),

  OR("or", "&e|&b"),
  SPY_ENABLED("spy.enabled", "{prefix}&aSpy mode enabled"),
  SPY_DISABLED("spy.disabled", "{prefix}&aSpy mode disabled"),
  GLOBAL_PREFIX("global-chat-prefix", "&e&lG> &r"),

  JOIN_INTEAM("join.in-team", "&cYou are in a team, you cannot join a match"),
  JOIN_SPECIFYARENA("join.specify-arena", String.join(System.lineSeparator(), "&cYou need to specify the type of arena", "&b/fc join [3v3{0}4v4]")),
  JOIN_INVALIDTYPE("join.invalid-arena-type", String.join(System.lineSeparator(), "&c{0} is not a valid arena type", "&b/fc setuparena [3v3{1}4v4]")),
  JOIN_ALREADYINGAME("join.already-in-game", "&cYou are already in a game"),
  JOIN_NOARENA("join.no-arena", "&cNo arena found."),
  JOIN_BLOCKED("join.blocked", "{prefix}&cYou cannot join matches for another {0}min {1}s due to leaving while losing."),

  TEAM_USAGE("team.usage", String.join(System.lineSeparator(), "&b/fc team [3v3{0}4v4] [player]", "&b/fc team accept/decline/cancel")),
  TEAM_CANCEL_OTHER("team.cancel.other", "{prefix}&a{0} cancelled his request to team with you"),
  TEAM_CANCEL("team.cancel.self", "{prefix}&aYou successfully cancelled your team request"),
  TEAM_NO_REQUEST_SENT("team.no-request-sent", "&cYou haven't sent a team request"),
  TEAM_NO_REQUEST("team.no-request", "&cThere is no team request to accept"),
  TEAM_ACCEPT_OTHER("team.accept.other", String.join(System.lineSeparator(), "{prefix}&aYou succesfully teamed with {0}", "&aYou must wait for there to be place for a team, this won't take long")),
  TEAM_ACCEPT_SELF("team.accept.self", String.join(System.lineSeparator(), "{prefix}&aYou succesfully teamed with {0}", "&aYou must wait for there to be place for a team, this won't take long")),
  TEAM_SUCCESS_1("team.success-1", "{prefix}&aYou successfully teamed with {0}"),
  TEAM_SWITCH("team.switch", "{prefix}&aYou switched teams so that {0} and {1} could team"),
  TEAM_DECLINE_OTHER("team.decline.other", "{prefix}&a{0} declined your team request"),
  TEAM_DECLINE_SELF("team.decline.self", "{prefix}&aYou successfully declined the team request"),
  TEAM_DECLINE_JOIN("team.decline.join", "{prefix}&aYou declined the team request because you joined a match"),
  TEAM_ALREADY_IN_TEAM("team.already-in-team", "&cYou're already in a team"),
  TEAM_ALREADY_GOT_REQUEST("team.already-got-request", String.join(System.lineSeparator(), "&cYou already got a team request yourself by {0} for a {1} match", "&b/fc team accept &aor &b/fc team decline &ato answer the team request")),
  TEAM_ALREADY_GOT_REQUEST_OTHER("team.already-got-request-other", "&cYou already sent a team request to {0} for a {1} match"),
  TEAM_USAGE_2("team.usage-2", "&b/fc team {0} [player]"),
  TEAM_ALREADY_IN_TEAM_2("team.already-in-team-2", "&c{0} is already in a team"),
  TEAM_ALREADY_GOT_REQUEST_2("team.already-got-request-2", "&c{0} already got a team request"),
  TEAM_ALREADY_SENT("team.already-sent", "&c{0} already sent a team request to someone else"),
  TEAM_WANTS_TO_TEAM_OTHER("team.wants-to-team.other", String.join(System.lineSeparator(), "{prefix}&a{0} wants to team with you on a {1}v{1} match", "&b/fc team accept &aor &b/fc team decline &ato answer the team request")),
  TEAM_WANTS_TO_TEAM_SELF("team.wants-to-team.self", String.join(System.lineSeparator(), "{prefix}&aYou succesfully sent {0} a team request for a {1}v{1} match", "&b/fc team cancel &ato cancel this")),
  TEAM_ALREADY_IN_GAME("team.already-in-game", "&c{0} is already in a game"),
  TEAM_NOT_ONLINE("team.not-online", "&c{0} is not online"),
  TEAM_CANT_SEND_INGAME("team.cant-send-ingame", "&cYou can't send team requests while you're in a match"),
  TEAM_CANCELLED_OTHER("team.cancelled-other", "{prefix}&a{0} cancelled his team request"),
  TEAM_CANCELLED_JOIN("team.cancelled-join", "{prefix}&aYou cancelled your team request because you joined a match"),
  TEAM_YOURSELF("team.yourself", "&cCan't send a team invite to yourself."),

  TAKEPLACE_INGAME("takeplace.already-ingame", "&cYou are already in a match"),
  TAKEPLACE_NOPLACE("takeplace.no-place", "&cThere is no place to be taken"),

  STATS_NONE("stats.none", "&c{0} has never played FootCube"),
  STATS("stats.info", String.join(System.lineSeparator(),
      "&e---------------------------------------------",
      "&r {prefix}&6{0} statistika:",
      "&r &r",
      "&7 Odigrano mečeva: &f{1}",
      "&7 Statistika mečeva: &a{2}W &c{3}L &9{4}T",
      "&7 Pobeda po meču: &2{5}",
      "&7 Najveći winstreak: &e{6}",
      "&7 Ostvareno golova: &a{7}",
      "&7 Golova po meču: &a{8}",
      "&7 Nivo znanja: &a{9} &7| Rank: &f{10}",
      "&r &r",
      "&7&o Koristite &6/&efc stats &2<&aime-igrača&2> &7&oza druge...",
      "&e---------------------------------------------")),

  DENIED_COMMAND("denied-command", "{prefix}&cNe mozete koristiti tu komandu"),

  LEAVE_NOT_INGAME("leave.not-ingame", "&cYou are not even in a match"),
  LEAVE_MATCH_STARTED("leave.match-started", "&cYou can't leave when the match is already started"),
  LEFT("leave.left", "{prefix}&aYou left the match."),
  LEAVE_LOSING("leave.losing", "{prefix}&cYou left while losing! You were fined $200 and cannot join for 30 minutes."),

  UNDO("undo", "{prefix}&aUndo successful"),

  CLEARSTORE_SUCCESS("clear-store.success", "{prefix}&aYou succesfully wiped {0}'s store"),
  CLEARSTORE_NOACCOUNT("clear-store.no-account", "&c{0} doesn't have a store account"),
  CLEARSTORE_USAGE("clear-store.usage", "&b/fc clearStore [player]"),

  STATSSET_USAGE("stats-set.usage", "&b/fc statSet [Player] [Stat] [Amount{0}Clear]"),
  STATSSET_IS_NOT_A_NUMBER("stats-set.is-not-a-number", "&c{0} is not a number"),
  STATSSET_NOT_A_STAT("stats-set.not-a-stat", String.join(System.lineSeparator(), "&c{0} is not a stat, choose from:", "&7wins, matches, ties, goals, streak, store, all")),
  STATSSET_NO_ACCOUNT("stats-set.no-account", "&c{0} does not have an account"),

  SETUP_ARENA_USAGE("setup-arena.usage", String.join(System.lineSeparator(), "&cYou need to specify the type of arena", "&b/fc setuparena [3v3{0}4v4]")),
  SETUP_ARENA_INVALID("setup-arena.invalid", String.join(System.lineSeparator(), "&c{0} is not a valid arena type", "&b/fc setuparena [3v3{1}4v4]")),
  SETUP_ARENA_START("setup-arena.start", String.join(System.lineSeparator(), "{prefix}&aYou just started to setup an arena", "&aIf you got here by accident, do &b/fc undo", "&aStep 1: Stand in the middle block behind the line of the blue goal and look at the red goal, then do &b/fc set")),
  SETUP_ARENA_ALREADY_SOMEONE("setup-arena.already-someone", "&c{0} is already setting up an arena"),
  SETUP_ARENA_FIRST_SET("setup-arena.first-set", String.join(System.lineSeparator(), "{prefix}&aFirst location successfully set.", "&aNow do the same for the red goal")),
  SETUP_ARENA_SUCCESS("setup-arena.success", "{prefix}&aYou successfully setup the arena"),

  CLEAR_ARENAS_SUCCESS("clear-arenas.success", "{prefix}&aYou successfully wiped all arenas"),
  ALREADY_ENOUGH_CUBES("already-enough-cubes", "&cThere already are enough cubes"),

  BALANCE("balance", "{prefix}&aYou currently have #{0}"),

  TEAMMATE_LEFT("team.teammate-left", "{prefix}&aYour teammate left, the teaming got cancelled"),

  GLOBAL_CHATTING("global-chatting", "{prefix}&aUse &b/g [message] &afor global chatting"),

  TAKEPLACE_ANNOUNCEMENT("takeplace.announcement", "{prefix}&6ANNOUNCEMENT: &aA player left a {0}v{0} match during discussion phase"),
  TAKEPLACE_ANNOUNCEMENT_2("takeplace.announcement-2", "{prefix}&6ANNOUNCEMENT: &aA player left a {0}v{0} match, he was running {1} with {2} seconds to play"),
  TAKEPLACE_ANNOUNCEMENT_3("takeplace.announcement-3", "&aUse &b/fc takeplace &ato take his place"),

  CUBE_SPAWN("cube.spawn", "&aYou spawned a cube"),
  CUBE_CLEAR("cube.clear", "&aCleared nearest cube"),
  CUBE_CLEAR_ALL("cube.clear-all", "&aCleared {0} cube{1}"),
  CUBE_NO_CUBES("cube.no-cube", "&cNo cubes near you"),

  COMMAND_DISABLER_USAGE("command-disabler.usage", String.join(System.lineSeparator(), "&b/cd add [command]", "&b/cd remove [command]", "&b/cd list")),
  COMMAND_DISABLER_ADD_USAGE("command-disabler.add-usage", "&b/cd add [command]"),
  COMMAND_DISABLER_ALREADY_ADDED("command-disabler.already-added", "&cThis command was already added"),
  COMMAND_DISABLER_SUCCESS("command-disabler.added-successfully", "&aYou successfully added command /{0} to the list of disabled commands"),
  COMMAND_DISABLER_REMOVE_USAGE("command-disabler.remove-usage", "&b/cd remove [command]"),
  COMMAND_DISABLER_SUCCESS_REMOVE("command-disabler.removed-successfully", "&aYou successfully removed command /{0} to the list of disabled commands"),
  COMMAND_DISABLER_WASNT_ADDED("command-disabler.wasnt-added", "&cThis command wasn't even added"),
  COMMAND_DISABLER_LIST("command-disabler.list", "&6List of disabled commands:"),
  COMMAND_DISABLER_CANT_USE("command-disabler.cant-use", "&cYou cannot use this command during a match"),

  SPEED_USAGE("speed.usage", "{prefix}&aYou got speed {0} for {1} seconds, you can use this again in {2} seconds"),

  WELCOME("match.welcome", "{prefix}&aWelcome to the match, you are on the {0} team"),
  STARTING("match.starting", String.join(System.lineSeparator(), "{prefix}&aThere are enough players to start, the match will start in 30 seconds. You now have time to discuss about your strategy.", "&2TIP: &aChoose someone to be goalkeeper.", "&aUse &b/tc [Message] &afor teamchat.")),
  TO_LEAVE("match.to-leave", "&aUse &b/fc leave &ato leave this room"),
  TEAMCHAT_RED("match.tc-red", "&cTC {0}&f: "),
  TEAMCHAT_BLUE("match.tc-blue", "&1TC {0}&f: "),
  TEAMCHAT_USAGE("match.tc-usage", "&cUsage: /tc [message]"),
  MATCH_STARTED("match.started", "{prefix}&aThe match has started, good luck"),
  MATCH_PROCEED("match.proceed", "{prefix}&aThe match will now proceed"),
  MATCH_TIMES_UP("match.times-up", "{prefix}&aTime's up! The {0} team has won"),
  MATCH_WIN_CREDITS("match.win-credits", "{prefix}&aYou got 15 credits for winning"),
  MATCH_WINSTREAK_CREDITS("match.winstreak-credits", "{prefix}&6&lYou get 100 credits bonus for winning {0} times in a row!!!"),
  MATCH_TIED("match.tied", "{prefix}&aTime's up! The game is tied"),
  MATCH_TIED_CREDITS("match.tied-credits", "{prefix}&aYou got 5 credits for ending tied"),
  MATCH_SCORE_CREDITS("match.score-credits", "{prefix}&aYou got 10 credits for scoring"),
  MATCH_ASSIST_CREDITS("match.assist-credits", "{prefix}&aYou got 5 credits for assisting"),
  MATCH_SCORE_HATTRICK("match.score-hattrick", "{prefix}&6&lYou get 100 credits bonus for making a hat-trick"),
  MATCH_GOAL("match.goal", String.join(System.lineSeparator(), "{prefix}&6&lGOAL!!! &a{0} scored a goal for the {1} team from {2} blocks away and got assisted by {3}")),
  MATCH_GOAL_ASSIST("match.goal-assist", "Asistent: {0}"),
  MATCH_GOAL_TITLE("match.goal-title", "&6&lGOOOOLL!"),
  MATCH_OWN_GOAL_TITLE("match.own-goal-title", "&c&lAUTOGOL!"),
  MATCH_GOAL_SUBTITLE("match.goal-subtitle", "&a{0} scored!"),
  MATCH_SCORE_STATS("match.score-stats", String.join(System.lineSeparator(), "&aIt is now {0}-{1} Red-Blue", "&aThe match will continue in 10 seconds")),
  MATCH_WIN_TEAM("match.winning-team", "&aThe {0} team has won the match"),
  MATCH_SCORE_OWN_GOAL("match.score-own-goal", "{prefix}You scored an own goal! $200 has been deducted."),
  MATCH_SCORE_OWN_GOAL_ANNOUNCE("match.score-own-goal-announce", "{prefix}&6&lOWN GOAL! &a{0} scored a goal for the opposing team"),
  MATCH_OWN_GOAL_LEAVE("match.own-goal-leave", "{prefix}&c{0} &cje izbačen iz igre zbog auto golova!"),

  BEST_UPDATING("best.updating", String.join(System.lineSeparator(), "{prefix}&aScores are updating, please wait", "&6Updating 500 players per second with {0} players to go")),
  BEST_HEADER("best.header", String.join(System.lineSeparator(), "{prefix}&6All FootCube highscores:", "&bBest ratings:")),
  BEST_ENTRY("best.entry", "&7{0}. {1} - {2}"),
  BEST_GOALS("best.most-goals", "&bMost goals:"),
  BEST_ASSISTS("best.most-assists", "&bMost assists:"),
  BEST_OWN_GOALS("best.most-own-goals", "&bMost own goals:"),
  BEST_WINS("best.most-wins", "&bMost wins:"),
  BEST_WINSTREAK("best.winstreak", "&bLongest win streak:"),

  NOBODY("nobody", "nobody"),

  HELP("help", String.join(System.lineSeparator(), "{prefix}&6List of commands with /fc", "&b/fc join [3v3{0}4v4]", "&b/fc team [3v3{0}4v4] [player]", "&b/fc team accept/decline/cancel", "/fc group", "/fc takeplace", "/fc stats", "/fc best")),
  HELP_ADMIN("help-admin", String.join(System.lineSeparator(), "{prefix-admin}&b/fc setuparena [3v3{0}4v4]", "{prefix-admin}&b/fc cleararenas")),

  SPEED_ITEM_NAME("speed-item.name", "Speed boost"),
  SPEED_ITEM_LORE("speed-item.lore", "Right click to get speed"),

  SCOREBOARD_HEADER("scoreboard.header", "&6&lFOOTCUBE"),
  SCOREBOARD_LINES_LOBBY("scoreboard.lines.lobby", String.join(System.lineSeparator(),
      "&r &r",
      "&r &r",
      "&f &lIgrači:",
      "&f{0}",
      "&r &r",
      "&f {1}",
      "&r &r"
      )),
  SCOREBOARD_LINES_RED_PLAYERS_ENTRY("scoreboard.lines.red-players-entry", "&r  {0}. &c{1}"),
  SCOREBOARD_LINES_BLUE_PLAYERS_ENTRY("scoreboard.lines.blue-players-entry", "&r  {0}. &9{1}"),
  SCOREBOARD_LINES_MATCH("scoreboard.lines.match", String.join(System.lineSeparator(),
      "&r &r",
      "{0}",
      "&r &r",
      "&c &lCrveni &f&l{1} - {2} &9&lPlavi",
      "&r &r",
      "&e &lTimeleft:",
      "&6  ┗ &e{3}",
      "&r &r"
  )),
  SCOREBOARD_FOOTER("scoreboard.footer", "&6   &nplay.CoalBox.xyz&r  "),

  TEAMS_NOT_SETUP("teams-not-setup","&cTeams not set up yet."),
  NO_ACTIVE_PLAYERS("no-active-players", "&cNo active players in teams."),
  RED("red", "&cRed"),
  BLUE("blue", "&9Blue"),
  INGAME_ONLY("ingame-only", "&cIngame only command."),
  BETA_FEATURE("beta-feature", "&e&l[BETA FEATURE] &9"),

  PRACTICE_AREAS_EMPTY("practice-areas-empty", "&cNedefinisane zone treniranja. Ne brišemo lopte."),

  CLEARED_CUBES("cleared-cubes", "&bℹ Obrisano &e{0} lopti &fsa zona treniranja &bℹ"),
  CLEARED_CUBES_UNLOADED("cleared-cubes-unloaded", "&bℹ Obrisano &e{0} lopti &fsa unloadanog chunka &bℹ"),
  CLEARED_CUBE_INGAME("cleared-cube-ingame", "{prefix}Lopta je obrisana u igri! Stvaramo je opet."),

  PRACTICE_AREA_SET("practice-area-set", "{prefix}&fUspešno postavljena lokacija &b{0}&f (&o{1}, {2}, {3}&f)."),

  FC_DISABLED("fc-disabled", "{prefix}&cUlaženje u mečeve je privremeno deaktivirano od strane admina."),

  TOGGLES_KICK("toggles.kick", "{prefix}Zvuk udaranje lopte je {0}&f!"),
  TOGGLES_GOAL("toggles.goal", "{prefix}Zvuk postizanja lopte je {0}&f!"),
  TOGGLES_PARTICLES("toggles.particles", "{prefix}Particles je {0}&f!"),
  TOGGLES_HIT_DEBUG("toggles.hit-debug", "{prefix}Debug udaranja lopte je {0}&f!"),

  INVALID_TYPE("type.invalid", "{prefix}Taj &e{0} &fse ne može koristiti."),
  INVALID_COLOR("type.invalid-color", "{prefix}Boja &e{0} &fse ne može koristiti."),
  AVAILABLE("type.available-regular", "{prefix}Dostupno: &e{0}"),
  AVAILABLE_TYPE("type.available", "{prefix}Dostupni &e{0}&f: &e{1}"),
  SOUND("type.sound", "zvuk"),
  PARTICLE("type.particle", "particle"),
  COLOR("type.color", "boja"),

  SET_SOUND_KICK("set.sound.kick", "{prefix}Zvuk udaranje lopte podešen na: &e{0}"),
  SET_SOUND_GOAL("set.sound.goal", "{prefix}Zvuk postizanja gola podešen na: &e{0}"),
  SET_PARTICLE("set.particle.regular", "{prefix}Particle podešen na: &e{0}"),
  SET_PARTICLE_REDSTONE("set.particle.redstone", "{prefix}Particle podešen na &e{0} &fsa bojom {1}"),
  SET_BUILD_MODE("set.build-mode.self", "{prefix}Build mod {0}&f!"),
  SET_BUILD_MODE_OTHER("set.build-mode.other", "{prefix}Build mod za &b{0}&f je {1}&f!"),

  SET_BLOCK_TOO_FAR("set.block.too-far", "{prefix-admin}&cMoraš gledati u blok koji je najviše 5 blokova udaljen od tebe."),
  SET_BLOCK_SUCCESS("set.block.success", "{prefix-admin}&aUspešno postavljeno &e{0} &adugme!"),

  BLOCK_INTERACT_COOLDOWN("block-interact-cooldown", "{prefix}&cSačekajte &e{0} pre ponovnog korišćenja."),

  MATCHES_LIST_NO_MATCHES("matches.list.no-matches", "&cTrenutno nema aktivnih mečeva..."),
  MATCHES_LIST_WAITING("matches.list.waiting", "&8&oČekamo igrače..."),
  MATCHES_LIST_STARTING("matches.list.starting", "&ePočinje za &c{0}s"),
  MATCHES_LIST_CONTINUING("matches.list.continuing", "&aNastavlja za &c{0}s"),
  MATCHES_LIST_LOBBY("matches.list.lobby", "&6 &l[{0} Lobby {1}]"),
  MATCHES_LIST_MATCH("matches.list.match", "&a &l[{0} Meč {1}]"),
  MATCHES_LIST_STATUS("matches.list.status", "&7 Status: &r{0}"),
  MATCHES_LIST_RESULT("matches.list.result", "&7 Rezultat: &c{0} &7- &9{1}"),
  MATCHES_LIST_TIMELEFT("matches.list.timeleft", "&7Preostalo vreme: &e{0}"),
  MATCHES_LIST_REDPLAYERS("matches.list.players.red", "&c Crveni&7: &r{0}"),
  MATCHES_LIST_BLUEPLAYERS("matches.list.players.blue", "&9 Plavi&7: &r{0}"),
  MATCHES_LIST_HEADER("matches.list.header", String.join(System.lineSeparator(),
      "&e---------------------------------------------",
      "&r {prefix}&eLista aktivnih mečeva:",
      "&r &r")),
  MATCHES_LIST_FOOTER("matches.list.footer", String.join(System.lineSeparator(),
      "&r &r",
      "&7&o Koristite &6/&efc join &2<&a2v2&2|&a3v3&2|&a4v4&2> &7&oza ulaz...",
      "&e---------------------------------------------"));

  private static FileConfiguration LANG;
  private final String path;
  private final String def;

  Lang(String path, String start) {
    this.path = path;
    this.def = start;
  }

  public static void setFile(FileConfiguration config) {
    LANG = config;
  }

  public String getDefault() {
    return this.def;
  }

  public String replace(String[] args) {
    String value = ChatColor.translateAlternateColorCodes('&', LANG.getString(this.path, this.def));
    if (args == null) {
      return value;
    } else if (args.length == 0) {
      return value;
    } else {
      for (int i = 0; i < args.length; ++i) {
        value = value.replace("{" + i + "}", args[i]);
      }

      value = ChatColor.translateAlternateColorCodes('&', value);
      return value;
    }
  }
}
