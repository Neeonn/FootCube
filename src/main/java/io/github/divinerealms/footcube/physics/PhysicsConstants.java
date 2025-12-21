package io.github.divinerealms.footcube.physics;

import lombok.Getter;
import java.util.concurrent.ThreadLocalRandom;

@Getter
public class PhysicsConstants {
  // --- Debug & Monitoring ---
  public static final int DEBUG_ON_MS = 5;

  // --- Task Intervals (Ticks) ---
  public static final long PHYSICS_TASK_INTERVAL_TICKS = 1;
  public static final long MATCH_TASK_INTERVAL_TICKS = 1;
  public static final long GLOW_TASK_INTERVAL_TICKS = 10;
  public static final long CUBE_REMOVAL_DELAY_TICKS = 20;
  public static final int CLEANUP_LAST_TOUCHES_INTERVAL = 2;
  public static final int EXP_UPDATE_INTERVAL_TICKS = 2;

  // --- Timeouts & Cooldowns (Milliseconds) ---
  public static final long REGULAR_KICK_COOLDOWN = 150;
  public static final long CHARGED_KICK_COOLDOWN = 500;
  public static final long RISE_COOLDOWN = 500;
  public static final long AFK_THRESHOLD = 60_000;
  public static final long SPAWN_COOLDOWN_MS = 3_000L;

  // --- Slime / Entity Configuration ---
  public static final int SLIME_SIZE = 1;
  public static final int JUMP_POTION_DURATION = Integer.MAX_VALUE;
  public static final int JUMP_POTION_AMPLIFIER = -3;

  // --- Kick Power & Charge Settings ---
  public static final double MAX_KP = 5;
  public static final double SOFT_CAP_MIN_FACTOR = 0.9;
  public static final double CHARGE_MULTIPLIER = 7;
  public static final double CHARGED_BASE_POWER = 0.375;
  public static final double REGULAR_BASE_POWER = 0.65;
  public static final double CHARGE_BASE_VALUE = 1;
  public static final double CHARGE_RECOVERY_RATE = 0.945;

  // --- Velocity & Motion Modifiers ---
  public static final double MIN_SPEED_FOR_DAMPENING = 0.5;
  public static final double VELOCITY_DAMPENING_FACTOR = 0.5;
  public static final double LOW_VELOCITY_THRESHOLD = 0.2;
  public static final double LOW_VELOCITY_PUSH_MULTIPLIER = 1.2;
  public static final double MIN_BOUNCE_VELOCITY_Y = 0.05;
  public static final double WALL_BOUNCE_FACTOR = 0.8;
  public static final double AIR_DRAG_FACTOR = 0.98;
  public static final double CUBE_JUMP_RIGHT_CLICK = 0.7;
  public static final double KICK_VERTICAL_BOOST = 0.3;

  // --- Distance & Collision Thresholds ---
  public static final double PLAYER_CLOSE = 1.5;
  public static final double HIT_RADIUS = 1.2;
  public static final double MIN_RADIUS = 0.8;
  public static final double BOUNCE_THRESHOLD = 0.3;

  // --- Physics Multipliers ---
  public static final double BALL_TOUCH_Y_OFFSET = 1;
  public static final double CUBE_HITBOX_ADJUSTMENT = 1.5;
  public static final double KICK_POWER_SPEED_MULTIPLIER = 2;
  public static final double PLAYER_SPEED_TOUCH_DIVISOR = 3;
  public static final double CUBE_SPEED_TOUCH_DIVISOR = 6;
  public static final double PROXIMITY_THRESHOLD_MULTIPLIER = 1.3;

  // --- Physics Math Thresholds ---
  public static final double VECTOR_CHANGE_THRESHOLD = 0.1;
  public static final double VERTICAL_BOUNCE_THRESHOLD = 0.05;
  public static final double TOLERANCE_VELOCITY_CHECK = 1.0E-6;

  // --- Player / Location Offsets ---
  public static final int PLAYER_HEAD_LEVEL = 2;
  public static final int PLAYER_FOOT_LEVEL = 1;

  // --- Sound Defaults ---
  public static final double MIN_SOUND_POWER = 0.15;
  public static final float SOUND_VOLUME = 0.5F;
  public static final float SOUND_PITCH = 1;

  // --- Particle Defaults ---
  public static final double DISTANCE_PARTICLE_THRESHOLD = 32;
  public static final double DISTANCE_PARTICLE_THRESHOLD_SQUARED = DISTANCE_PARTICLE_THRESHOLD * DISTANCE_PARTICLE_THRESHOLD;
  public static final double MAX_PARTICLE_DISTANCE = 160;
  public static final double MAX_PARTICLE_DISTANCE_SQUARED = MAX_PARTICLE_DISTANCE * MAX_PARTICLE_DISTANCE;
  public static final double PARTICLE_Y_OFFSET = 0.25;
  public static final float GENERIC_PARTICLE_OFFSET = 0.01F;
  public static final float GENERIC_PARTICLE_SPEED = 0.1F;
  public static final int GENERIC_PARTICLE_COUNT = 10;

  // --- Utility ---
  public static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();
}