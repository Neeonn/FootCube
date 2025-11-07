package io.github.divinerealms.footcube.physics.utilities;

import lombok.Getter;

@Getter
public class PlayerKickResult {
  private final double power;
  private final double charge;
  private final double baseKickPower;
  private final double finalKickPower;
  private final boolean isChargedHit;

  public PlayerKickResult(double power, double charge, double baseKickPower, double finalKickPower, boolean isChargedHit) {
    this.power = power;
    this.charge = charge;
    this.baseKickPower = baseKickPower;
    this.finalKickPower = finalKickPower;
    this.isChargedHit = isChargedHit;
  }
}