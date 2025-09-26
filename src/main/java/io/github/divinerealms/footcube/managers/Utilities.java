package io.github.divinerealms.footcube.managers;

import io.github.divinerealms.footcube.core.FCManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.ChatColor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Utilities {
  public LuckPerms luckPerms;

  public Utilities(FCManager fcManager) {
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
}
