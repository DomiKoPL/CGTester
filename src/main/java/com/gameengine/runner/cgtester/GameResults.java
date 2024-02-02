package com.codingame.gameengine.runner.cgtester;

import com.codingame.gameengine.runner.simulate.GameResult;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.common.collect.ImmutableList;

import java.util.concurrent.atomic.AtomicInteger;

public class GameResults {
  private final AtomicLongMap<String> wins = AtomicLongMap.create();
  private final AtomicLongMap<String> draws = AtomicLongMap.create();
  private final AtomicLongMap<String> losses = AtomicLongMap.create();
  private final AtomicLongMap<String> errors = AtomicLongMap.create();
  private final AtomicInteger games = new AtomicInteger(0);

  boolean containsErrors(int playerIndex, GameResult gameResult) {
    // It is usual for CG to return -1 in case of any player errors.
    int score = gameResult.scores.get(playerIndex);
    if (score == -1) {
      return true;
    }

    // In some games in case of timeout, the final score is equal to 0
    // and there is a message that player timeout out.
    for (String s : gameResult.summaries) {
      // Usual format: "First player ($0): Timeout!"
      if (s.contains("Timeout!") && s.contains(String.format("($%d)", playerIndex))) {
        return true;
      }
    }

    return false;
  }

  public synchronized void update(ImmutableList<Agent> agents, GameResult gameResult) {
    // Sadly this part only works for 2 players. :(
    assert (agents.size() == 2);

    // Fill in errors.
    for (int i = 0; i < agents.size(); ++i) {
      if (containsErrors(i, gameResult)) {
        String agentName = agents.get(i).name;
        errors.getAndIncrement(agentName);
        gameResult.scores.put(i, -1);
      }
    }

    int score0 = gameResult.scores.get(0);
    int score1 = gameResult.scores.get(1);

    if (score0 > score1) {
      wins.getAndIncrement(agents.get(0).name);
      losses.getAndIncrement(agents.get(1).name);
    } else if (score1 > score0) {
      wins.getAndIncrement(agents.get(1).name);
      losses.getAndIncrement(agents.get(0).name);
    } else {
      draws.getAndIncrement(agents.get(0).name);
      draws.getAndIncrement(agents.get(1).name);
    }

    games.getAndIncrement();
  }

  public void printFinal(ImmutableList<Agent> agents) {
    agents.stream().forEach(agent -> {
      System.out.println(String.format(
          "Agent(%s) wins=%s draws=%s losses=%s errors=%s",
          agent.name, wins.get(agent.name),
          draws.get(agent.name), losses.get(agent.name),
          errors.get(agent.name)));
    });
  }
}