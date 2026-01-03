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

  RELOAD("reload", "{prefix-admin}FootCube reloaded!"),
  FC_TOGGLE("toggle.status", "{prefix-admin}FootCube Matches turned {0}"),
  ADMIN_STATSET("statset", "{prefix-admin}Updated {0} for player {1} to {2}"),

  MATCHMAN_FORCE_START("matchman.force-start", "{prefix-admin}Pokrenut {0} meč."),
  MATCHMAN_FORCE_END("matchman.force-end", "{prefix-admin}Zaustavljen {0} meč."),

  FORCE_LEAVE("force-leave", "{prefix-admin}Izbačen igrač {0}&f iz svih mečeva/lobbya."),

  HITDEBUG_PLAYER_WHOLE("debug.hits-player.whole", "{0}{1}"),
  HITDEBUG_PLAYER_CHARGED("debug.hits-player.charged",
      "&8[&dCharged&8] &a{0}KP &8[&e&o{1}PW&8, &d&o{2}CH&8]"),
  HITDEBUG_PLAYER_REGULAR("debug.hits-player.regular", "&8[&aRegular&8] &a{0}KP"),
  HITDEBUG_PLAYER_COOLDOWN("debug.hits-player.cooldown", "&8 [&fCD: {0}{1}ms&8]"),
  HITDEBUG_WHOLE("debug.hits.whole", "{0}"),
  HITDEBUG_CHARGED("debug.hits.charged",
      "{prefix}&dCharged &8| {0} &8| {1}KP &8| &e{2}PW&7, &d{3}CH"),
  HITDEBUG_REGULAR("debug.hits.regular", "{prefix}&aRegular &8| {0} &8| &a{1}KP"),
  HITDEBUG_VELOCITY_CAP("debug.hits.velocity-cap",
      "{prefix}&cVelocity Cap Triggered! &7Speed: &e{0} &7-> &a{1} &7| Hitter: &f{2}"),

  OR("or", "&e|&b"),

  JOIN_SUCCESS("join.success", "&b[Trener] Ušao si u {0} lobby. Sačekaj da se skupe igrači..."),
  JOIN_INVALIDTYPE("join.invalid-arena-type",
      String.join(System.lineSeparator(), "&c{0} is not a valid arena type",
          "&b/fc setuparena [3v3{1}4v4]")),
  JOIN_ALREADYINGAME("join.already-in-game", "&cYou are already in a game"),
  JOIN_NOARENA("join.no-arena", "&cNo arena found."),

  TEAM_USAGE("team.usage",
      String.join(System.lineSeparator(), "&b/fc team [3v3{0}4v4] [player]",
          "&b/fc team accept/decline/cancel")),
  TEAM_NO_REQUEST("team.no-request", "&cThere is no team request to accept"),
  TEAM_ACCEPT_OTHER("team.accept.other",
      String.join(System.lineSeparator(), "{prefix}&aYou succesfully teamed with {0}",
          "&aYou must wait for there to be place for a team, this won't take long")),
  TEAM_ACCEPT_SELF("team.accept.self",
      String.join(System.lineSeparator(), "{prefix}&aYou succesfully teamed with {0}",
          "&aYou must wait for there to be place for a team, this won't take long")),
  TEAM_DECLINE_OTHER("team.decline.other", "{prefix}&a{0} declined your team request"),
  TEAM_DECLINE_SELF("team.decline.self", "{prefix}&aYou successfully declined the team request"),
  TEAM_ALREADY_IN_TEAM("team.already-in-team", "&cYou're already in a team"),
  TEAM_ALREADY_IN_TEAM_2("team.already-in-team-2", "&c{0} is already in a team"),
  TEAM_WANTS_TO_TEAM_OTHER("team.wants-to-team.other",
      String.join(System.lineSeparator(), "{prefix}&a{0} wants to team with you on a {1}v{1} match",
          "&b/fc team accept &aor &b/fc team decline &ato answer the team request")),
  TEAM_WANTS_TO_TEAM_SELF("team.wants-to-team.self",
      String.join(System.lineSeparator(),
          "{prefix}&aYou succesfully sent {0} a team request for a {1}v{1} match",
          "&b/fc team cancel &ato cancel this")),
  TEAM_ALREADY_IN_GAME("team.already-in-game", "&c{0} is already in a game"),
  TEAM_NOT_ONLINE("team.not-online", "&c{0} is not online"),
  TEAM_DISBANDED("team.disbanded", "&cYour team was disbanded because {0} left."),

  TAKEPLACE_SUCCESS("takeplace.success", "&b[Trener] Zauzeli ste mesto u meču #{0}"),
  TAKEPLACE_INGAME("takeplace.already-ingame", "&cYou are already in a match"),
  TAKEPLACE_NOPLACE("takeplace.no-place", "&cThere is no place to be taken"),
  TAKEPLACE_AVAILABLE_HEADER("takeplace.available.header", "&eAvailable matches to join:"),
  TAKEPLACE_AVAILABLE_ENTRY("takeplace.available.entry",
      "&e- Match #{0} &7({1}) - &a{2} &7slots open"),
  TAKEPLACE_INVALID_ID("takeplace.invalid-id", "&cCould not find an open match with ID #{0}."),
  TAKEPLACE_FULL("takeplace.full", "&cMatch #{0} is already full."),

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

  LEAVE_NOT_INGAME("leave.not-ingame", "&cYou are not even in a match"),
  LEFT("leave.left", "{prefix}&aYou left the match."),
  LEAVE_LOSING("leave.losing",
      "{prefix}&cYou left while losing! You were fined $200 and cannot join for 30 minutes."),

  UNDO("undo", "{prefix}&aUndo successful"),

  CLEAR_STATS_SUCCESS("clear-stats.success", "{prefix}&aYou succesfully wiped {0}'s store"),

  STATSSET_IS_NOT_A_NUMBER("stats-set.is-not-a-number", "&c{0} is not a number"),
  STATSSET_NOT_A_STAT("stats-set.not-a-stat",
      String.join(System.lineSeparator(), "&c{0} is not a stat, choose from:",
          "&7wins, matches, ties, goals, streak, store, all")),

  SETUP_ARENA_START("setup-arena.start",
      String.join(System.lineSeparator(), "{prefix}&aYou just started to setup an arena",
          "&aIf you got here by accident, do &b/fc undo",
          "&aStep 1: Stand in the middle block behind the line of the blue goal and look at the red goal, then do "
              +
              "&b/fc set")),
  SETUP_ARENA_FIRST_SET("setup-arena.first-set",
      String.join(System.lineSeparator(), "{prefix}&aFirst location successfully set.",
          "&aNow do the same for the red goal")),
  SETUP_ARENA_SUCCESS("setup-arena.success", "{prefix}&aYou successfully setup the arena"),

  CLEAR_ARENAS_SUCCESS("clear-arenas.success", "{prefix}&aYou successfully wiped all arenas"),
  CLEAR_ARENAS_TYPE_SUCCESS("clear-arenas.type-success", "{prefix-admin}&aUspešno obrisane &e{0} &aarene!"),
  ALREADY_ENOUGH_CUBES("already-enough-cubes", "&cThere already are enough cubes"),

  BALANCE("balance", "{prefix}&aYou currently have #{0}"),

  TAKE_PLACE_ANNOUNCEMENT_LOBBY("match.tkp-announcement.lobby",
      "{prefix}&6&lIZMENA: &aNeko je izašao iz {0}&a tokom faze diskusije."),
  TAKE_PLACE_ANNOUNCEMENT_MATCH("match.tkp-announcement.match", String.join(System.lineSeparator(),
      "{prefix}&6&lIZMENA: &aNeko je izašao iz {0}&a!",
      "&aRezultat: {1} &f{2} - {3} {4} &7(preostalo vreme: &e{5}&7)",
      "&aKucajte &e/tkp <id> &ada zauzmete mesto!")),

  CUBE_SPAWN("cube.spawn", "&aYou spawned a cube"),
  CUBE_CLEAR("cube.clear", "&aCleared nearest cube"),
  CUBE_CLEAR_ALL("cube.clear-all", "&aCleared {0} cube{1}"),
  CUBE_NO_CUBES("cube.no-cube", "&cNo cubes near you"),

  COMMAND_DISABLER_ALREADY_ADDED("command-disabler.already-added",
      "&cThis command was already added"),
  COMMAND_DISABLER_SUCCESS("command-disabler.added-successfully",
      "&aYou successfully added command /{0} to the list of disabled commands"),
  COMMAND_DISABLER_SUCCESS_REMOVE("command-disabler.removed-successfully",
      "&aYou successfully removed command /{0} to the list of disabled commands"),
  COMMAND_DISABLER_WASNT_ADDED("command-disabler.wasnt-added", "&cThis command wasn't even added"),
  COMMAND_DISABLER_LIST("command-disabler.list", "&6List of disabled commands:"),
  COMMAND_DISABLER_CANT_USE("command-disabler.cant-use",
      "&cYou cannot use this command during a match"),

  STARTING("match.starting", String.join(System.lineSeparator(),
      "{prefix}&aThere are enough players to start, the match will start in 30 seconds. You now have time to discuss "
          +
          "about your strategy.",
      "&2TIP: &aChoose someone to be goalkeeper.", "&aUse &b/tc [Message] &afor teamchat.")),
  TEAMCHAT_RED("match.tc-red", "&cTC {0}&f: "),
  TEAMCHAT_BLUE("match.tc-blue", "&1TC {0}&f: "),
  MATCH_STARTED("match.started", "{prefix}&aThe match has started, good luck"),
  MATCH_ALREADY_STARTED("match.already-started", "{prefix}&cMatch has already started."),
  MATCH_PROCEED("match.proceed", "{prefix}&aThe match will now proceed"),
  MATCH_TIMES_UP("match.times-up", "{prefix}&aTime's up! The {0} team has won"),
  MATCH_WIN_CREDITS("match.win-credits", "{prefix}&aYou got 15 credits for winning"),
  MATCH_WINSTREAK_CREDITS("match.winstreak-credits",
      "{prefix}&6&lYou get 100 credits bonus for winning {0} times in a row!!!"),
  MATCH_TIED("match.tied", "{prefix}&aTime's up! The game is tied"),
  MATCH_TIED_CREDITS("match.tied-credits", "{prefix}&aYou got 5 credits for ending tied"),
  MATCH_SCORE_CREDITS("match.score-credits", "{prefix}&aYou got 10 credits for scoring"),
  MATCH_ASSIST_CREDITS("match.assist-credits", "{prefix}&aYou got 5 credits for assisting"),
  MATCH_SCORE_HATTRICK("match.score-hattrick",
      "{prefix}&6&lYou get 100 credits bonus for making a hat-trick"),
  MATCH_HATTRICK("match.hattrick", "&6&lHAT-TRICK!!!"),
  MATCH_GOALLL("match.goalll", "&6&lGOOOOLL!!!"),
  MATCH_GOAL("match.goal", String.join(System.lineSeparator(),
      "{prefix}{0} &a{1} scored a goal for the {2} team from {3} blocks away and got assisted by {4}")),
  MATCH_GOAL_ASSIST("match.goal-assist", "Asistent: {0}"),
  MATCH_SCORE_STATS("match.score-stats",
      String.join(System.lineSeparator(), "&aIt is now {0}-{1} Red-Blue",
          "&aThe match will continue in 10 seconds")),
  MATCH_SCORE_OWN_GOAL_ANNOUNCE("match.score-own-goal-announce",
      "{prefix}&6&lOWN GOAL! &a{0} scored a goal for the opposing team"),
  LEAVE_QUEUE_ACTIONBAR("match.leave-queue-actionbar", "&cIzašli ste iz {0} queue..."),
  PLAYER_PLACEHOLDERS("match.player-placeholders", "%luckperms_prefix%%player_name%"),

  MATCH_STARTING_TITLE("match.starting-title", "&a&lGL HF!"),
  MATCH_STARTING_SUBTITLE("match.starting-subtitle", "&2Meč je počeo!"),
  MATCH_PREPARATION_TITLE("match.preparation-title", "&e&lPriprema..."),
  MATCH_PREPARATION_SUBTITLE("match.preparation-subtitle", "&6Ko će braniti?"),
  MATCH_PREPARING("match.preparing", "&3Spremamo meč..."),
  MATCH_STARTING_ACTIONBAR("match.starting-actionbar", "{0} &8┃ &e{1}"),
  MATCH_STARTED_ACTIONBAR("match.started-actionbar", "Meč je počeo, srećno!"),

  QUEUE_ACTIONBAR("match.queue-actionbar", "{0} &8┃ {1} &7({2}&7/&a{3}&7)"),

  BEST_HEADER("best.header",
      String.join(System.lineSeparator(), "{prefix}&6All FootCube highscores:", "&bBest ratings:")),
  BEST_ENTRY("best.entry", "&7{0}. {1} - {2}"),
  BEST_GOALS("best.most-goals", "&bMost goals:"),
  BEST_ASSISTS("best.most-assists", "&bMost assists:"),
  BEST_OWN_GOALS("best.most-own-goals", "&bMost own goals:"),
  BEST_WINS("best.most-wins", "&bMost wins:"),
  BEST_WINSTREAK("best.winstreak", "&bLongest win streak:"),
  BEST_UPDATING("best.updating",
      "{prefix}&cHighScores nisu još dostupni. Probajte ponovo za par sekundi..."),

  NOBODY("nobody", "nobody"),

  HELP("help", String.join(System.lineSeparator(), "{prefix}&6List of commands with /fc",
      "&b/fc join [3v3{0}4v4]",
      "&b/fc team [3v3{0}4v4] [player]", "&b/fc team accept/decline/cancel", "/fc group",
      "/fc takeplace", "/fc stats",
      "/fc best")),
  HELP_ADMIN("help-admin",
      String.join(System.lineSeparator(), "{prefix-admin}&b/fc setuparena [3v3{0}4v4]",
          "{prefix-admin}&b/fc cleararenas")),

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
  SCOREBOARD_LINES_WAITING_PLAYERS_ENTRY("scoreboard.lines.waiting-players-entry", "&r  {0}. {1}"),
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

  RED("red", "&cRed"),
  BLUE("blue", "&9Blue"),
  INGAME_ONLY("ingame-only", "&cIngame only command."),

  PRACTICE_AREAS_EMPTY("practice-areas-empty", "&cNedefinisane zone treniranja. Ne brišemo lopte."),

  CLEARED_CUBES("cleared-cubes", "&bℹ Obrisano &e{0} lopti &fsa zona treniranja &bℹ"),
  CLEARED_CUBE_INGAME("cleared-cube-ingame", "{prefix}Lopta je obrisana u igri! Stvaramo je opet."),

  PRACTICE_AREA_SET("practice-area-set",
      "{prefix}&fUspešno postavljena lokacija &b{0}&f (&o{1}, {2}, {3}&f)."),

  FC_DISABLED("fc-disabled",
      "{prefix}&cUlaženje u mečeve je privremeno deaktivirano od strane admina."),

  TOGGLES_KICK("toggles.kick", "{prefix}Zvuk udaranje lopte je {0}&f!"),
  TOGGLES_GOAL("toggles.goal", "{prefix}Zvuk postizanja lopte je {0}&f!"),
  TOGGLES_PARTICLES("toggles.particles", "{prefix}Particles je {0}&f!"),
  TOGGLES_HIT_DEBUG("toggles.hit-debug", "{prefix}Debug udaranja lopte je {0}&f!"),

  INVALID_TYPE("type.invalid", "{prefix}Taj &e{0} &fse ne može koristiti."),
  INVALID_COLOR("type.invalid-color", "{prefix}Boja &e{0} &fse ne može koristiti."),
  AVAILABLE_TYPE("type.available", "{prefix}Dostupni &e{0}&f: &e{1}"),
  SOUND("type.sound", "zvuk"),
  PARTICLE("type.particle", "particle"),
  COLOR("type.color", "boja"),

  SET_SOUND_KICK("set.sound.kick", "{prefix}Zvuk udaranje lopte podešen na: &e{0}"),
  SET_SOUND_GOAL("set.sound.goal", "{prefix}Zvuk postizanja gola podešen na: &e{0}"),
  SET_PARTICLE("set.particle.regular", "{prefix}Particle podešen na: &e{0}"),
  SET_PARTICLE_REDSTONE("set.particle.redstone",
      "{prefix}Particle podešen na &e{0} &fsa bojom {1}"),
  SET_BUILD_MODE("set.build-mode.self", "{prefix}Build mod {0}&f!"),
  SET_BUILD_MODE_OTHER("set.build-mode.other", "{prefix}Build mod za &b{0}&f je {1}&f!"),
  SET_GOAL_CELEBRATION("set.goal-celebration",
      "{prefix}Postavili ste stil proslave gola na: &e{0}"),
  GM_EPIC_TITLE_1("goal-messages.epic.title-1", "&c&lAUTOGOL!"),
  GM_EPIC_TITLE_1_GOAL("goal-messages.epic.title-1-goal", "&e&lGOOOOOL!"),
  GM_EPIC_TITLE_1_HATTY("goal-messages.epic.title-1-hattrick", "&6&l⚡ HAT-TRICK! ⚡"),
  GM_EPIC_SUBTITLE_1("goal-messages.epic.subtitle-1-own", "&7Oh ne..."),
  GM_EPIC_SUBTITLE_1_SCORER("goal-messages.epic.subtitle-1-scorer", "&a&lDAO SI GOL!"),
  GM_EPIC_SUBTITLE_1_OTHER("goal-messages.epic.subtitle-1-other", "&7{0}"),
  GM_EPIC_TITLE_2("goal-messages.epic.title-2", "&c{0}"),
  GM_EPIC_TITLE_2_GOAL("goal-messages.epic.title-2-goal", "&6{0}"),
  GM_EPIC_SUBTITLE_2("goal-messages.epic.subtitle-2", "&7Strelac: &f{0}{1}"),
  GM_EPIC_TITLE_3("goal-messages.epic.title-3", "&e{0} blokova"),
  GM_EPIC_SUBTITLE_3("goal-messages.epic.subtitle-3", "{0} &f{1} - {2} {3}"),
  GM_SIMPLE_TITLE("goal-messages.simple.title", "&c&lAUTOGOL!"),
  GM_SIMPLE_TITLE_GOAL("goal-messages.simple.title-goal", "&e&lGOOOOOL!"),
  GM_SIMPLE_SUBTITLE("goal-messages.simple.subtitle", "&7{0} &fsa &e{1} blokova"),
  GM_MINIMAL_OWN("goal-messages.minimal.own", "&cAUTOGOL &8┃ {0} &8┃ {1} &f{2} - {3} {4}"),
  GM_MINIMAL_GOAL("goal-messages.minimal.goal", "&eGOOOOOL &8┃ {0} &8┃ {1} &f{2} - {3} {4}"),
  GM_DEFAULT_TITLE_OWN("goal-messages.default.title-own", "&c&lAUTOGOL!"),
  GM_DEFAULT_TITLE_GOAL("goal-messages.default.title-goal", "&e&lGOOOOOL!"),
  GM_DEFAULT_TITLE_SCORER("goal-messages.default.title-scorer", "&a&lDAO SI GOL!"),
  GM_DEFAULT_TITLE_HATTY("goal-messages.default.title-hattrick", "&6&lHAT-TRICK!"),
  GM_DEFAULT_SUBTITLE_OWN("goal-messages.default.subtitle-own", "&7{0} &8→ &c{1}"),
  GM_DEFAULT_SUBTITLE_GOAL("goal-messages.default.subtitle-goal", "&7{0} &8┃ &e{1} blokova{2}"),
  GM_DEFAULT_ACTIONBAR("goal-mesasages.default.actionbar", "{0} &f{1} - {2} {3}"),
  GM_ASSISTS_TEXT("goal-messages.assists-text", "&7 (&f{0}&7)"),

  SET_BLOCK_TOO_FAR("set.block.too-far",
      "{prefix-admin}&cMoraš gledati u blok koji je najviše 5 blokova udaljen od tebe."),
  SET_BLOCK_SUCCESS("set.block.success", "{prefix-admin}&aUspešno postavljeno &e{0} &adugme!"),

  BLOCK_INTERACT_COOLDOWN("block-interact-cooldown",
      "{prefix}&cSačekajte &e{0} pre ponovnog korišćenja."),

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
  MATCHES_LIST_WAITINGPLAYERS("matches.list.players.waiting", "&f Queue&7: &r{0}"),
  MATCHES_LIST_HEADER("matches.list.header", String.join(System.lineSeparator(),
      "&e---------------------------------------------",
      "&r {prefix}&eLista aktivnih mečeva:",
      "&r &r")),
  MATCHES_LIST_FOOTER("matches.list.footer", String.join(System.lineSeparator(),
      "&r &r",
      "&7&o Koristite &6/&efc join &2<&a2v2&2|&a3v3&2|&a4v4&2> &7&oza ulaz...",
      "&e---------------------------------------------")),

  TASKS_REPORT_HEADER("plugin-stats.report.tasks.header", String.join(System.lineSeparator(),
      "&e-------------[ &6&lTask Status &e]----------------",
      "&r &r",
      "&7  Running: &e{0} &8/ &7{1}",
      "&r &r")),
  TASKS_REPORT_ENTRY("plugin-stats.report.tasks.entry", "  {0} &d{1}&f: &a{2}ms &87({3} runs)"),
  TASKS_REPORT_FOOTER("plugin-stats.report.tasks.footer", String.join(System.lineSeparator(),
      "&r &r",
      "&7 &lOverall Average Tick Time: &a{0}ms",
      "&e---------------------------------------------")),
  TASKS_RESTART("plugin-stats.report.tasks.restart",
      "{prefix-admin}&aAll tasks have been restarted."),
  TASKS_RESET_STATS("plugin-stats.report.tasks.reset-stats",
      "{prefix-admin}&aAll task statistics have been reset."),

  PLAYER_BANNED("bans.success", "{prefix-admin}{0} &cje banovan iz FC na &e{1}&c."),
  PLAYER_UNBANNED("bans.unbanned", "{prefix-admin}{0} &aje unbanovan."),
  BAN_REMAINING("bans.remaining", "{prefix-admin}{0} &cje banovan još &e{1}&c."),
  NOT_BANNED("bans.not-banned", "{prefix-admin}{0} &cnije banovan."),

  SIMPLE_FOOTER("simple-footer", "&e---------------------------------------------");

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

  public String replace(String... args) {
    if (LANG == null) {
      return ChatColor.translateAlternateColorCodes('&', def);
    }

    String value = LANG.getString(this.path, this.def);

    if (value.contains("{prefix}")) {
      value = value.replace("{prefix}", PREFIX.toString());
    }

    if (value.contains("{prefix-admin}")) {
      value = value.replace("{prefix-admin}", PREFIX_ADMIN.toString());
    }

    if (args != null && args.length > 0) {
      for (int i = 0; i < args.length; i++) {
        if (args[i] != null) {
          value = value.replace("{" + i + "}", args[i]);
        }
      }
    }

    return ChatColor.translateAlternateColorCodes('&', value);
  }

  @Override
  public String toString() {
    return this.replace();
  }
}
