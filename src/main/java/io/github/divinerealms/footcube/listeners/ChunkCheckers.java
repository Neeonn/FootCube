package io.github.divinerealms.footcube.listeners;

import io.github.divinerealms.footcube.configs.Lang;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.core.Physics;
import io.github.divinerealms.footcube.utils.Logger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkCheckers implements Listener {
  private final Physics physics;
  private final Logger logger;

  public ChunkCheckers(FCManager fcManager) {
    this.physics = fcManager.getPhysics();
    this.logger = fcManager.getLogger();
  }

  @EventHandler
  public void onUnloadChunk(ChunkUnloadEvent event) {
    int amount = 0;

    for (Entity entity : event.getChunk().getEntities()) {
      if (!(entity instanceof Slime)) continue;
      amount++;
      physics.getCubes().remove(entity);
      physics.getPracticeCubes().remove(entity);

      entity.remove();
    }

    if (amount > 0) logger.broadcastBar(Lang.CLEARED_CUBES.replace(new String[]{String.valueOf(amount)}));
  }
}
