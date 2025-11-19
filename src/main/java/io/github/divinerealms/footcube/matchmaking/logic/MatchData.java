package io.github.divinerealms.footcube.matchmaking.logic;

import io.github.divinerealms.footcube.matchmaking.Match;
import io.github.divinerealms.footcube.matchmaking.util.MatchmakingConstants;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class MatchData {
  private final List<Match> matches = new CopyOnWriteArrayList<>();
  private final List<Match> openMatches = new CopyOnWriteArrayList<>();
  private final Map<Integer, Queue<Player>> playerQueues = new ConcurrentHashMap<>();

  @Setter private boolean matchesEnabled = true;

  public MatchData() {
    playerQueues.put(MatchmakingConstants.TWO_V_TWO, new ConcurrentLinkedQueue<>());
    playerQueues.put(MatchmakingConstants.THREE_V_THREE, new ConcurrentLinkedQueue<>());
    playerQueues.put(MatchmakingConstants.FOUR_V_FOUR, new ConcurrentLinkedQueue<>());
    playerQueues.put(MatchmakingConstants.FIVE_V_FIVE, new ConcurrentLinkedQueue<>());
  }
}
