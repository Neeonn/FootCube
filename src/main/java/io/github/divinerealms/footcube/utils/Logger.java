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

import static io.github.divinerealms.footcube.configs.Lang.PREFIX;
import static io.github.divinerealms.footcube.configs.Lang.PREFIX_ADMIN;

/**
 * The Logger class provides a utility for managing formatted logging and messaging functionalities
 * in a Minecraft server environment. It handles message customization, replacement of placeholders,
 * and formatted broadcasting to players and the console, ensuring consistent and readable output.
 */
public class Logger {
  private final FCManager fcManager;
  private final Server server;
  private final ConsoleCommandSender consoleSender;
  @Getter
  private final String consolePrefix;

  public Logger(FCManager fcManager) {
    this.fcManager = fcManager;
    this.server = fcManager.getPlugin().getServer();
    this.consoleSender = this.server.getConsoleSender();
    this.consolePrefix =
        ChatColor.GREEN + "[" + fcManager.getPlugin().getDescription().getName() + "] " + ChatColor.DARK_GREEN;
  }

  /**
   * Logs an informational message to the console.
   * Accepts either a {@link Lang} entry or a raw string.
   * The message is processed to remove specific prefixes (like {prefix}) to maintain
   * a clean console log while keeping the actual text formatted.
   *
   * @param messageObj the message to be logged (Lang or String)
   * @param args       optional arguments for placeholder replacement (only used with Lang entries)
   */
  public void info(Object messageObj, String... args) {
    String message = formatMessage(messageObj, args);
    consoleSender.sendMessage(consolePrefix + replacePlaceholders(message, true));
  }

  /**
   * Sends a formatted message to a specified {@link CommandSender}.
   * Accepts either a {@link Lang} entry or a raw string.
   * If the sender is a player, the message includes full prefixes. If the sender is the
   * console, prefixes are stripped for better readability.
   *
   * @param sender     the recipient of the message (player or console)
   * @param messageObj the message to send (Lang or String)
   * @param args       optional arguments for placeholder replacement (only used with Lang entries)
   */
  public void send(CommandSender sender, Object messageObj, String... args) {
    String message = formatMessage(messageObj, args);

    if (sender instanceof Player) {
      sender.sendMessage(message);
    } else {
      consoleSender.sendMessage(consolePrefix + replacePlaceholders(message, true));
    }
  }

  /**
   * Sends a formatted message to all players with a specific permission and logs it to the console.
   * Accepts either a {@link Lang} entry or a raw string.
   * Placeholders are automatically handled when using Lang entries.
   *
   * @param permission the permission required for players to receive the message
   * @param messageObj the message to send (Lang or String)
   * @param args       optional arguments for placeholder replacement (only used with Lang entries)
   */
  public void send(String permission, Object messageObj, String... args) {
    String formatted = formatMessage(messageObj, args);

    server.broadcast(formatted, permission);
    consoleSender.sendMessage(consolePrefix + replacePlaceholders(formatted, true));
  }

  /**
   * Sends a formatted message to all players within a specified radius who have the given
   * permission, and logs the message to the console.
   * Accepts either a {@link Lang} entry or a raw string.
   *
   * @param permission the permission required for players to receive the message
   * @param center     the center location to define the area
   * @param radius     the radius around the center
   * @param messageObj the message to send (Lang or String)
   * @param args       optional arguments for placeholder replacement (only used with Lang entries)
   */
  public void send(String permission, Location center, double radius, Object messageObj, String... args) {
    if (center == null || radius <= 0) {
      return;
    }

    String formatted = formatMessage(messageObj, args);
    double radiusSquared = radius * radius;

    for (Player player : fcManager.getCachedPlayers()) {
      if (player.getWorld() != center.getWorld()) {
        continue;
      }

      if (!player.hasPermission(permission)) {
        continue;
      }

      if (player.getLocation().distanceSquared(center) > radiusSquared) {
        continue;
      }

      player.sendMessage(formatted);
    }

    consoleSender.sendMessage(consolePrefix + replacePlaceholders(formatted, true));
  }

  /**
   * Sends a raw string message to a sender. Primarily used for dynamic/admin-only
   * notifications that are not stored in the Lang file.
   *
   * @param sender  the recipient
   * @param message the raw string message
   */
  public void send(CommandSender sender, String message) {
    if (sender instanceof Player) {
      sender.sendMessage(color(replacePlaceholders(message, false)));
    } else {
      consoleSender.sendMessage(consolePrefix + color(replacePlaceholders(message, true)));
    }
  }

