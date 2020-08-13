/* (C) 2020 Edward Harman */
package org.ethelred.log_helper;

import static java.nio.file.Files.createSymbolicLink;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isSameFile;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.Files.readSymbolicLink;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.util.StatusPrinter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "rolling_log_helper",
    mixinStandardHelpOptions = true,
    version = "0.1",
    description = "augment stdin with per line timestamps and append to rolling dated log files")
public class App implements Callable<Integer> {

  public static void main(String[] args) {
    System.exit(new CommandLine(new App()).execute(args));
  }

  @Override
  public Integer call() throws Exception {

    Logger logger = configureLogging();

    try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in))) {
      for (String line = r.readLine(); line != null; line = r.readLine()) {
        logger.info(line);
      }
    }
    return 0;
  }

  @Parameters(index = "0")
  public void setOutput(Path output) {
    original = output;
    Path parent = output.getParent();
    if (parent != null) {
      dir = parent.toString() + "/";
    }
    Path filename = output.getFileName();
    String[] parts = filename.toString().split("\\.");
    if (parts.length == 1) {
      name = parts[0];
    } else {
      name = "";
      for (int i = 0; i < parts.length; i++) {
        int reverseIndex = parts.length - 1 - i;
        switch (i) {
          case 0:
            extension = parts[reverseIndex];
            break;
          case 1:
            name = parts[reverseIndex];
            break;
          default:
            name = parts[reverseIndex] + "." + name;
        }
      }
    }
  }

  private Path original;
  private String dir = "";
  private String name;
  private String extension;

  @Option(
      names = {"-d", "--debug"},
      defaultValue = "false",
      description = "log to console as well as file")
  private boolean console;

  @Option(
      names = {"-l", "--link"},
      defaultValue = "true",
      description = "Maintain original name as symbolic link to latest log file")
  private boolean link;

  private static final long TIME_BETWEEN_CHECKS = TimeUnit.MINUTES.toMillis(1);

  private class LinkerPolicy<T> extends TimeBasedRollingPolicy<T> {
    private volatile long lastCheck = 0;

    @Override
    public String getActiveFileName() {
      String activeName = super.getActiveFileName();
      _checkLink(activeName);
      return activeName;
    }

    private void _checkLink(String activeName) {
      if (link) {
        long now = System.currentTimeMillis();
        if (now > lastCheck + TIME_BETWEEN_CHECKS) {
          lastCheck = now;
          try {
            Path active = Path.of(activeName).toAbsolutePath();
            if (exists(original)
                && isSymbolicLink(original)
                && !isSameFile(readSymbolicLink(original), active)) {
              delete(original);
            }
            if (!exists(original)) {
              createSymbolicLink(original.toAbsolutePath(), active);
            }
          } catch (IOException e) {
            System.err.println("Exception updating link");
            e.printStackTrace(System.err);
          }
        }
      }
    }
  }

  private Logger configureLogging() {
    LoggerContext lc = new LoggerContext();
    PatternLayoutEncoder ple = new PatternLayoutEncoder();

    ple.setPattern("[%d{yyyy-MM-dd HH:mm:ss}] %msg%n");
    ple.setContext(lc);
    ple.start();

    TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new LinkerPolicy<>();
    rollingPolicy.setFileNamePattern(dir + name + ".%d." + extension + ".gz");
    rollingPolicy.setMaxHistory(8);
    rollingPolicy.setContext(lc);

    RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
    appender.setContext(lc);
    appender.setRollingPolicy(rollingPolicy);
    rollingPolicy.setParent(appender);
    appender.setEncoder(ple);
    appender.setImmediateFlush(true);
    rollingPolicy.start();
    appender.start();

    Logger appLogger = lc.getLogger(App.class);
    appLogger.addAppender(appender);
    appLogger.setAdditive(console);

    if (console) StatusPrinter.print(lc);
    return appLogger;
  }
}
