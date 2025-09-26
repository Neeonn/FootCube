package io.github.divinerealms.footcube.utils;

import lombok.Getter;

@Getter
public class KickResult {
  private final double power;
  private final double charge;
  private final double baseKickPower;
  private final double finalKickPower;

  public KickResult(double power, double charge, double baseKickPower, double finalKickPower) {
    this.power = power;
    this.charge = charge;
    this.baseKickPower = baseKickPower;
    this.finalKickPower = finalKickPower;
  }
}