package io.github.divinerealms.footcube.utils;

import io.github.divinerealms.footcube.configs.Lang;
import lombok.Getter;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.IChatBaseComponent.ChatSerializer;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class Logger {
  private final Server server;
  private final ConsoleCommandSender consoleSender;
  @Getter private final String consolePrefix;

  public Logger(Plugin plugin) {
    this.server = plugin.getServer();
    this.consoleSender = this.server.getConsoleSender();
    this.consolePrefix = ChatColor.GREEN + "[" + plugin.getDescription().getName() + "] " + ChatColor.DARK_GREEN;
  }

  public void info(String message) {
    message = message.replace("{plugin-string}", "").replace("{admin-string}", "");
    consoleSender.sendMessage(consolePrefix + color(message));
  }

  public void send(CommandSender sender, String message) {
    if (sender instanceof Player) {
      message = message.replace("{plugin-string}", Lang.PLUGIN_STRING.replace(null)).replace("{admin-string}", Lang.ADMIN_STRING.replace(null));
      sender.sendMessage(color(message));
    } else {
      message = message.replace("{plugin-string}", "").replace("{admin-string}", "");
      consoleSender.sendMessage(consolePrefix + color(message));
    }
  }

  public void send(String permission, String message) {
    String formattedMc = message.replace("{plugin-string}", Lang.PLUGIN_STRING.replace(null)).replace("{admin-string}", Lang.ADMIN_STRING.replace(null));
    String formattedConsole = message.replace("{plugin-string}", "").replace("{admin-string}", "");

    server.broadcast(color(formattedMc), permission);
    consoleSender.sendMessage(consolePrefix + color(formattedConsole));
  }

  public void broadcast(String message) {
    server.broadcastMessage(color(message));
  }

  public void sendActionBar(Player player, String message) {
    message = color(message.replace("{plugin-string}", Lang.PLUGIN_STRING.replace(null)).replace("{admin-string}", Lang.ADMIN_STRING.replace(null)));
    IChatBaseComponent iChatBaseComponent = ChatSerializer.a("{\"text\": \"" + message + "\"}");
    PacketPlayOutChat packetPlayOutChat = new PacketPlayOutChat(iChatBaseComponent, (byte)2);
    ((CraftPlayer)player).getHandle().playerConnection.sendPacket(packetPlayOutChat);
  }

  public void broadcastBar(String message) {
    message = color(message.replace("{plugin-string}", Lang.PLUGIN_STRING.replace(null)).replace("{admin-string}", Lang.ADMIN_STRING.replace(null)));
    IChatBaseComponent iChatBaseComponent = ChatSerializer.a("{\"text\": \"" + message + "\"}");
    PacketPlayOutChat packetPlayOutChat = new PacketPlayOutChat(iChatBaseComponent, (byte)2);

    for(Player player : server.getOnlinePlayers()) {
      ((CraftPlayer)player).getHandle().playerConnection.sendPacket(packetPlayOutChat);
    }
  }

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

  public String color(String message) {
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}
