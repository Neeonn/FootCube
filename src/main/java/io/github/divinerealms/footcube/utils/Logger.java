package io.github.divinerealms.footcube.utils;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import lombok.Getter;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.IChatBaseComponent.ChatSerializer;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * The Logger class provides a utility for managing formatted logging and messaging functionalities
 * in a Minecraft server environment. It handles message customization, replacement of placeholders,
 * and formatted broadcasting to players and the console, ensuring consistent and readable output.
 */
public class Logger {
  private final FCManager fcManager;
  private final Server server;
  private final ConsoleCommandSender consoleSender;
  @Getter private final String consolePrefix;

  public Logger(FCManager fcManager) {
    this.fcManager = fcManager;
    this.server = fcManager.getPlugin().getServer();
    this.consoleSender = this.server.getConsoleSender();
    this.consolePrefix = ChatColor.GREEN + "[" + fcManager.getPlugin().getDescription().getName() + "] " + ChatColor.DARK_GREEN;
  }

  /**
   * Logs an informational message to the console. The message is processed to remove
   * specific prefixes and is then displayed with a formatted color scheme.
   *
   * @param message the informational message to be logged. The message can include
   *                placeholders such as "{prefix}" and "{prefix-admin}", which will
   *                be removed before formatting and sending to the console.
   */
  public void info(String message) {
    message = message.replace("{prefix}", "").replace("{prefix-admin}", "");
    consoleSender.sendMessage(consolePrefix + color(message));
  }

  /**
   * Sends a formatted message to a specified {@link CommandSender}. If the sender is a player, placeholders
   * such as "{prefix}" and "{prefix-admin}" will be replaced and the message will be sent in a colored format.
   * Otherwise, the message will be sent to the console with appropriate formatting.
   *
   * @param sender  the recipient of the message, can be a player or the console
   * @param message the message to send, which may include placeholders such as "{prefix}" and "{prefix-admin}"
   */
  public void send(CommandSender sender, String message) {
    if (sender instanceof Player) {
      message = message.replace("{prefix}", Lang.PREFIX.replace(null)).replace("{prefix-admin}", Lang.PREFIX_ADMIN.replace(null));
      sender.sendMessage(color(message));
    } else {
      message = message.replace("{prefix}", "").replace("{prefix-admin}", "");
      consoleSender.sendMessage(consolePrefix + color(message));
    }
  }

  /**
   * Sends a formatted message to all players with a specific permission and logs it to the console.
   * The message can include placeholders for dynamic content, such as "{prefix}" and "{prefix-admin}".
   * These placeholders will be replaced with corresponding values before sending.
   *
   * @param permission the permission required for players to receive the message
   * @param message    the message to send, including optional placeholders such as "{prefix}"
   *                   and "{prefix-admin}" to be replaced with predefined values
   */
  public void send(String permission, String message) {
    String formattedMc = message.replace("{prefix}", Lang.PREFIX.replace(null)).replace("{prefix-admin}", Lang.PREFIX_ADMIN.replace(null));
    String formattedConsole = message.replace("{prefix}", "").replace("{prefix-admin}", "");

    server.broadcast(color(formattedMc), permission);
    consoleSender.sendMessage(consolePrefix + color(formattedConsole));
  }

  /**
   * Sends a formatted message to all players within a specified radius, who have the given permission,
   * and logs the message to the console. The message can include placeholders for dynamic content, such as
   * "{prefix}" and "{prefix-admin}", which will be replaced with predefined values.
   *
   * @param permission the permission required for players to receive the message
   * @param center     the center location to define the area for sending the message
   * @param radius     the radius around the center within which players will receive the message
   * @param message    the message to send, including optional placeholders such as "{prefix}" and "{prefix-admin}"
   */
  public void send(String permission, Location center, double radius, String message) {
    if (center == null || radius <= 0) return;

    String formattedMc = message.replace("{prefix}", Lang.PREFIX.replace(null)).replace("{prefix-admin}", Lang.PREFIX_ADMIN.replace(null));
    String formattedConsole = message.replace("{prefix}", "").replace("{prefix-admin}", "");

    double radiusSquared = radius * radius;
    for (Player player : fcManager.getCachedPlayers()) {
      if (player.getWorld() != center.getWorld()) continue;
      if (!player.hasPermission(permission)) continue;
      if (player.getLocation().distanceSquared(center) > radiusSquared) continue;

      player.sendMessage(color(formattedMc));
    }

    consoleSender.sendMessage(consolePrefix + color(formattedConsole));
  }

