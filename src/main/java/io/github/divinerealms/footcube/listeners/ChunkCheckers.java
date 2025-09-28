package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Physics;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkCheckers implements Listener {
  private final Physics physics;

  public ChunkCheckers(FCManager fcManager) {
    this.physics = fcManager.getPhysics();
  }

  @EventHandler
  public void onUnloadChunk(ChunkUnloadEvent event) {
    for (Entity entity : event.getChunk().getEntities()) {
      if (!(entity instanceof Slime)) continue;
      physics.getCubes().remove(entity);
      physics.getPracticeCubes().remove(entity);

      entity.remove();
    }
  }
}
