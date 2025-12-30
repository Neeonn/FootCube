package io.github.divinerealms.footcube.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkCheckers implements Listener {

  @EventHandler
  public void onUnloadChunk(ChunkUnloadEvent event) {
    for (Entity entity : event.getChunk().getEntities()) {
      if (!(entity instanceof Slime)) {
        continue;
      }

      ((Slime) entity).setHealth(0);
    }
  }
}
