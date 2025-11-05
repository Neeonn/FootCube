package io.github.divinerealms.footcube.core;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.utils.HighScores;
import io.github.divinerealms.footcube.utils.Logger;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

@Setter
public class Organization {
  private final FCManager fcManager;
  @Getter private final Plugin plugin;
  @Getter private final Logger logger;
  private final PlayerDataManager playerDataManager;
  @Getter private HighScores highscores;
  @Getter private String setupGuy;
  @Getter private int setupType;
  @Getter private Location setupLoc;
  @Getter private Match[] matches2v2;
  @Getter private Match[] matches3v3;
  @Getter private Match[] matches4v4;
  @Getter private int lobby2v2;
  @Getter private int lobby3v3;
  @Getter private int lobby4v4;
  @Getter public HashMap<String, Integer> waitingPlayers;
  @Getter public ArrayList<String> playingPlayers;
  @Getter private final HashMap<Player, Player> team;
  @Getter private final HashMap<Player, Player> teamReverse;
  @Getter private final HashMap<Player, Integer> teamType;
  @Getter private Player[][] waitingTeams;
  @Getter private final ArrayList<Player> waitingTeamPlayers;
  @Getter private Match[] leftMatches;
  @Getter private boolean[] leftPlayerIsRed;
  @Getter private final Map<UUID, Long> leaveCooldowns;

  private long announcementTime;
  private int updateTaskId;

  private final FileConfiguration arenas;