  /**
   * Sends a broadcast message to all players on the server.
   * Accepts either a {@link Lang} entry or a raw string.
   *
   * @param messageObj the message to be broadcasted (Lang or String)
   * @param args       optional arguments for placeholder replacement (only used with Lang entries)
   */
  public void broadcast(Object messageObj, String... args) {
    String message = formatMessage(messageObj, args);
    server.broadcastMessage(message);
  }

  /**
   * Sends an action bar message to the specified player.
   * Accepts either a {@link Lang} entry or a raw string.
   *
   * @param player     the player to whom the action bar message will be sent
   * @param messageObj the message to send (Lang or String)
   * @param args       optional arguments for placeholder replacement (only used with Lang entries)
   */
  public void sendActionBar(Player player, Object messageObj, String... args) {
    String message = formatMessage(messageObj, args);
    IChatBaseComponent iChatBaseComponent = ChatSerializer.a("{\"text\":\"" + message + "\"}");
    PacketPlayOutChat packet = new PacketPlayOutChat(iChatBaseComponent, (byte) 2);
    ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
  }

  /**
   * Sends a formatted action bar message to all cached players on the server.
   * Accepts either a {@link Lang} entry or a raw string.
   *
   * @param messageObj the message to be broadcasted in the action bar (Lang or String)
   * @param args       optional arguments for placeholder replacement (only used with Lang entries)
   */
  @SuppressWarnings("unused")
  public void broadcastBar(Object messageObj, String... args) {
    String message = formatMessage(messageObj, args);
    IChatBaseComponent iChatBaseComponent = ChatSerializer.a("{\"text\":\"" + message + "\"}");
    PacketPlayOutChat packet = new PacketPlayOutChat(iChatBaseComponent, (byte) 2);

    for (Player player : fcManager.getCachedPlayers()) {
      ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }
  }

  /**
   * Sends a title and subtitle to a specified player with customized durations.
   * Accepts both {@link Lang} objects and raw strings for title and subtitle.
   *
   * @param player      the player to whom the title and subtitle will be sent
   * @param titleObj    the main title text (Lang or String)
   * @param subtitleObj the subtitle text (Lang or String)
   * @param fadeIn      the time in ticks for fade in
   * @param stay        the time in ticks to remain on screen
   * @param fadeOut     the time in ticks for fade out
   */
  public void title(Player player, Object titleObj, Object subtitleObj, int fadeIn, int stay, int fadeOut) {
    String title = formatMessage(titleObj);
    String subtitle = formatMessage(subtitleObj);

    CraftPlayer craftPlayer = (CraftPlayer) player;
    IChatBaseComponent titleJSON = ChatSerializer.a("{\"text\":\"" + title + "\"}");
    IChatBaseComponent subtitleJSON = ChatSerializer.a("{\"text\":\"" + subtitle + "\"}");

    craftPlayer.getHandle().playerConnection.sendPacket(
        new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.TITLE, titleJSON));
    craftPlayer.getHandle().playerConnection.sendPacket(
        new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.SUBTITLE, subtitleJSON));
    craftPlayer.getHandle().playerConnection.sendPacket(new PacketPlayOutTitle(fadeIn, stay, fadeOut));
  }

  /**
   * Converts a message object (either Lang entry or raw String) into a formatted string.
   * If the message is a Lang entry, it processes placeholders using the provided args.
   * If the message is a raw String, it returns the colored string as-is.
   *
   * @param messageObj the message object (Lang or String)
   * @param args       optional arguments for placeholder replacement (only used with Lang entries)
   * @return the formatted and colored message string
   */
  private String formatMessage(Object messageObj, String... args) {
    if (messageObj instanceof Lang) {
      return ((Lang) messageObj).replace(args);
    } else {
      return color(replacePlaceholders(messageObj.toString(), false));
    }
  }

  /**
   * Handles prefix placeholder replacement or removal based on context.
   * When clear is true, removes placeholders entirely for clean console output.
   * When clear is false, replaces placeholders with their formatted prefix values for player messages.
   *
   * @param message the message containing placeholder text like "{prefix}" or "{prefix-admin}"
   * @param clear if true, removes placeholders; if false, replaces them with formatted values
   * @return the processed message with placeholders either removed or replaced
   */
  public String replacePlaceholders(String message, boolean clear) {
    return message
        .replace("{prefix}", clear ? "" : PREFIX.toString())
        .replace("{prefix-admin}", clear ? "" : PREFIX_ADMIN.toString());
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
