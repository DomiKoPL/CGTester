package com.codingame.gameengine.runner.cgtester;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.IStringConverter;

import java.util.ArrayList;
import java.util.List;

class AgentListConverter implements IStringConverter<List<Agent>> {
  @Override
  public List<Agent> convert(String files) {
    String[] configs = files.split(",");
    List<Agent> agents = new ArrayList<>();
    for (String config : configs) {
      if (config.contains("@")) {
        String[] parts = config.split("@");
        if (parts.length != 2) {
          System.err.println("Too many parts!");
          System.exit(1);
        }

        agents.add(new Agent(parts[0], parts[1]));
      } else {
        agents.add(new Agent(config, config));
      }
    }
    return agents;
  }
}

public class Arguments {
  @Parameter(names = { "-d", "--draw" })
  public String draw = null;

  @Parameter(names = { "-a", "--agents" }, listConverter = AgentListConverter.class, description = "List of agents.")
  public List<Agent> agents;

  @Parameter(names = { "-t", "--threads" }, description = "Number of threads to use.")
  public Integer threads = 4;

  @Parameter(names = { "-g", "--games" }, description = "Number of games to play.")
  public Integer games = 100;

  @Parameter(names = { "-s", "--swap" }, description = "Swap player position for each seed.")
  public boolean swap = false;

  @Parameter(names = { "--level" }, description = "leagueLevel")
  public Integer leagueLevel = 2;

  @Parameter(names = { "-l", "--logs" }, description = "Log directory.")
  public String logs = null;

  @Parameter(names = { "-h", "--help" }, help = true)
  public boolean help = false;
};