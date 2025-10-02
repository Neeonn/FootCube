package io.github.divinerealms.footcube.managers;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class Utilities {
  private static Plugin plugin;
  public LuckPerms luckPerms;

  public Utilities(FCManager fcManager) {
    plugin = fcManager.getPlugin();
    this.luckPerms = fcManager.getLuckPerms();
  }

  public static String color(String string) {
    return ChatColor.translateAlternateColorCodes('&', string);
  }

  public CompletableFuture<String> getPrefixedName(UUID uuid,  String playerName) {
    return luckPerms.getUserManager().loadUser(uuid).thenApplyAsync(user -> {
      CachedMetaData meta = user.getCachedData().getMetaData();
      String prefix = meta.getPrefix();
      if (prefix == null) prefix = "";
      return ChatColor.translateAlternateColorCodes('&', prefix + playerName);
    });
  }

  public static int parseTime(String input) throws NumberFormatException {
    int totalSeconds = 0;
    input = input.toLowerCase().replaceAll("\\s+", ""); // remove spaces
    StringBuilder number = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (Character.isDigit(c)) number.append(c);
      else if (c == 'm') { // check "min"
        if (i + 2 < input.length() && input.startsWith("min", i)) {
          totalSeconds += Integer.parseInt(number.toString()) * 60;
          number.setLength(0);
          i += 2;
        } else throw new NumberFormatException("Invalid time format");
      } else if (c == 's') {
        totalSeconds += Integer.parseInt(number.toString());
        number.setLength(0);
      } else throw new NumberFormatException("Invalid time format");
    }
    if (number.length() > 0) totalSeconds += Integer.parseInt(number.toString());
    return totalSeconds;
  }

  public static void sendParticle(Player player, EnumParticle particle, Location loc, float offsetX, float offsetY, float offsetZ, float speed, int count) {
    sendParticle(player, particle, loc, offsetX, offsetY, offsetZ, speed, count, null);
  }

  public static void sendParticle(Player player, EnumParticle particle, Location loc, float offsetX, float offsetY, float offsetZ, float speed, int count, Color color) {
    PacketPlayOutWorldParticles packet;
    try {
      if (particle == EnumParticle.REDSTONE && color != null) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        packet = new PacketPlayOutWorldParticles(particle, true, (float) loc.getX(), (float) loc.getY(), (float) loc.getZ(), r, g, b, 1.0f, 0);
      } else if (PlayerSettings.DISALLOWED_PARTICLES.contains(particle)) {
        return;
      } else {
        packet = new PacketPlayOutWorldParticles(particle, true, (float) loc.getX(), (float) loc.getY(), (float) loc.getZ(), offsetX, offsetY, offsetZ, speed, count);
      }

      ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    } catch (Exception exception) {
      plugin.getLogger().log(Level.SEVERE, "Error while trying to send particle", exception);
    }
  }
}
