package io.github.divinerealms.footcube.commands;

import static io.github.divinerealms.footcube.configs.Lang.AVAILABLE_TYPE;
import static io.github.divinerealms.footcube.configs.Lang.COLOR;
import static io.github.divinerealms.footcube.configs.Lang.INVALID_COLOR;
import static io.github.divinerealms.footcube.configs.Lang.INVALID_TYPE;
import static io.github.divinerealms.footcube.configs.Lang.OFF;
import static io.github.divinerealms.footcube.configs.Lang.ON;
import static io.github.divinerealms.footcube.configs.Lang.PARTICLE;
import static io.github.divinerealms.footcube.configs.Lang.SET_GOAL_CELEBRATION;
import static io.github.divinerealms.footcube.configs.Lang.SET_PARTICLE;
import static io.github.divinerealms.footcube.configs.Lang.SET_PARTICLE_REDSTONE;
import static io.github.divinerealms.footcube.configs.Lang.SET_SOUND_GOAL;
import static io.github.divinerealms.footcube.configs.Lang.SET_SOUND_KICK;
import static io.github.divinerealms.footcube.configs.Lang.SOUND;
import static io.github.divinerealms.footcube.configs.Lang.TOGGLES_GOAL;
import static io.github.divinerealms.footcube.configs.Lang.TOGGLES_HIT_DEBUG;
import static io.github.divinerealms.footcube.configs.Lang.TOGGLES_KICK;
import static io.github.divinerealms.footcube.configs.Lang.TOGGLES_PARTICLES;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_SET_GOAL_CELEBRATION;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_SET_PARTICLE;
import static io.github.divinerealms.footcube.utils.Permissions.PERM_SET_SOUND;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import io.github.divinerealms.footcube.configs.PlayerData;
import io.github.divinerealms.footcube.core.FCManager;
import io.github.divinerealms.footcube.managers.PlayerDataManager;
import io.github.divinerealms.footcube.physics.PhysicsData;
import io.github.divinerealms.footcube.physics.utilities.PhysicsSystem;
import io.github.divinerealms.footcube.utils.Logger;
import io.github.divinerealms.footcube.utils.PlayerSettings;
import java.util.StringJoiner;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@CommandAlias("fc|footcube")
public class FCSettingsCommands extends BaseCommand {

  private final FCManager fcManager;
  private final Logger logger;
  private final PlayerDataManager dataManager;
  private final PhysicsData physicsData;
  private final PhysicsSystem system;

  public FCSettingsCommands(FCManager fcManager) {
    this.fcManager = fcManager;
    this.logger = fcManager.getLogger();
    this.dataManager = fcManager.getDataManager();
    this.physicsData = fcManager.getPhysicsData();
    this.system = fcManager.getPhysicsSystem();
  }