  public Organization(FCManager fcManager) {
    this.fcManager = fcManager;
    this.setupGuy = null;
    this.setupType = 0;
    this.setupLoc = null;
    this.matches2v2 = new Match[0];
    this.matches3v3 = new Match[0];
    this.matches4v4 = new Match[0];
    this.lobby2v2 = 0;
    this.lobby3v3 = 0;
    this.lobby4v4 = 0;
    this.waitingPlayers = new HashMap<>();
    this.playingPlayers = new ArrayList<>();
    this.team = new HashMap<>();
    this.teamReverse = new HashMap<>();
    this.teamType = new HashMap<>();
    this.waitingTeams = new Player[0][0];
    this.waitingTeamPlayers = new ArrayList<>();
    this.leftMatches = new Match[0];
    this.leftPlayerIsRed = new boolean[0];
    this.leaveCooldowns = new HashMap<>();
    this.plugin = fcManager.getPlugin();
    this.logger = fcManager.getLogger();
    this.playerDataManager = fcManager.getDataManager();
    this.highscores = new HighScores(fcManager);

    ConfigManager configManager = fcManager.getConfigManager();
    configManager.createNewFile("arenas.yml", "Arenas");
    this.arenas = configManager.getConfig("arenas.yml");
    arenas.addDefault("arenas.2v2.amount", 0);
    arenas.addDefault("arenas.3v3.amount", 0);
    arenas.addDefault("arenas.4v4.amount", 0);
    arenas.options().copyDefaults(true);
    configManager.saveConfig("arenas.yml");

    this.updateTaskId = plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::update, 1L, 1L).getTaskId();
  }

  public void matchStart(int type) {
    if (type == 2) {
      for (int i = 0; i < this.matches2v2.length; ++i) {
        if (this.matches2v2[i].phase == 1) {
          this.lobby2v2 = i;
          break;
        }
      }

      for (int i = 0; i < this.waitingTeams.length; ++i) {
        if (this.waitingTeams[i].length > 1 && this.matches2v2[this.lobby2v2].team(this.waitingTeams[i][0], this.waitingTeams[i][1])) {
          this.waitingTeamPlayers.remove(this.waitingTeams[i][0]);
          this.waitingTeamPlayers.remove(this.waitingTeams[i][1]);
          this.waitingTeams = this.reduceArray(this.waitingTeams, this.waitingTeams[i][0]);
        }
      }
    } else if (type == 3) {
      for (int i = 0; i < this.matches3v3.length; ++i) {
        if (this.matches3v3[i].phase == 1) {
          this.lobby3v3 = i;
          break;
        }
      }

      for (int i = 0; i < this.waitingTeams.length; ++i) {
        if (this.waitingTeams[i].length > 2 && this.matches3v3[this.lobby3v3].team(this.waitingTeams[i][0], this.waitingTeams[i][1])) {
          this.waitingTeamPlayers.remove(this.waitingTeams[i][0]);
          this.waitingTeamPlayers.remove(this.waitingTeams[i][1]);
          this.waitingTeams = this.reduceArray(this.waitingTeams, this.waitingTeams[i][0]);
        }
      }
    } else {
      for (int i = 0; i < this.matches4v4.length; ++i) {
        if (this.matches4v4[i].phase == 1) {
          this.lobby4v4 = i;
          break;
        }
      }

      for (int i = 0; i < this.waitingTeams.length; ++i) {
        if (this.waitingTeams[i].length > 3 && this.matches4v4[this.lobby4v4].team(this.waitingTeams[i][0], this.waitingTeams[i][1])) {
          this.waitingTeamPlayers.remove(this.waitingTeams[i][0]);
          this.waitingTeamPlayers.remove(this.waitingTeams[i][1]);
          this.waitingTeams = this.reduceArray(this.waitingTeams, this.waitingTeams[i][0]);
        }
      }
    }
  }

  public void playerLeaves(Match m, boolean red) {
    if (m.phase < 2) return;

    this.leftMatches = this.extendArray(this.leftMatches, m);
    this.leftPlayerIsRed = this.extendArray(this.leftPlayerIsRed, red);

    if (this.leftMatches.length < 2) {
      this.announcementTime = System.currentTimeMillis();
      String v;
      if (red) {
        v = m.scoreRed + "-" + m.scoreBlue;
        if (m.scoreRed > m.scoreBlue) {
          v = v + " in the lead";
        } else if (m.scoreRed < m.scoreBlue) {
          v = v + " behind";
        }
      } else {
        v = m.scoreBlue + "-" + m.scoreRed;
        if (m.scoreRed < m.scoreBlue) {
          v = v + " in the lead";
        } else if (m.scoreRed > m.scoreBlue) {
          v = v + " behind";
        }
      }

      for (Player p : fcManager.getCachedPlayers()) {
        if (!isInGame(p)) {
          if (m.scoreTime < 0) {
            logger.send(p, Lang.TAKEPLACE_ANNOUNCEMENT.replace(new String[]{String.valueOf(m.type)}));
          } else {
            logger.send(p, Lang.TAKEPLACE_ANNOUNCEMENT_2.replace(new String[]{String.valueOf(m.type), v, String.valueOf(m.scoreTime)}));
          }

          logger.send(p, Lang.TAKEPLACE_ANNOUNCEMENT_3.replace(null));
        }
      }
    }
  }

  public boolean isBanned(Player player) {
    UUID uuid = player.getUniqueId();

    if (!leaveCooldowns.containsKey(uuid)) return false;

    long until = leaveCooldowns.get(uuid);
    long now = System.currentTimeMillis();

    if (now < until) {
      long remainingSec = (until - now) / 1000;
      String formattedTime = Utilities.formatTime(remainingSec);

      logger.send(player, Lang.JOIN_BLOCKED.replace(new String[]{formattedTime}));
      return true;
    } else {
      leaveCooldowns.remove(uuid);
      return false;
    }
  }

  public void undoTakePlace(Match m) {
    int matches = 0;
    for (Match match : this.leftMatches) if (m.equals(match)) ++matches;

    Match[] newL = new Match[this.leftMatches.length - matches];
    boolean[] newB = new boolean[this.leftMatches.length - matches];
    int i = 0;

    for (int l = 0; i < this.leftMatches.length; ++i) {
      if (!this.leftMatches[i].equals(m)) {
        newL[l] = this.leftMatches[i];
        newB[l] = this.leftPlayerIsRed[i];
        ++l;
      }
    }

    this.leftMatches = newL;
    this.leftPlayerIsRed = newB;
  }

  public void endMatch(Player player) {
    this.playingPlayers.remove(player.getName());
    player.setLevel(0);
  }

  public void playerStarts(Player player) {
    this.playingPlayers.add(player.getName());
    this.waitingPlayers.remove(player.getName());
  }

  public void ballTouch(Player player, TouchType type) {
    for (Match match : this.matches2v2) match.kick(player, type);
    for (Match match : this.matches3v3) match.kick(player, type);
    for (Match match : this.matches4v4) match.kick(player, type);
  }

  public ItemStack createComplexItem(Material material, String name, String[] lore) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(name);
    ArrayList<String> loreArray = new ArrayList<>();

    Collections.addAll(loreArray, lore);

    meta.setLore(loreArray);
    item.setItemMeta(meta);
    return item;
  }

  public void clearInventory(Player p) {
    PlayerInventory inv = p.getInventory();
    inv.setChestplate(null);
    inv.setLeggings(null);
    inv.setItem(4, null);
  }

  public void removeTeam(Player p) {
    if (this.team.containsKey(p)) {
      Player player = this.team.get(p);
      logger.send(player, Lang.TEAM_DECLINE_OTHER.replace(new String[]{p.getName()}));
      logger.send(p, Lang.TEAM_DECLINE_JOIN.replace(null));
      this.teamType.remove(player);
      this.teamReverse.remove(player);
      this.team.remove(p);
    }

    if (this.teamReverse.containsKey(p)) {
      Player player = this.teamReverse.get(p);
      logger.send(player, Lang.TEAM_CANCELLED_OTHER.replace(new String[]{p.getName()}));
      logger.send(p, Lang.TEAM_CANCELLED_JOIN.replace(null));
      this.teamType.remove(p);
      this.teamReverse.remove(p);
      this.team.remove(player);
    }
  }

  public Player[][] extendArray(Player[][] oldL, Player[] add) {
    Player[][] newL = new Player[oldL.length + 1][];
    System.arraycopy(oldL, 0, newL, 0, oldL.length);
    newL[oldL.length] = add;
    return newL;
  }

  public Player[][] reduceArray(Player[][] oldL, Player remove) {
    Player[][] newL = new Player[0][oldL.length - 1];
    int i = 0;
    for(int j = 0; i < newL.length; ++i) {
      if (oldL[i][0] != remove && oldL[i][1] != remove) {
        newL[j] = oldL[i];
        ++j;
      }
    }
    return newL;
  }

  private Match[] extendArray(Match[] oldL, Match add) {
    Match[] newL = new Match[oldL.length + 1];
    System.arraycopy(oldL, 0, newL, 0, oldL.length);
    newL[oldL.length] = add;
    return newL;
  }

  private boolean[] extendArray(boolean[] oldL, boolean add) {
    boolean[] newL = new boolean[oldL.length + 1];
    System.arraycopy(oldL, 0, newL, 0, oldL.length);
    newL[oldL.length] = add;
    return newL;
  }

  public boolean isOnlinePlayer(String s) {
    for(Player p : fcManager.getCachedPlayers()) {
      if (p.getName().equalsIgnoreCase(s)) {
        return true;
      }
    }

    return false;
  }

  public void addArena(int type, Location b, Location r) {
    Location m = new Location(b.getWorld(), (b.getX() + r.getX()) / (double)2.0F, (b.getY() + r.getY()) / (double)2.0F + (double)2.0F, (b.getZ() + r.getZ()) / (double)2.0F);
    int id;

    if (type == 2) {
      id = this.matches2v2.length + 1;
      this.matches2v2 = this.extendArray(this.matches2v2, new Match(fcManager, 2, b, r, m, id));
    } else if (type == 3) {
      id = this.matches3v3.length + 1;
      this.matches3v3 = this.extendArray(this.matches3v3, new Match(fcManager, 3, b, r, m, id));
    } else {
      id = this.matches4v4.length + 1;
      this.matches4v4 = this.extendArray(this.matches4v4, new Match(fcManager, 4, b, r, m, id));
    }
  }

  public void loadArenas() {
    for (int i = 1; i <= arenas.getInt("arenas.2v2.amount"); ++i) {
      World world = this.plugin.getServer().getWorld(arenas.getString("arenas.world"));
      String blue = "arenas.2v2." + i + ".blue.";
      String red = "arenas.2v2." + i + ".red.";
      Location b = new Location(world, arenas.getDouble(blue + "x"), arenas.getDouble(blue + "y"), arenas.getDouble(blue + "z"));
      b.setPitch((float) arenas.getDouble(blue + "pitch"));
      b.setYaw((float) arenas.getDouble(blue + "yaw"));
      Location r = new Location(world, arenas.getDouble(red + "x"), arenas.getDouble(red + "y"), arenas.getDouble(red + "z"));
      r.setPitch((float) arenas.getDouble(red + "pitch"));
      r.setYaw((float) arenas.getDouble(red + "yaw"));
      this.addArena(2, b, r);
    }

    for (int i = 1; i <= arenas.getInt("arenas.3v3.amount"); ++i) {
      World world = this.plugin.getServer().getWorld(arenas.getString("arenas.world"));
      String blue = "arenas.3v3." + i + ".blue.";
      String red = "arenas.3v3." + i + ".red.";
      Location b = new Location(world, arenas.getDouble(blue + "x"), arenas.getDouble(blue + "y"), arenas.getDouble(blue + "z"));
      b.setPitch((float) arenas.getDouble(blue + "pitch"));
      b.setYaw((float) arenas.getDouble(blue + "yaw"));
      Location r = new Location(world, arenas.getDouble(red + "x"), arenas.getDouble(red + "y"), arenas.getDouble(red + "z"));
      r.setPitch((float) arenas.getDouble(red + "pitch"));
      r.setYaw((float) arenas.getDouble(red + "yaw"));
      this.addArena(3, b, r);
    }

    for (int i = 1; i <= arenas.getInt("arenas.4v4.amount"); ++i) {
      World world = this.plugin.getServer().getWorld(arenas.getString("arenas.world"));
      String blue = "arenas.4v4." + i + ".blue.";
      String red = "arenas.4v4." + i + ".red.";
      Location b = new Location(world, arenas.getDouble(blue + "x"), arenas.getDouble(blue + "y"), arenas.getDouble(blue + "z"));
      b.setPitch((float) arenas.getDouble(blue + "pitch"));
      b.setYaw((float) arenas.getDouble(blue + "yaw"));
      Location r = new Location(world, arenas.getDouble(red + "x"), arenas.getDouble(red + "y"), arenas.getDouble(red + "z"));
      r.setPitch((float) arenas.getDouble(red + "pitch"));
      r.setYaw((float) arenas.getDouble(red + "yaw"));
      this.addArena(4, b, r);
    }
  }

  public void checkStats(String playerName, CommandSender asker) {
    PlayerData data = playerDataManager.get(playerName);
    if (data == null || !data.has("matches")) {
      logger.send(asker, Lang.STATS_NONE.replace(new String[]{playerName}));
      return;
    }

    int matches = (int) data.get("matches");
    int wins = (int) data.get("wins");
    int ties = (int) data.get("ties");
    int bestWinStreak = (int) data.get("bestwinstreak");
    int losses = matches - wins - ties;

    double winsPerMatch = (matches > 0) ? (double) wins / matches : 0;

    int goals = (int) data.get("goals");
    int assists = (int) data.get("assists");
    int ownGoals = (int) data.get("owngoals");
    double goalsPerMatch = (matches > 0) ? (double) goals / matches : 0;

    double multiplier = 1.0 - Math.pow(0.9, matches);
    double goalBonus = matches > 0
        ? (goals == matches ? 1.0 : Math.min(1.0, 1 - multiplier * Math.pow(0.2, (double) goals / matches)))
        : 0.5;

    double addition = 0.0;
    if (matches > 0 && wins + ties > 0) {
      addition = 8.0 * (1.0 / ((100.0 * matches) / (wins + 0.5 * ties) / 100.0)) - 4.0;
    } else if (matches > 0) {
      addition = -4.0;
    }

    double skillLevel = Math.min(5.0 + goalBonus + addition * multiplier, 10.0);
    int rank = (int) (skillLevel * 2.0 - 0.5);
    String rang;

    switch (rank) {
      case 1: rang = "Nub"; break;
      case 2: rang = "Luzer"; break;
      case 3: rang = "Beba"; break;
      case 4: rang = "Učenik"; break;
      case 5: rang = "Loš"; break;
      case 6: rang = ":("; break;
      case 7: rang = "Eh"; break;
      case 8: rang = "Igrač"; break;
      case 9: rang = "Ok"; break;
      case 10: rang = "Prosečan"; break;
      case 11: rang = "Dobar"; break;
      case 12: rang = "Odličan"; break;
      case 13: rang = "Kralj"; break;
      case 14: rang = "Super"; break;
      case 15: rang = "Pro"; break;
      case 16: rang = "Maradona"; break;
      case 17: rang = "Supermen"; break;
      case 18: rang = "Bog"; break;
      case 19: rang = "h4x0r"; break;
      default: rang = "Nema"; break;
    }

    logger.send(asker, Lang.STATS.replace(new String[]{
        playerName, String.valueOf(matches), String.valueOf(wins), String.valueOf(losses),
        String.valueOf(ties), String.format("%.2f", winsPerMatch), String.valueOf(bestWinStreak),
        String.valueOf(goals), String.format("%.2f", goalsPerMatch), String.valueOf(assists), String.format("%.2f", skillLevel), rang, String.valueOf(ownGoals)
    }));
  }

  public boolean isInGame(Player player) {
    return getWaitingPlayers().containsKey(player.getName()) || getPlayingPlayers().contains(player.getName()) || getWaitingTeamPlayers().contains(player);
  }

  public void updateHighScores(Player player) {
    if (highscores == null) {
      highscores = new HighScores(fcManager);
      highscores.playerUpdate(player);
    } else if (highscores.needsUpdate()) {
      highscores.playerUpdate(player);
    } else if (highscores.isUpdating) {
      highscores.addWaitingPlayer(player);
    } else {
      highscores.showHighScores(player);
    }
  }

  private void update() {
    for (Match match : this.matches2v2) {
      if (match.hasPlayers()) match.update();
    }

    for (Match match : this.matches3v3) {
      if (match.hasPlayers()) match.update();
    }

    for (Match match : this.matches4v4) {
      if (match.hasPlayers()) match.update();
    }

    if (this.leftMatches.length > 0 && System.currentTimeMillis() - this.announcementTime > 30000L) {
      Match m = this.leftMatches[0];
      this.announcementTime = System.currentTimeMillis();
      String v;
      if (this.leftPlayerIsRed[0]) {
        v = m.scoreRed + "-" + m.scoreBlue;
        if (m.scoreRed > m.scoreBlue) {
          v = v + " in the lead";
        } else if (m.scoreRed < m.scoreBlue) {
          v = v + " behind";
        }
      } else {
        v = m.scoreBlue + "-" + m.scoreRed;
        if (m.scoreRed < m.scoreBlue) {
          v = v + " in the lead";
        } else if (m.scoreRed > m.scoreBlue) {
          v = v + " behind";
        }
      }

      for (Player p : fcManager.getCachedPlayers()) {
        if (!this.playingPlayers.contains(p.getName()) && !this.waitingPlayers.containsKey(p.getName())) {
          if (m.scoreTime < 0) {
            logger.send(p, Lang.TAKEPLACE_ANNOUNCEMENT.replace(new String[]{String.valueOf(m.type)}));
          } else {
            logger.send(p, Lang.TAKEPLACE_ANNOUNCEMENT_2.replace(new String[]{String.valueOf(m.type), v, String.valueOf(m.scoreTime)}));
          }

          logger.send(p, Lang.TAKEPLACE_ANNOUNCEMENT_3.replace(null));
        }
      }
    }
  }

  public List<String> getMatches() {
    List<String> output = new ArrayList<>();
    boolean firstBlock = true;

    for (String type : Arrays.asList("2v2", "3v3", "4v4")) {
      MatchHelper.ArenaData data = MatchHelper.getArenaData(this, type);
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
          int remaining = match.scoreTime != 0 ? match.scoreTime : -1;
          timeDisplay = remaining >= 0 ? Utilities.formatTime(remaining) : "N/A";
        }

        if (match.phase == 1) {
          output.add(Lang.MATCHES_LIST_LOBBY.replace(new String[]{type, String.valueOf(match.matchID)}));
          output.add(Lang.MATCHES_LIST_REDPLAYERS.replace(new String[]{redPlayers.isEmpty() ? "/" : String.join(", ", redPlayers)}));
          output.add(Lang.MATCHES_LIST_BLUEPLAYERS.replace(new String[]{bluePlayers.isEmpty() ? "/" : String.join(", ", bluePlayers)}));
          output.add(Lang.MATCHES_LIST_STATUS.replace(new String[]{timeDisplay}));
        } else {
          output.add(Lang.MATCHES_LIST_MATCH.replace(new String[]{type, String.valueOf(match.matchID)}));
          output.add(Lang.MATCHES_LIST_RESULT.replace(new String[]{String.valueOf(match.scoreRed), String.valueOf(match.scoreBlue),
              Lang.MATCHES_LIST_TIMELEFT.replace(new String[]{timeDisplay})}));
          output.add(Lang.MATCHES_LIST_REDPLAYERS.replace(new String[]{redPlayers.isEmpty() ? "/" : String.join(", ", redPlayers)}));
          output.add(Lang.MATCHES_LIST_BLUEPLAYERS.replace(new String[]{bluePlayers.isEmpty() ? "/" : String.join(", ", bluePlayers)}));
        }
      }
    }

    return output;
  }

  public void cleanup() {
    try {
      if (this.updateTaskId != -1) {
        plugin.getServer().getScheduler().cancelTask(this.updateTaskId);
        this.updateTaskId = -1;
      }
    } catch (Exception ignored) {}

    try {
      if (matches2v2 != null) for (Match m : matches2v2) if (m != null) m.cleanup();
      if (matches3v3 != null) for (Match m : matches3v3) if (m != null) m.cleanup();
      if (matches4v4 != null) for (Match m : matches4v4) if (m != null) m.cleanup();
      if (leftMatches != null) for (Match m : leftMatches) if (m != null) m.cleanup();
    } catch (Exception ignored) {}

    try {
      waitingPlayers.clear();
      playingPlayers.clear();
      waitingTeamPlayers.clear();
      team.clear();
      teamReverse.clear();
      teamType.clear();
      leaveCooldowns.clear();

      waitingTeams = new Player[0][0];
      lobby2v2 = 0;
      lobby3v3 = 0;
      lobby4v4 = 0;
      matches2v2 = new Match[0];
      matches3v3 = new Match[0];
      matches4v4 = new Match[0];
      leftMatches = new Match[0];
      leftPlayerIsRed = new boolean[0];

      highscores = new HighScores(fcManager);
    } catch (Exception ignored) {}

    try {
      setupGuy = null;
      setupType = 0;
      setupLoc = null;
      announcementTime = 0L;
    } catch (Exception ignored) {}

    try {
      plugin.getServer().getScheduler().cancelTasks(plugin);
    } catch (Exception ignored) {}
  }
}