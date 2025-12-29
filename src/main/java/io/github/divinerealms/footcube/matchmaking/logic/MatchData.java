package io.github.divinerealms.footcube.matchmaking.logic;

import io.github.divinerealms.footcube.matchmaking.Match;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.divinerealms.footcube.matchmaking.util.MatchConstants.*;

@Getter
public class MatchData {
  private final List<Match> matches = new CopyOnWriteArrayList<>();
  private final List<Match> openMatches = new CopyOnWriteArrayList<>();
  private final Map<Integer, Queue<Player>> playerQueues = new ConcurrentHashMap<>();
  private final Set<Integer> lockedQueues = ConcurrentHashMap.newKeySet();
  private final Map<Integer, ReentrantLock> queueLocks = new ConcurrentHashMap<>();

  @Setter private boolean matchesEnabled = true;

  public MatchData() {
    // playerQueues.put(ONE_V_ONE, new ConcurrentLinkedQueue<>());
    playerQueues.put(TWO_V_TWO, new ConcurrentLinkedQueue<>());
    playerQueues.put(THREE_V_THREE, new ConcurrentLinkedQueue<>());
    playerQueues.put(FOUR_V_FOUR, new ConcurrentLinkedQueue<>());
    // playerQueues.put(FIVE_V_FIVE, new ConcurrentLinkedQueue<>());
  }
}