  @Subcommand("toggles kick|ts kick")
  @Description("Toggle kick sound effects")
  @CommandAlias("toggles kick|ts kick")
  public void onToggleKick(Player player) {
    PlayerData playerData = dataManager.get(player);
    if (playerData == null) {
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    settings.setKickSoundEnabled(!settings.isKickSoundEnabled());
    playerData.set("sounds.kick.enabled", settings.isKickSoundEnabled());
    logger.send(player, TOGGLES_KICK,
        settings.isKickSoundEnabled() ? ON.toString() : OFF.toString());
  }

  @Subcommand("toggles goal|ts goal")
  @Description("Toggle goal sound effects")
  @CommandAlias("toggles goal|ts goal")
  public void onToggleGoal(Player player) {
    PlayerData playerData = dataManager.get(player);
    if (playerData == null) {
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    settings.setGoalSoundEnabled(!settings.isGoalSoundEnabled());
    playerData.set("sounds.goal.enabled", settings.isGoalSoundEnabled());
    logger.send(player, TOGGLES_GOAL,
        settings.isGoalSoundEnabled() ? ON.toString() : OFF.toString());
  }

  @Subcommand("toggles particles|ts particles")
  @Description("Toggle particle effects")
  @CommandAlias("toggles particles|ts particles")
  public void onToggleParticles(Player player) {
    PlayerData playerData = dataManager.get(player);
    if (playerData == null) {
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    settings.setParticlesEnabled(!settings.isParticlesEnabled());
    playerData.set("particles.enabled", settings.isParticlesEnabled());
    logger.send(player, TOGGLES_PARTICLES,
        settings.isParticlesEnabled() ? ON.toString() : OFF.toString());
  }

  @Subcommand("toggles hits|ts hits")
  @Description("Toggle hit debug visualization")
  @CommandAlias("toggles hits|ts hits")
  public void onToggleHits(Player player) {
    boolean wasEnabled = physicsData.getCubeHits().contains(player.getUniqueId());

    if (wasEnabled) {
      physicsData.getCubeHits().remove(player.getUniqueId());
    } else {
      physicsData.getCubeHits().add(player.getUniqueId());
    }

    logger.send(player, TOGGLES_HIT_DEBUG, !wasEnabled ? ON.toString() : OFF.toString());
  }

  @Subcommand("setsound kick")
  @CommandPermission(PERM_SET_SOUND)
  @Syntax("<soundName|list>")
  @CommandCompletion("list")
  @Description("Set kick sound effect")
  @CommandAlias("setsound kick")
  public void onSetSoundKick(Player player, String soundName) {
    if (soundName.equalsIgnoreCase("list")) {
      showAllowedSounds(player, PlayerSettings.ALLOWED_KICK_SOUNDS);
      return;
    }

    PlayerData playerData = dataManager.get(player);
    if (playerData == null) {
      return;
    }

    Sound sound = parseSound(player, soundName);
    if (sound == null) {
      return;
    }

    if (!PlayerSettings.ALLOWED_KICK_SOUNDS.contains(sound)) {
      logger.send(player, INVALID_TYPE, SOUND.toString());
      showAllowedSounds(player, PlayerSettings.ALLOWED_KICK_SOUNDS);
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    settings.setKickSound(sound);
    playerData.set("sounds.kick.sound", sound.toString());
    logger.send(player, SET_SOUND_KICK, sound.name());
  }

  @Subcommand("setsound goal")
  @CommandPermission(PERM_SET_SOUND)
  @Syntax("<soundName|list>")
  @CommandCompletion("list")
  @Description("Set goal sound effect")
  @CommandAlias("setsound goal")
  public void onSetSoundGoal(Player player, String soundName) {
    if (soundName.equalsIgnoreCase("list")) {
      showAllowedSounds(player, PlayerSettings.ALLOWED_GOAL_SOUNDS);
      return;
    }

    PlayerData playerData = dataManager.get(player);
    if (playerData == null) {
      return;
    }

    Sound sound = parseSound(player, soundName);
    if (sound == null) {
      return;
    }

    if (!PlayerSettings.ALLOWED_GOAL_SOUNDS.contains(sound)) {
      logger.send(player, INVALID_TYPE, SOUND.toString());
      showAllowedSounds(player, PlayerSettings.ALLOWED_GOAL_SOUNDS);
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    settings.setGoalSound(sound);
    playerData.set("sounds.goal.sound", sound.toString());
    logger.send(player, SET_SOUND_GOAL, sound.name());
  }

  @Subcommand("setparticle")
  @CommandPermission(PERM_SET_PARTICLE)
  @Syntax("<particleName|list> [color]")
  @CommandCompletion("list|@particles @colors")
  @Description("Set particle effect for ball trail")
  @CommandAlias("setparticle")
  public void onSetParticle(Player player, String particleName, @Optional String color) {
    if (particleName.equalsIgnoreCase("list")) {
      logger.send(player, AVAILABLE_TYPE, PARTICLE.toString(),
          String.join(", ", PlayerSettings.getAllowedParticles()));
      system.recordPlayerAction(player);
      return;
    }

    PlayerData playerData = dataManager.get(player);
    if (playerData == null) {
      return;
    }

    EnumParticle particle = parseParticle(player, particleName);
    if (particle == null) {
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);

    if (particle == EnumParticle.REDSTONE) {
      handleRedstoneParticle(player, playerData, settings, particle, color);
      return;
    }

    settings.setParticle(particle);
    playerData.set("particles.effect", particle.toString());
    logger.send(player, SET_PARTICLE, particle.name());
  }

  @Subcommand("setgoalcelebration|sgc")
  @CommandPermission(PERM_SET_GOAL_CELEBRATION)
  @Syntax("<default|simple|epic|minimal|list>")
  @CommandCompletion("default|simple|epic|minimal|list")
  @Description("Set goal celebration style")
  @CommandAlias("setgoalcelebration|sgc")
  public void onSetGoalCelebration(Player player, String celebrationType) {
    if (celebrationType.equalsIgnoreCase("list")) {
      logger.send(player, AVAILABLE_TYPE, "goal celebrations", "default, simple, epic, minimal");
      system.recordPlayerAction(player);
      return;
    }

    PlayerData playerData = dataManager.get(player);
    if (playerData == null) {
      return;
    }

    String type = celebrationType.toLowerCase();
    if (!type.equals("default") && !type.equals("simple") &&
        !type.equals("epic") && !type.equals("minimal")) {
      logger.send(player, INVALID_TYPE, "goal celebrations");
      logger.send(player, AVAILABLE_TYPE, "goal celebrations", "default, simple, epic, minimal");
      system.recordPlayerAction(player);
      return;
    }

    PlayerSettings settings = fcManager.getPlayerSettings(player);
    settings.setGoalMessage(type);
    playerData.set("goalcelebration", type);
    logger.send(player, SET_GOAL_CELEBRATION, type);
  }

  private void showAllowedSounds(Player player, java.util.List<Sound> sounds) {
    StringJoiner joiner = new StringJoiner(", ");
    for (Sound s : sounds) {
      joiner.add(s.name());
    }
    logger.send(player, AVAILABLE_TYPE, SOUND.toString(), joiner.toString());
  }

  private Sound parseSound(Player player, String soundName) {
    try {
      return Sound.valueOf(soundName.toUpperCase());
    } catch (Exception e) {
      logger.send(player, INVALID_TYPE, SOUND.toString());
      system.recordPlayerAction(player);
      return null;
    }
  }

  private EnumParticle parseParticle(Player player, String particleName) {
    try {
      EnumParticle particle = EnumParticle.valueOf(particleName.toUpperCase());

      if (PlayerSettings.DISALLOWED_PARTICLES.contains(particle)) {
        logger.send(player, INVALID_TYPE, PARTICLE.toString());
        logger.send(player, AVAILABLE_TYPE, PARTICLE.toString(),
            String.join(", ", PlayerSettings.getAllowedParticles()));
        system.recordPlayerAction(player);
        return null;
      }

      return particle;
    } catch (Exception e) {
      logger.send(player, INVALID_TYPE, PARTICLE.toString());
      logger.send(player, AVAILABLE_TYPE, PARTICLE.toString(),
          String.join(", ", PlayerSettings.getAllowedParticles()));
      system.recordPlayerAction(player);
      return null;
    }
  }

  private void handleRedstoneParticle(Player player, PlayerData playerData,
      PlayerSettings settings, EnumParticle particle, String color) {
    String colorName = color != null ? color.toUpperCase() : "WHITE";
    try {
      settings.setCustomRedstoneColor(colorName);
      playerData.set("particles.effect", "REDSTONE:" + colorName);
      logger.send(player, SET_PARTICLE_REDSTONE, particle.name(), colorName);
    } catch (IllegalArgumentException e) {
      logger.send(player, INVALID_COLOR, colorName);
      logger.send(player, AVAILABLE_TYPE, COLOR.toString(),
          String.join(", ", PlayerSettings.getAllowedColorNames()));
      system.recordPlayerAction(player);
      return;
    }
    settings.setParticle(EnumParticle.REDSTONE);
  }
}