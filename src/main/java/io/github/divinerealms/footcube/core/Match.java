package io.github.divinerealms.footcube.core;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.managers.ConfigManager;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.MatchHelper;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Match {
  private final FCManager fcManager;
  private final Logger logger;
  public int matchID;
  public int type;
  public int phase;
  public int countdown;
  public int tickToSec;
  private int teams;
  private long startTime;
  public final HashMap<Player, Long> sugarCooldown = new HashMap<>();
  public final Location blue;
  public final Location red;
  private final Location mid;
  private final Organization organization;
  private final boolean x;
  private final boolean redAboveBlue;

  public ArrayList<Player> redPlayers = new ArrayList<>();
  public ArrayList<Player> bluePlayers = new ArrayList<>();

  public ArrayList<Player> teamers = new ArrayList<>();
  public final ArrayList<Player> takePlace = new ArrayList<>();
  public HashMap<Player, Boolean> isRed = new HashMap<>();
  private Player lastKickRed = null;
  private Player lastKickBlue = null;
  private Player assistRed = null;
  private Player assistBlue = null;
  public int scoreRed;
  public int scoreBlue;
  public final HashMap<Player, Integer> goals = new HashMap<>();
  public final HashMap<UUID, Integer> ownGoals = new HashMap<>();
  private final ItemStack redChestPlate;
  private final ItemStack redLeggings;
  private final ItemStack blueChestPlate;
  private final ItemStack blueLeggings;
  public final ItemStack sugar;
  public Score time;
  private final Score redGoals;
  private final Score blueGoals;
  private final ScoreboardManager sbm;
  private final Scoreboard sb;
  public Slime cube;

  private final FileConfiguration config;

  public Match(FCManager fcManager, int t, Location b, Location r, Location m, int id) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.matchID = id;
    this.organization = fcManager.getOrg();
    this.type = t;
    this.blue = b;
    this.red = r;
    this.mid = m;
    this.phase = 1;
    this.scoreRed = 0;
    this.scoreBlue = 0;
    this.startTime = 0L;
    this.redChestPlate = this.createColoredArmour(Material.LEATHER_CHESTPLATE, Color.RED);
    this.redLeggings = this.createColoredArmour(Material.LEATHER_LEGGINGS, Color.RED);
    this.blueChestPlate = this.createColoredArmour(Material.LEATHER_CHESTPLATE, Color.BLUE);
    this.blueLeggings = this.createColoredArmour(Material.LEATHER_LEGGINGS, Color.BLUE);
    String itemName = Lang.SPEED_ITEM_NAME.replace(null);
    String itemLore = Lang.SPEED_ITEM_LORE.replace(null);
    this.sugar = this.organization.createComplexItem(Material.SUGAR, itemName, new String[]{itemLore});
    this.sbm = Bukkit.getScoreboardManager();
    this.sb = this.sbm.getNewScoreboard();
    boolean objectiveExists = false;

    for(Objective ob : this.sb.getObjectives()) {
      if (ob.getName().equalsIgnoreCase("Match")) {
        objectiveExists = true;
        break;
      }
    }

    Objective o;
    if (objectiveExists) {
      o = this.sb.getObjective("Match");
      o.setDisplayName("Match");
    } else {
      o = this.sb.registerNewObjective("Match", "dummy");
      o.setDisplaySlot(DisplaySlot.SIDEBAR);
      o.setDisplayName(Lang.SCOREBOARD_TITLE.replace(null));
    }

    String scoreboardTimeLeft = Lang.SCOREBOARD_TIMELEFT.replace(null);
    String scoreboardRedTeam = Lang.SCOREBOARD_RED_TEAM.replace(null);
    String scoreboardBlueTeam = Lang.SCOREBOARD_BLUE_TEAM.replace(null);
    this.time = o.getScore(scoreboardTimeLeft);
    this.time.setScore(300);
    this.redGoals = o.getScore(scoreboardRedTeam);
    this.redGoals.setScore(0);
    this.blueGoals = o.getScore(scoreboardBlueTeam);
    this.blueGoals.setScore(0);

    this.x = Math.abs(b.getX() - r.getX()) > Math.abs(b.getZ() - r.getZ());
    if (this.x) {
      this.redAboveBlue = r.getX() > b.getX();
    } else {
      this.redAboveBlue = r.getZ() > b.getZ();
    }

    ConfigManager configManager = fcManager.getConfigManager();
    this.config = configManager.getConfig("config.yml");
  }

  public boolean equals(Match m) {
    return m.matchID == this.matchID;
  }

  private ItemStack createColoredArmour(Material material, Color color) {
    ItemStack is = new ItemStack(material);
    if (is.getItemMeta() instanceof LeatherArmorMeta) {
      LeatherArmorMeta meta = (LeatherArmorMeta)is.getItemMeta();
      meta.setColor(color);
      is.setItemMeta(meta);
    }

    return is;
  }

  public void join(Player p, boolean b) {
    PlayerData data = fcManager.getDataManager().get(p);
    fcManager.getDataManager().addDefaults(data);

    if (this.redPlayers.size() < this.type && !b) {
      this.redPlayers.add(p);
      this.isRed.put(p, true);
      p.teleport(this.red);
      logger.send(p, Lang.WELCOME.replace(new String[]{Lang.RED.replace(null)}));
    } else if (this.bluePlayers.size() < this.type) {
      this.bluePlayers.add(p);
      this.isRed.put(p, false);
      p.teleport(this.blue);
      logger.send(p, Lang.WELCOME.replace(new String[]{Lang.BLUE.replace(null)}));
    }

    if (this.phase < 2 && this.bluePlayers.size() == this.type && this.redPlayers.size() == this.type) {
      this.phase = 2;
      this.countdown = 15;
      this.tickToSec = 20;
      this.organization.matchStart(this.type);

      for (Player player : this.isRed.keySet()) {
        PlayerData playerData = fcManager.getDataManager().get(player);
        player.setLevel(this.countdown);

        if (this.type != 2) {
          playerData.add("matches");
          player.getInventory().setItem(4, this.sugar);
        }

        if (this.isRed.get(player)) {
          player.getInventory().setChestplate(this.redChestPlate);
          player.getInventory().setLeggings(this.redLeggings);
        } else {
          player.getInventory().setChestplate(this.blueChestPlate);
          player.getInventory().setLeggings(this.blueLeggings);
        }

        logger.send(player, Lang.STARTING.replace(null));
      }
    } else {
      logger.send(p, Lang.TO_LEAVE.replace(null));
    }
  }

  public void leave(Player player) {
    if (player == null) return;
    UUID uuid = player.getUniqueId();

    this.redPlayers.removeIf(player1 -> player1 == null || player1.getUniqueId().equals(uuid));
    this.bluePlayers.removeIf(player1 -> player1 == null || player1.getUniqueId().equals(uuid));
    this.isRed.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().getUniqueId().equals(uuid));
    this.takePlace.removeIf(player1 -> player1 == null || player1.getUniqueId().equals(uuid));
    this.teamers.removeIf(player1 -> player1 == null || player1.getUniqueId().equals(uuid));
    this.goals.keySet().removeIf(player1 -> player1 == null || player1.getUniqueId().equals(uuid));
    this.sugarCooldown.remove(player);

    try {
      player.teleport(config.get("lobby") != null ? (Location) config.get("lobby") : player.getWorld().getSpawnLocation());
    } catch (Exception ignored) {}
    player.setScoreboard(this.sbm.getNewScoreboard());
  }

  public void takePlace(Player p) {
    if (this.phase < 2) {
      logger.send(p, Lang.MATCHES_LIST_NO_MATCHES.replace(null));
      return;
    }

    if (this.isRed.containsKey(p) || this.redPlayers.contains(p) || this.bluePlayers.contains(p)) {
      logger.send(p, "&cVeć ste u meču.");
      return;
    }

    this.takePlace.add(p);

    if (this.redPlayers.size() <= this.bluePlayers.size() && this.redPlayers.size() < this.type) {
      this.redPlayers.add(p);
      this.isRed.put(p, true);
      p.teleport(this.red);
      logger.send(p, Lang.WELCOME.replace(new String[]{Lang.RED.replace(null)}));
    } else if (this.bluePlayers.size() < this.type) {
      this.bluePlayers.add(p);
      this.isRed.put(p, false);
      p.teleport(this.blue);
      logger.send(p, Lang.WELCOME.replace(new String[]{Lang.BLUE.replace(null)}));
    }

    if (this.type != 2) p.getInventory().setItem(4, this.sugar);
    if (this.isRed.containsKey(p) && this.isRed.get(p)) {
      p.getInventory().setChestplate(this.redChestPlate);
      p.getInventory().setLeggings(this.redLeggings);
    } else {
      p.getInventory().setChestplate(this.blueChestPlate);
      p.getInventory().setLeggings(this.blueLeggings);
    }

    if (this.phase > 2) p.setScoreboard(this.sb);
  }

  public void kick(Player p) {
    if (!this.isRed.containsKey(p)) return;

    boolean red = this.isRed.get(p);
    if (red) {
      if (this.lastKickRed != null && !this.lastKickRed.equals(p)) this.assistRed = this.lastKickRed;
      this.lastKickRed = p;
      this.lastKickBlue = null;
      this.assistBlue = null;
    } else {
      if (this.lastKickBlue != null && !this.lastKickBlue.equals(p)) this.assistBlue = this.lastKickBlue;
      this.lastKickBlue = p;
      this.lastKickRed = null;
      this.assistRed = null;
    }
  }

  public boolean isInMatch(Player player) {
    return this.redPlayers.contains(player) || this.bluePlayers.contains(player);
  }

  public boolean canUseTeamChat() {
    return !this.redPlayers.isEmpty() && !this.bluePlayers.isEmpty();
  }

  public void teamChat(Player p, String message) {
    if (this.isRed.get(p)) {
      for (Player player : this.redPlayers) {
        if (player != null) logger.send(player, Lang.TEAMCHAT_RED.replace(new String[]{fcManager.getChat().getPlayerPrefix(p) + p.getName()}) + message);
      }
    } else {
      for (Player player : this.bluePlayers) {
        if (player != null) logger.send(player, Lang.TEAMCHAT_BLUE.replace(new String[]{fcManager.getChat().getPlayerPrefix(p) + p.getName()}) + message);
      }
    }
  }

  public boolean team(Player p0, Player p1) {
    if ((this.redPlayers.size() + this.bluePlayers.size()) < (2 * this.type) - 2 && (this.teams < 2 || this.type != 3 && this.type != 4)) {
      logger.send(p0, Lang.TEAM_SUCCESS_1.replace(new String[]{p1.getName()}));
      logger.send(p1, Lang.TEAM_SUCCESS_1.replace(new String[]{p0.getName()}));
      this.teamers.add(p0);
      this.teamers.add(p1);
      ++this.teams;
      if ((this.type - this.redPlayers.size()) >= 2) {
        this.join(p0, false);
        this.join(p1, false);
      } else if ((this.type - this.bluePlayers.size()) >= 2) {
        this.join(p0, true);
        this.join(p1, true);
      } else {
        boolean rare = true;

        Iterator<Player> bluePlayersIterator = this.bluePlayers.iterator();
        while (bluePlayersIterator.hasNext()) {
          Player p = bluePlayersIterator.next();
          if (!this.teamers.contains(p)) {
            bluePlayersIterator.remove();
            this.redPlayers.add(p);
            this.isRed.put(p, true);
            p.teleport(this.red);
            logger.send(p, Lang.TEAM_SWITCH.replace(new String[]{p0.getName(), p1.getName()}));
            this.join(p0, true);
            this.join(p1, true);
            rare = false;
            break;
          }
        }

        if (rare) {
          Iterator<Player> redPlayersIterator = this.redPlayers.iterator();
          while (redPlayersIterator.hasNext()) {
            Player p = redPlayersIterator.next();
            if (!this.teamers.contains(p)) {
              redPlayersIterator.remove();
              this.bluePlayers.add(p);
              this.isRed.put(p, true);
              p.teleport(this.blue);
              logger.send(p, Lang.TEAM_SWITCH.replace(new String[]{p0.getName(), p1.getName()}));
              this.join(p0, false);
              this.join(p1, false);
              break;
            }
          }
        }
      }

      return true;
    } else {
      return false;
    }
  }

  public void update() {
    --this.tickToSec;

    if (this.phase == 3) {
      if (this.cube == null) return;

      Location l = this.cube.getLocation();
      if (this.x) {
        if ((this.redAboveBlue && l.getBlockX() >= this.red.getBlockX() || !this.redAboveBlue && this.red.getBlockX() >= l.getBlockX()) && l.getY() < this.red.getY() + (double)3 && l.getZ() < this.red.getZ() + (double)4 && l.getZ() > this.red.getZ() - (double)4) {
          this.score(false);
        } else if ((this.redAboveBlue && l.getBlockX() <= this.blue.getBlockX() || !this.redAboveBlue && this.blue.getBlockX() <= l.getBlockX()) && l.getY() < this.blue.getY() + (double)3 && l.getZ() < this.blue.getZ() + (double)4 && l.getZ() > this.blue.getZ() - (double)4) {
          this.score(true);
        }
      } else if ((this.redAboveBlue && l.getBlockZ() >= this.red.getBlockZ() || !this.redAboveBlue && this.red.getBlockZ() >= l.getBlockZ()) && l.getY() < this.red.getY() + (double)3 && l.getX() < this.red.getX() + (double)4 && l.getX() > this.red.getX() - (double)4) {
        this.score(false);
      } else if ((this.redAboveBlue && l.getBlockZ() <= this.blue.getBlockZ() || !this.redAboveBlue && this.blue.getBlockZ() <= l.getBlockZ()) && l.getY() < this.blue.getY() + (double)3 && l.getX() < this.blue.getX() + (double)4 && l.getX() > this.blue.getX() - (double)4) {
        this.score(true);
      }
    }

    if ((this.phase == 2 || this.phase == 4) && this.tickToSec == 0) {
      --this.countdown;
      this.tickToSec = 20;

      for(Player p : this.isRed.keySet()) {
        p.setLevel(this.countdown);
      }

      if (this.countdown <= 0) {
        String message;
        if (this.phase == 2) {
          message = Lang.MATCH_STARTED.replace(null);
          this.startTime = System.currentTimeMillis();
          this.redGoals.setScore(0);
          this.blueGoals.setScore(0);

          for(Player p : this.isRed.keySet()) {
            this.organization.playerStarts(p);
            p.setScoreboard(this.sb);
          }
        } else {
          message = Lang.MATCH_PROCEED.replace(null);
        }

        this.phase = 3;
        this.cube = PhysicsUtil.spawnCube(this.mid);
        Random random = new Random();
        double vertical = 0.3 * random.nextDouble() + 0.2;
        double horizontal = 0.3 * random.nextDouble() + 0.3;
        if (random.nextBoolean()) {
          horizontal *= -1;
        }

        if (this.x) {
          this.cube.setVelocity(new Vector(0, vertical, horizontal));
        } else {
          this.cube.setVelocity(new Vector(horizontal, vertical, 0));
        }

        for (Player p : this.isRed.keySet()) {
          logger.send(p, message);
          if (this.isRed.containsKey(p) && this.isRed.get(p)) {
            p.teleport(this.red);
          } else {
            p.teleport(this.blue);
          }

          p.playSound(p.getLocation(), Sound.EXPLODE, 1, 1);
        }
      } else if (this.countdown <= 10) {
        for (Player p : this.isRed.keySet()) {
          p.playSound(p.getLocation(), Sound.NOTE_STICKS, 1, 1);
        }
      }
    }

    if (this.phase == 3 && this.cube.isDead()) {
      this.cube = PhysicsUtil.spawnCube(this.mid);
      Random random = new Random();
      double vertical = 0.3 * random.nextDouble() + 0.2;
      double horizontal = 0.3 * random.nextDouble() + 0.3;
      if (random.nextBoolean()) {
        horizontal *= -1;
      }

      if (this.x) {
        this.cube.setVelocity(new Vector(0, vertical, horizontal));
      } else {
        this.cube.setVelocity(new Vector(horizontal, vertical, 0));
      }

      for(Player p : this.isRed.keySet()) {
        logger.send(p, Lang.CLEARED_CUBE_INGAME.replace(null));
        if (this.isRed.containsKey(p) && this.isRed.get(p)) {
          p.teleport(this.red);
        } else {
          p.teleport(this.blue);
        }

        p.playSound(p.getLocation(), Sound.EXPLODE, 1, 1);
      }
    }

    if (this.type == 2) {
      this.time.setScore(120 - (int)(System.currentTimeMillis() - this.startTime) / 1000);
    } else {
      this.time.setScore(300 - (int) (System.currentTimeMillis() - this.startTime) / 1000);
    }

    if (this.time.getScore() <= 0 && this.phase > 2) {
      for (Player p : this.isRed.keySet()) {
        PlayerData data = fcManager.getDataManager().get(p);
        this.organization.endMatch(p);
        p.setScoreboard(this.sbm.getNewScoreboard());
        p.teleport(config.get("lobby") != null ? (Location) config.get("lobby") : p.getWorld().getSpawnLocation());
        this.organization.clearInventory(p);
        if (this.scoreRed > this.scoreBlue) {
          logger.send(p, Lang.MATCH_TIMES_UP.replace(new String[]{Lang.RED.replace(null)}));
          if (this.isRed.get(p) && !this.takePlace.contains(p)) {
            if (this.type != 2) {
              data.add("wins");
              data.add("winstreak");
              if ((int) data.get("winstreak") > (int) data.get("bestwinstreak")) {
                data.set("bestwinstreak", data.get("winstreak"));
              }
            }

            fcManager.getEconomy().depositPlayer(p.getPlayer(), 15);
            logger.send(p, Lang.MATCH_WIN_CREDITS.replace(null));
            if ((int) data.get("winstreak") % 5 == 0) {
              fcManager.getEconomy().depositPlayer(p.getPlayer(), 100);
              logger.send(p, Lang.MATCH_WINSTREAK_CREDITS.replace(new String[]{String.valueOf(data.get("winstreak"))}));
            }
            p.setExp(0);
          } else {
            if (this.type != 2) {
              data.set("winstreak", 0);
            }
          }
        } else if (this.scoreRed < this.scoreBlue) {
          logger.send(p, Lang.MATCH_TIMES_UP.replace(new String[]{Lang.BLUE.replace(null)}));
          if (!(Boolean)this.isRed.get(p) && !this.takePlace.contains(p)) {
            if (this.type != 2) {
              data.add("wins");
              data.add("winstreak");
              if ((int) data.get("winstreak") > (int) data.get("bestwinstreak")) {
                data.set("bestwinstreak", data.get("winstreak"));
              }
            }

            fcManager.getEconomy().depositPlayer(p.getPlayer(), 15);
            logger.send(p, Lang.MATCH_WIN_CREDITS.replace(null));
            if ((int) data.get("winstreak") % 5 == 0) {
              fcManager.getEconomy().depositPlayer(p.getPlayer(), 100);
              logger.send(p, Lang.MATCH_WINSTREAK_CREDITS.replace(new String[]{String.valueOf(data.get("winstreak"))}));
            }
          } else {
            if (this.type != 2) {
              data.set("winstreak", 0);
            }
          }
        } else {
          logger.send(p, Lang.MATCH_TIED.replace(null));
          if (!this.takePlace.contains(p)) {
            if (this.type != 2) {
              data.add("ties");
              data.set("winstreak", 0);
            }
            fcManager.getEconomy().depositPlayer(p.getPlayer(), 5);
            logger.send(p, Lang.MATCH_TIED_CREDITS.replace(null));
          }
        }
      }

      this.phase = 1;
      this.cube.setHealth(0F);
      this.organization.undoTakePlace(this);
      this.scoreRed = 0;
      this.scoreBlue = 0;
      this.teams = 0;
      this.redPlayers.clear();
      this.bluePlayers.clear();
      this.teamers = new ArrayList<>();
      this.isRed = new HashMap<>();
      this.takePlace.clear();
      this.goals.clear();
      this.sugarCooldown.clear();
    }

    long cutoff = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(40);
    this.sugarCooldown.entrySet().removeIf(entry -> {
      Player player = entry.getKey();
      boolean expired = cutoff > entry.getValue();
      if (expired && organization.isInGame(player)) player.getInventory().setItem(4, this.sugar);
      return expired;
    });
  }

  private void score(boolean red) {
    this.phase = 4;
    this.tickToSec = 20;
    this.countdown = 5;
    this.cube.setHealth(0);

    Player assister = red ? this.assistRed : this.assistBlue;
    String scoringTeamName = red ? Lang.RED.replace(null) : Lang.BLUE.replace(null);

    if (red) {
      ++this.scoreRed;
      this.redGoals.setScore(this.redGoals.getScore() + 1);
    } else {
      ++this.scoreBlue;
      this.blueGoals.setScore(this.blueGoals.getScore() + 1);
    }

    boolean ownGoal = false;
    Player playerResponsible = null;
    if (red && this.lastKickRed != null) {
      playerResponsible = this.lastKickRed;
    } else if (!red && this.lastKickBlue != null) {
      playerResponsible = this.lastKickBlue;
    }

    if (playerResponsible == null) {
      if (red && this.lastKickBlue != null) {
        playerResponsible = this.lastKickBlue;
      } else if (!red && this.lastKickRed != null) {
        playerResponsible = this.lastKickRed;
      }
    }

    if (playerResponsible != null) {
      boolean lastKickerIsRed = this.isRed.getOrDefault(playerResponsible, false);
      ownGoal = (lastKickerIsRed != red);
    }

    Player scorer = playerResponsible;
    if (scorer == null) {
      List<Player> winningTeam = red ? this.redPlayers : this.bluePlayers;
      if (!winningTeam.isEmpty()) scorer = winningTeam.get(new Random().nextInt(winningTeam.size()));
    }

    if (scorer == null) return;

    if (playerResponsible != null) {
      if (ownGoal) {
        PlayerData ownGoalData = fcManager.getDataManager().get(playerResponsible);
        int ogCount = ownGoals.getOrDefault(playerResponsible.getUniqueId(), 0) + 1;
        ownGoals.put(playerResponsible.getUniqueId(), ogCount);
        ownGoalData.add("owngoals");

        fcManager.getEconomy().withdrawPlayer(playerResponsible, 200);
        logger.send(playerResponsible, Lang.MATCH_SCORE_OWN_GOAL.replace(null));

        if (ogCount >= 3) {
          MatchHelper.leaveMatch(organization, playerResponsible, this, logger, true);

          long banUntil = System.currentTimeMillis() + (30 * 60 * 1000);
          organization.getLeaveCooldowns().put(playerResponsible.getUniqueId(), banUntil);

          for (Player player : this.isRed.keySet()) logger.send(player, Lang.MATCH_SCORE_OWN_GOAL_ANNOUNCE.replace(new String[]{playerResponsible.getDisplayName()}));
        }
      }

      PlayerData data = fcManager.getDataManager().get(playerResponsible);
      if (data != null && !this.takePlace.contains(playerResponsible) && !ownGoal) {
        if (this.type != 2) data.add("goals");
        fcManager.getEconomy().depositPlayer(playerResponsible.getPlayer(), 10);
        this.goals.put(playerResponsible, this.goals.getOrDefault(playerResponsible, 0) + 1);

        logger.send(playerResponsible, Lang.MATCH_SCORE_CREDITS.replace(null));

        if (assister != null && assister != playerResponsible && this.isRed.getOrDefault(assister, false) == this.isRed.getOrDefault(playerResponsible, false)) {
          PlayerData assistData = fcManager.getDataManager().get(assister);
          if (assistData != null && this.type != 2) assistData.add("assists");
          fcManager.getEconomy().depositPlayer(assister, 5);
          logger.send(assister, Lang.MATCH_ASSIST_CREDITS.replace(null));
        }

        if (this.goals.get(playerResponsible) == 3) {
          logger.send(playerResponsible, Lang.MATCH_SCORE_HATTRICK.replace(null));
          fcManager.getEconomy().depositPlayer(playerResponsible.getPlayer(), 100);
        }
      }
    }

    for (Player p : this.isRed.keySet()) {
      Location goalLocation = red ? this.blue : this.red;
      double distanceToGoal = scorer.getLocation().distance(goalLocation);

      String displayPrefixName = (playerResponsible != null)
          ? fcManager.getChat().getPlayerPrefix(playerResponsible) + playerResponsible.getName()
          : fcManager.getChat().getPlayerPrefix(scorer) + scorer.getName();

      String goalMessage = ownGoal
          ? Lang.MATCH_SCORE_OWN_GOAL_ANNOUNCE.replace(new String[]{displayPrefixName, scoringTeamName})
          : Lang.MATCH_GOAL.replace(new String[]{
          displayPrefixName,
          scoringTeamName,
          String.format("%.0f", distanceToGoal),
          assister != null && assister != playerResponsible ? Lang.MATCH_GOAL_ASSIST.replace(new String[]{fcManager.getChat().getPlayerPrefix(assister) + assister.getName()}) : ""
      });

      logger.send(p, goalMessage);
      PlayerSettings settings = fcManager.getPlayerSettings(p);
      if (settings.isGoalSoundEnabled()) p.playSound(p.getLocation(), settings.getGoalSound(), 1, 1);

      String part = red ? "&c" : "&9";
      fcManager.getLogger().title(p, (ownGoal ? Lang.MATCH_OWN_GOAL_TITLE.replace(null) : Lang.MATCH_GOAL_TITLE.replace(null)),
          Lang.MATCH_GOAL_SUBTITLE.replace(new String[]{
              part + displayPrefixName + part
          }), 10, 30, 10);

      if (this.time.getScore() > 0) {
        p.setLevel(10);
        logger.send(p, Lang.MATCH_SCORE_STATS.replace(new String[]{String.valueOf(this.scoreRed), String.valueOf(this.scoreBlue)}));
      } else {
        this.organization.endMatch(p);
        p.setScoreboard(this.sbm.getNewScoreboard());

        PlayerData playerData = fcManager.getDataManager().get(p);
        boolean playerIsRed = this.isRed.getOrDefault(p, false);

        if (playerData != null && playerIsRed == red && !this.takePlace.contains(p)) {
          if (this.type != 2) {
            playerData.add("wins");
            playerData.add("winstreak");
            if ((int) playerData.get("winstreak") > (int) playerData.get("bestwinstreak")) {
              playerData.set("bestwinstreak", playerData.get("winstreak"));
            }
          }

          fcManager.getEconomy().depositPlayer(p.getPlayer(), 15);
          logger.send(p, Lang.MATCH_WIN_CREDITS.replace(null));
          if ((int) playerData.get("winstreak") % 5 == 0) {
            fcManager.getEconomy().depositPlayer(p.getPlayer(), 100);
            logger.send(p, Lang.MATCH_WINSTREAK_CREDITS.replace(new String[]{String.valueOf(playerData.get("winstreak"))}));
          }
        } else if (playerData != null && !this.takePlace.contains(p) && this.type != 2) {
          playerData.set("winstreak", 0);
        }

        logger.send(p, Lang.MATCH_WIN_TEAM.replace(new String[]{scoringTeamName}));
        p.teleport(config.get("lobby") != null ? (Location) config.get("lobby") : p.getWorld().getSpawnLocation());
        this.organization.clearInventory(p);
      }
    }

    if (this.time.getScore() <= 0) {
      this.phase = 1;
      this.organization.undoTakePlace(this);
      this.scoreRed = 0;
      this.scoreBlue = 0;
      this.teams = 0;
      this.redPlayers.clear();
      this.bluePlayers.clear();
      this.teamers.clear();
      this.isRed.clear();
      this.takePlace.clear();
      this.goals.clear();
      this.sugarCooldown.clear();
    }
  }

  public void cleanup() {
    try {
      if (this.cube != null && !this.cube.isDead()) {
        this.cube.remove();
        this.cube = null;
      }
    } catch (Exception ignored) {}

    try {
      for (Player p : new ArrayList<>(this.isRed.keySet())) {
        try {
          p.setScoreboard(this.sbm.getNewScoreboard());
          Location lobby = config.get("lobby") != null ? (Location) config.get("lobby") : p.getWorld().getSpawnLocation();
          p.teleport(lobby);
          organization.clearInventory(p);
          p.setLevel(0);
          logger.send(p, Lang.RELOAD.replace(null));
        } catch (Exception ignored) {}
      }
    } catch (Exception ignored) {}

    try {
      if (this.sb != null) {
        for (Objective obj : this.sb.getObjectives()) {
          try {
            obj.unregister();
          } catch (Exception ignored) {}
        }
      }
    } catch (Exception ignored) {}

    try {
      this.scoreRed = 0;
      this.scoreBlue = 0;
      this.teams = 0;
      this.redPlayers.clear();
      this.bluePlayers.clear();
      this.teamers.clear();
      this.takePlace.clear();
      this.isRed.clear();
      this.goals.clear();
      this.ownGoals.clear();
      this.sugarCooldown.clear();
      this.lastKickRed = null;
      this.lastKickBlue = null;
      this.assistRed = null;
      this.assistBlue = null;
    } catch (Exception ignored) {}
  }
}