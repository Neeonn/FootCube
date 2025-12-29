package io.github.divinerealms.footcube.physics.touch;

import lombok.Getter;

import static io.github.divinerealms.footcube.physics.PhysicsConstants.*;

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