package io.github.divinerealms.footcube.matchmaking.util;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.managers.Utilities;
import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.MatchPhase;
import io.github.divinerealms.footcube.matchmaking.player.TeamColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MatchUtils {

  public static void giveArmor(Player player, TeamColor color) {
    ItemStack chestplate = createColoredArmor(Material.LEATHER_CHESTPLATE, color == TeamColor.RED ? Color.RED : Color.BLUE);
    ItemStack leggings = createColoredArmor(Material.LEATHER_LEGGINGS, color == TeamColor.RED ? Color.RED : Color.BLUE);

    PlayerInventory inventory = player.getInventory();
    inventory.setChestplate(chestplate);
    inventory.setLeggings(leggings);
  }

  public static ItemStack createColoredArmor(Material material, org.bukkit.Color color) {
    ItemStack is = new ItemStack(material);
    ItemMeta meta = is.getItemMeta();
    if (meta instanceof LeatherArmorMeta) {
      LeatherArmorMeta leatherMeta = (LeatherArmorMeta) meta;
      leatherMeta.setColor(color);
      is.setItemMeta(leatherMeta);
    }
    return is;
  }

  public static void clearPlayer(Player player) {
    if (player == null) return;
    player.getInventory().setArmorContents(null);
    player.getInventory().clear();
    player.setExp(0);
    player.setLevel(0);
  }

  public static List<String> getFormattedMatches(List<Match> matches) {
    List<String> output = new ArrayList<>();
    boolean firstBlock = true;

    for (Match match : matches) {
      if (match.getPlayers().stream().allMatch(Objects::isNull)) continue;

      if (!firstBlock) output.add("");
      firstBlock = false;

      String type = match.getArena().getType() + "v" + match.getArena().getType();
      List<String> redPlayers = match.getPlayers().stream()
          .filter(Objects::nonNull)
          .filter(p -> p.getPlayer() != null && p.getTeamColor() == TeamColor.RED)
          .map(p -> p.getPlayer().getName())
          .collect(Collectors.toList());
      List<String> bluePlayers = match.getPlayers().stream()
          .filter(Objects::nonNull)
          .filter(p -> p.getPlayer() != null && p.getTeamColor() == TeamColor.BLUE)
          .map(p -> p.getPlayer().getName())
          .collect(Collectors.toList());
      List<String> waitingPlayers = match.getPlayers().stream()
          .filter(Objects::nonNull)
          .filter(p -> p.getPlayer() != null && p.getTeamColor() == null)
          .map(p -> p.getPlayer().getName())
          .collect(Collectors.toList());

      String timeDisplay;
      if (match.getPhase() == MatchPhase.LOBBY) {
        timeDisplay = Lang.MATCHES_LIST_WAITING.replace(null);
      } else if (match.getPhase() == MatchPhase.STARTING || match.getPhase() == MatchPhase.CONTINUING) {
        timeDisplay = Lang.MATCHES_LIST_STARTING.replace(new String[]{String.valueOf(match.getCountdown())});
      } else {
        long matchDuration = match.getArena().getType() == MatchConstants.TWO_V_TWO ? 120 : 300;
        long elapsedMillis = (System.currentTimeMillis() - match.getStartTime()) - match.getTotalPausedTime();
        long remainingSeconds = matchDuration - TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);

        timeDisplay = Utilities.formatTimePretty((int) remainingSeconds);
      }

      if (match.getPhase() == MatchPhase.LOBBY) {
        output.add(Lang.MATCHES_LIST_LOBBY.replace(new String[]{type, String.valueOf(match.getArena().getId())}));
        output.add(Lang.MATCHES_LIST_WAITINGPLAYERS.replace(new String[]{
            waitingPlayers.isEmpty() ? "/" : String.join(", ", waitingPlayers)
        }));
        output.add(Lang.MATCHES_LIST_STATUS.replace(new String[]{timeDisplay}));
      } else if (match.getPhase() == MatchPhase.STARTING) {
        output.add(Lang.MATCHES_LIST_LOBBY.replace(new String[]{type, String.valueOf(match.getArena().getId())}));
        output.add(Lang.MATCHES_LIST_REDPLAYERS.replace(new String[]{
            redPlayers.isEmpty() ? "/" : String.join(", ", redPlayers)
        }));
        output.add(Lang.MATCHES_LIST_BLUEPLAYERS.replace(new String[]{
            bluePlayers.isEmpty() ? "/" : String.join(", ", bluePlayers)
        }));
        output.add(Lang.MATCHES_LIST_STATUS.replace(new String[]{timeDisplay}));
      } else {
        output.add(Lang.MATCHES_LIST_MATCH.replace(new String[]{type, String.valueOf(match.getArena().getId())}));
        output.add(Lang.MATCHES_LIST_RESULT.replace(new String[]{String.valueOf(match.getScoreRed()), String.valueOf(match.getScoreBlue()),
            Lang.MATCHES_LIST_TIMELEFT.replace(new String[]{timeDisplay})}));
        output.add(Lang.MATCHES_LIST_REDPLAYERS.replace(new String[]{redPlayers.isEmpty() ? "/" : String.join(", ", redPlayers)}));
        output.add(Lang.MATCHES_LIST_BLUEPLAYERS.replace(new String[]{bluePlayers.isEmpty() ? "/" : String.join(", ", bluePlayers)}));
      }
    }
    return output;
  }
}
