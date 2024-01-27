package cgtester;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.IStringConverter;
import com.codingame.gameengine.runner.MultiplayerGameRunner;
import com.codingame.gameengine.runner.simulate.GameResult;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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

class Arguments {

  @Parameter(names = { "-a", "--agents" }, listConverter = AgentListConverter.class, description = "List of agents.")
  public List<Agent> agents;

  @Parameter(names = { "-t", "--threads" }, description = "Number of threads to use.")
  public Integer threads = 4;

  @Parameter(names = { "-g", "--games" }, description = "Number of games to play.")
  public Integer games = 100;

  @Parameter(names = { "--level" }, description = "leagueLevel")
  public Integer leagueLevel = 2;

  @Parameter(names = { "-h", "--help" }, help = true)
  public boolean help = false;
};

class Agent {
  public String commandLine;

  public String name;

  Agent(String commandLine, String name) {
    this.commandLine = commandLine;
    this.name = name;
  }
}

class Task {
  final ImmutableList<Agent> agents;
  final long seed;

  Task(ImmutableList<Agent> agents, long seed) {
    this.agents = agents;
    this.seed = seed;
  }
}

class GameResults {
  private final AtomicLongMap<String> wins = AtomicLongMap.create();
  private final AtomicInteger draws = new AtomicInteger(0);
  private final AtomicLongMap<String> errors = AtomicLongMap.create();
  private final AtomicInteger games = new AtomicInteger(0);

  public void update(ImmutableList<Agent> agents, GameResult gameResult) {
    // Sadly this part only works for 2 players. :(
    assert (agents.size() == 2);

    // Fill in errors.
    for (int i = 0; i < agents.size(); ++i) {
      String agentName = agents.get(i).name;
      int score = gameResult.scores.get(i);
      if (score == -1) {
        errors.getAndIncrement(agentName);
      }
    }

    int score0 = gameResult.scores.get(0);
    int score1 = gameResult.scores.get(1);

    if (score0 > score1) {
      wins.getAndIncrement(agents.get(0).name);
    } else if (score1 > score0) {
      wins.getAndIncrement(agents.get(1).name);
    } else {
      draws.getAndIncrement();
    }

    games.getAndIncrement();
  }

  public synchronized void print(ImmutableList<Agent> agents) {
    String msg = agents.stream().map(agent -> {
      return String.format("Agent[%s] wins=%s errors=%s",
          agent.name, wins.get(agent.name), errors.get(agent.name));
    }).collect(Collectors.joining(" "));

    System.out.println(msg);
  }

  public void printFinal(ImmutableList<Agent> agents) {

  }
}

class Confidence {

  public static double getConfidence95Interval(int wins, int draws, int loses) {
    int plays = wins + draws + loses;
    double avg = (wins + draws * 0.5) / plays;
    double var = (wins + draws * 0.25 - avg * avg * plays) / (wins + draws + loses - 1);
    return 1.9602 * Math.sqrt(var / (plays - 1)); // 95%
  }

  public static double getConfidence99Interval(int wins, int draws, int loses) {
    int plays = wins + draws + loses;
    double avg = (wins + draws * 0.5) / plays;
    double var = (wins + draws * 0.25 - avg * avg * plays) / (wins + draws + loses - 1);
    return 2.5763 * Math.sqrt(var / (plays - 1)); // 99%
  }

}

class CGTester {

  private final ArrayBlockingQueue<Task> dataQueue;
  private final ThreadPoolExecutor executor;
  private final GameResults gameResults = new GameResults();
  private final ImmutableList<Agent> agents;
  private final Arguments arguments;

  public CGTester(Arguments arguments) {
    this.arguments = arguments;

    for (Agent agent : arguments.agents) {
      System.out.println(agent.name + " " + agent.commandLine);
    }

    this.agents = ImmutableList.copyOf(arguments.agents);

    dataQueue = new ArrayBlockingQueue<>(arguments.games);
    executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(arguments.threads);
  }

  public void run() {
    try {
      List<Agent> currentAgents = new ArrayList<>(this.agents);
      for (long seed = 0; seed < arguments.games; ++seed) {
        dataQueue.put(new Task(ImmutableList.copyOf(currentAgents), seed));
        Collections.rotate(currentAgents, 1);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    for (int i = 0; i < arguments.threads; i++) {
      executor.submit(new Worker());
    }

    executor.shutdown();
    // Wait for all tasks to be completed.
    while (!executor.isTerminated()) {
      ;
    }

    System.out.println("Done.");
    gameResults.printFinal(agents);
  }

  class Worker implements Runnable {

    void play(Task task) {
      MultiplayerGameRunner runner = new MultiplayerGameRunner();
      runner.setLeagueLevel(arguments.leagueLevel);
      runner.setSeed(task.seed);
      for (Agent agent : task.agents) {
        runner.addAgent(agent.commandLine);
      }

      GameResult gameResult = runner.simulate();
      gameResults.update(task.agents, gameResult);
      gameResults.print(agents);
    }

    @Override
    public void run() {
      while (true) {
        try {
          Task task = dataQueue.poll(500, TimeUnit.MILLISECONDS);
          play(task);
        } catch (InterruptedException e) {
          break;
        }
      }
    }
  }

  public static void main(String[] args) throws InterruptedException {
    Arguments arguments = new Arguments();
    JCommander jCommander = JCommander.newBuilder().addObject(arguments).build();
    jCommander.parse(args);
    if (arguments.help) {
      jCommander.usage();
      return;
    }

    CGTester tester = new CGTester(arguments);
    tester.run();
  }
}
