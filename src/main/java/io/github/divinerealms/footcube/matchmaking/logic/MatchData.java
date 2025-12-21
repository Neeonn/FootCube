package io.github.divinerealms.footcube.matchmaking.logic;

import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.util.MatchConstants;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

@Getter
public class MatchData {
  private final List<Match> matches = new CopyOnWriteArrayList<>();
  private final List<Match> openMatches = new CopyOnWriteArrayList<>();
  private final Map<Integer, Queue<Player>> playerQueues = new ConcurrentHashMap<>();
  private final Set<Integer> lockedQueues = ConcurrentHashMap.newKeySet();
  private final Map<Integer, ReentrantLock> queueLocks = new ConcurrentHashMap<>();

  @Setter private boolean matchesEnabled = true;

  public MatchData() {
    playerQueues.put(MatchConstants.TWO_V_TWO, new ConcurrentLinkedQueue<>());
    playerQueues.put(MatchConstants.THREE_V_THREE, new ConcurrentLinkedQueue<>());
    playerQueues.put(MatchConstants.FOUR_V_FOUR, new ConcurrentLinkedQueue<>());
    playerQueues.put(MatchConstants.FIVE_V_FIVE, new ConcurrentLinkedQueue<>());
  }
}
