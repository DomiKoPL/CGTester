package com.codingame.gameengine.runner.cgtester;

import com.google.common.collect.ImmutableList;

public class Task {
  final public ImmutableList<Agent> agents;
  final public long seed;
  final public long index;

  public Task(ImmutableList<Agent> agents, long seed, long index) {
    this.agents = agents;
    this.seed = seed;
    this.index = index;
  }
}