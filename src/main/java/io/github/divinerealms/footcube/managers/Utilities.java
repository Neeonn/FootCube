package io.github.divinerealms.footcube.managers;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class Utilities {

  private static Plugin plugin;
  private final FCManager fcManager;
  public LuckPerms luckPerms;

  public Utilities(FCManager fcManager) {
    plugin = fcManager.getPlugin();
    this.fcManager = fcManager;
    this.luckPerms = fcManager.getLuckPerms();
  }

  public static int parseTime(String input) throws NumberFormatException {
    int totalSeconds = 0;
    input = input.toLowerCase().replaceAll("\\s+", ""); // remove spaces
    StringBuilder number = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (Character.isDigit(c)) {
        number.append(c);
      } else {
        if (c == 'm') { // check "min"
          if (i + 2 < input.length() && input.startsWith("min", i)) {
            totalSeconds += Integer.parseInt(number.toString()) * 60;
            number.setLength(0);
            i += 2;
          } else {
            throw new NumberFormatException("Invalid time format");
          }
        } else {
          if (c == 's') {
            totalSeconds += Integer.parseInt(number.toString());
            number.setLength(0);
          } else {
            throw new NumberFormatException("Invalid time format");
          }
        }
      }
    }

    if (number.length() > 0) {
      totalSeconds += Integer.parseInt(number.toString());
    }

    return totalSeconds;
  }

  public static String formatTimePretty(int totalSeconds) {
    int minutes = totalSeconds / 60;
    int seconds = totalSeconds % 60;
    return String.format("%02d:%02d", minutes, seconds);
  }

  public static String formatTime(long totalSeconds) {
    if (totalSeconds <= 0) {
      return "0s";
    }

    final long SECONDS_IN_MINUTE = 60;
    final long SECONDS_IN_HOUR = 60 * SECONDS_IN_MINUTE;
    final long SECONDS_IN_DAY = 24 * SECONDS_IN_HOUR;

    final long SECONDS_IN_MONTH = 30 * SECONDS_IN_DAY;
    final long SECONDS_IN_YEAR = 365 * SECONDS_IN_DAY;

    long seconds = totalSeconds;

    long years = seconds / SECONDS_IN_YEAR;
    seconds %= SECONDS_IN_YEAR;

    long months = seconds / SECONDS_IN_MONTH;
    seconds %= SECONDS_IN_MONTH;

    long hours = seconds / SECONDS_IN_HOUR;
    seconds %= SECONDS_IN_HOUR;

    long minutes = seconds / SECONDS_IN_MINUTE;
    seconds %= SECONDS_IN_MINUTE;

    StringBuilder sb = new StringBuilder();

    if (years > 0) {
      sb.append(years).append("y");
    }

    if (months > 0) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(months).append("mo");
    }

    if (hours > 0) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(hours).append("h");
    }

    if (minutes > 0) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(minutes).append("min");
    }

    if (seconds > 0 || sb.length() == 0) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(seconds).append("s");
    }

    return sb.toString();
  }

  public static void sendParticle(Player player, EnumParticle particle,
      double x, double y, double z,
      float offsetX, float offsetY, float offsetZ,
      float speed, int count) {
    if (PlayerSettings.DISALLOWED_PARTICLES.contains(particle)) {
      return;
    }

    try {
      PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(particle, true,
          (float) x, (float) y, (float) z,
          offsetX, offsetY, offsetZ, speed, count);
      ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    } catch (Exception exception) {
      plugin.getLogger().log(Level.SEVERE, "Error while trying to send particle", exception);
    }
  }

  public String getCachedPrefixedName(UUID uuid, String playerName) {
    String cached = fcManager.getPrefixedName(uuid);
    if (cached != null) {
      return cached;
    }

    return playerName;
  }

  public CompletableFuture<String> getPrefixedName(UUID uuid, String playerName) {
    return luckPerms.getUserManager().loadUser(uuid).thenApplyAsync(user -> {
      CachedMetaData meta = user.getCachedData().getMetaData(
          luckPerms.getContextManager().getStaticQueryOptions()
      );

      String prefix = meta.getPrefix();
      if (prefix == null) {
        prefix = "";
      }

      return ChatColor.translateAlternateColorCodes('&', prefix + playerName);
    });
  }
}
