package io.github.divinerealms.footcube.physics.touch;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.CHARGED_KICK_COOLDOWN;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.REGULAR_KICK_COOLDOWN;
import static io.github.divinerealms.footcube.physics.PhysicsConstants.RISE_COOLDOWN;

import lombok.Getter;

@Getter
public enum CubeTouchType {
  REGULAR_KICK(REGULAR_KICK_COOLDOWN),
  CHARGED_KICK(CHARGED_KICK_COOLDOWN),
  RISE(RISE_COOLDOWN);

  private final long cooldown;

  CubeTouchType(long cooldown) {
    this.cooldown = cooldown;
  }
}