  /**
   * Sends a broadcast message to all players on the server. The message can include
   * placeholders such as "{prefix}" and "{prefix-admin}", which will be replaced
   * with predefined values before broadcasting. The message is also formatted for color.
   *
   * @param message the message to be broadcasted, including optional placeholders
   *                such as "{prefix}" and "{prefix-admin}" to be replaced and formatted.
   */
  public void broadcast(String message) {
    message = message.replace("{prefix}", Lang.PREFIX.replace(null)).replace("{prefix-admin}", Lang.PREFIX_ADMIN.replace(null));
    server.broadcastMessage(color(message));
  }

  /**
   * Sends an action bar message to the specified player. The message may include placeholders
   * such as "{prefix}" and "{prefix-admin}", which will be replaced with predefined values.
   * The message is automatically formatted for color before being sent.
   *
   * @param player  the player to whom the action bar message will be sent
   * @param message the message to send, which may include placeholders like "{prefix}" and
   *                "{prefix-admin}" for dynamic content replacement
   */
  public void sendActionBar(Player player, String message) {
    message = color(message.replace("{prefix}", Lang.PREFIX.replace(null)).replace("{prefix-admin}", Lang.PREFIX_ADMIN.replace(null)));
    IChatBaseComponent iChatBaseComponent = ChatSerializer.a("{\"text\": \"" + message + "\"}");
    PacketPlayOutChat packetPlayOutChat = new PacketPlayOutChat(iChatBaseComponent, (byte)2);
    ((CraftPlayer)player).getHandle().playerConnection.sendPacket(packetPlayOutChat);
  }

  /**
   * Sends a formatted action bar message to all cached players on the server. The message
   * can include placeholders such as "{prefix}" and "{prefix-admin}", which will be replaced
   * with predefined values before broadcasting. The message is also formatted with color.
   *
   * @param message the message to be broadcasted in the action bar, including optional
   *                placeholders such as "{prefix}" and "{prefix-admin}" to be replaced
   *                and formatted with color.
   */
  public void broadcastBar(String message) {
    message = color(message.replace("{prefix}", Lang.PREFIX.replace(null)).replace("{prefix-admin}", Lang.PREFIX_ADMIN.replace(null)));
    IChatBaseComponent iChatBaseComponent = ChatSerializer.a("{\"text\": \"" + message + "\"}");
    PacketPlayOutChat packetPlayOutChat = new PacketPlayOutChat(iChatBaseComponent, (byte)2);

    for(Player player : fcManager.getCachedPlayers()) {
      ((CraftPlayer)player).getHandle().playerConnection.sendPacket(packetPlayOutChat);
    }
  }

  /**
   * Sends a title and subtitle to a specified player with customized fade in, stay, and fade out durations.
   * The title and subtitle can include color codes, which will be translated to corresponding colors.
   *
   * @param player  the player to whom the title and subtitle will be sent
   * @param title   the main title text to display, including optional color codes
   * @param fadeIn  the time in ticks for the title and subtitle to fade in
   * @param stay    the time in ticks for the title and subtitle to remain on screen
   * @param fadeOut the time in ticks for the title and subtitle to fade out
   */
  public void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
    title = color(title);
    subtitle = color(subtitle);
    CraftPlayer craftPlayer = (CraftPlayer) player;

    IChatBaseComponent titleJSON = ChatSerializer.a("{\"text\":\"" + title + "\"}");
    IChatBaseComponent subtitleJSON = ChatSerializer.a("{\"text\":\"" + subtitle + "\"}");

    PacketPlayOutTitle titlePacket = new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.TITLE, titleJSON);
    PacketPlayOutTitle subtitlePacket = new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.SUBTITLE, subtitleJSON);
    PacketPlayOutTitle timesPacket = new PacketPlayOutTitle(fadeIn, stay, fadeOut);

    craftPlayer.getHandle().playerConnection.sendPacket(titlePacket);
    craftPlayer.getHandle().playerConnection.sendPacket(subtitlePacket);
    craftPlayer.getHandle().playerConnection.sendPacket(timesPacket);
  }

  /**
   * Translates alternate color codes in the given message using the '&' character
   * as the prefix for color codes.
   *
   * @param message the string containing alternate color codes to be translated
   * @return the formatted string with alternate color codes translated to actual color codes
   */
  public String color(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}
