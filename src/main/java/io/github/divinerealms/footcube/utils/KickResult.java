package io.github.divinerealms.footcube.utils;

import lombok.Getter;

@Getter
public class KickResult {
  private final double power;
  private final double charge;
  private final double baseKickPower;
  private final double finalKickPower;
  private final boolean isChargedHit;

  public KickResult(double power, double charge, double baseKickPower, double finalKickPower, boolean isChargedHit) {
    this.power = power;
    this.charge = charge;
    this.baseKickPower = baseKickPower;
    this.finalKickPower = finalKickPower;
    this.isChargedHit = isChargedHit;
  }
}