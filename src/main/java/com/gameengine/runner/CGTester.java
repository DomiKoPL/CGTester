package com.codingame.gameengine.runner;

import com.beust.jcommander.JCommander;
import com.codingame.gameengine.runner.MultiplayerGameRunner;
import com.codingame.gameengine.runner.cgtester.Agent;
import com.codingame.gameengine.runner.cgtester.Arguments;
import com.codingame.gameengine.runner.cgtester.GameResults;
import com.codingame.gameengine.runner.cgtester.Task;
import com.codingame.gameengine.runner.simulate.GameResult;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CGTester {

  private final ArrayBlockingQueue<Task> taskQueue;
  private final ThreadPoolExecutor executor;
  private final GameResults gameResults = new GameResults();
  private final ImmutableList<Agent> agents;
  private final Arguments arguments;
  private final String logsDirectory;

  public CGTester(Arguments arguments) throws Exception {
    this.arguments = arguments;

    for (Agent agent : arguments.agents) {
      System.out.println(agent.name + " " + agent.commandLine);
    }

    this.agents = ImmutableList.copyOf(arguments.agents);

    logsDirectory = arguments.logs;

    if (logsDirectory != null) {
      Path logs = FileSystems.getDefault().getPath(arguments.logs);
      if (!Files.isDirectory(logs)) {
        throw new NotDirectoryException(
            "Given path for logs directory is not a directory: " + logs);
      }
    }

    // This also is true only for 2 players.
    taskQueue = new ArrayBlockingQueue<>(arguments.games * 2);
    executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(arguments.threads);
  }

  public void run() {
    try {
      if (arguments.swap) {
        long index = 0;
        List<Agent> currentAgents = new ArrayList<>(this.agents);
        for (long seed = 0; seed < arguments.games; ++seed) {
          // This section also works only for 2 players.
          for (int i = 0; i < 2; ++i) {
            taskQueue.put(
                new Task(ImmutableList.copyOf(currentAgents), seed, index));
            Collections.rotate(currentAgents, 1);
            index += 1;
          }
        }
      } else {
        long index = 0;
        for (long seed = 0; seed < arguments.games; ++seed) {
          taskQueue.put(new Task(this.agents, seed, index));
          index += 1;
        }
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

    gameResults.printFinal(agents);
  }

  Path getLogPathForTask(Task task) {
    return Paths.get(logsDirectory, task.index + ".json");
  }

  synchronized void print(Task task, GameResult gameResult) {
    String agentNames = task.agents.stream().map(a -> a.name)
        .collect(Collectors.joining(" vs "));
    String scores = IntStream.range(0, task.agents.size())
        .mapToObj(idx -> gameResult.scores.get(idx).toString())
        .collect(Collectors.joining(", "));

    String msg = String.format("Seed=%s %s scores: %s",
        task.seed, agentNames, scores);

    boolean containsErrors = gameResult.scores.entrySet().stream()
        .anyMatch(x -> x.getValue().equals(-1));
    if (containsErrors) {
      // Add red color to message.
      msg = "\033[0;31m" + msg + "\033[0m";
    }

    System.out.println(msg);
  }

  class Worker implements Runnable {

    void play(Task task) {
      MultiplayerGameRunner runner = new MultiplayerGameRunner();
      runner.setLeagueLevel(arguments.leagueLevel);
      runner.setSeed(task.seed);
      for (Agent agent : task.agents) {
        runner.addAgent(agent.commandLine, agent.name);
      }

      GameResult gameResult = runner.simulate();

      if (logsDirectory != null) {
        try {
          String result = new Gson().toJson(runner.gameResult);
          Files.write(getLogPathForTask(task), result.getBytes());
        } catch (Exception e) {
          System.out.println("Error while saving logs!");
          e.printStackTrace();
        }
      }

      gameResults.update(task.agents, gameResult);
      print(task, gameResult);
    }

    @Override
    public void run() {
      while (true) {
        try {
          Task task = taskQueue.poll(500, TimeUnit.MILLISECONDS);
          play(task);
        } catch (InterruptedException e) {
          break;
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    Arguments arguments = new Arguments();
    JCommander jCommander = JCommander.newBuilder().addObject(arguments).build();
    jCommander.parse(args);
    if (arguments.help) {
      jCommander.usage();
      return;
    }

    if (arguments.draw != null) {
      System.out.println(arguments.draw);
      String jsonResult = new String(Files.readAllBytes(Paths.get(arguments.draw)));
      System.out.println(jsonResult);
      new Renderer(4444).render(2, jsonResult);
      return;
    }

    try {
      CGTester tester = new CGTester(arguments);
      tester.run();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
