package com.auracode.ispec.model;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;
import com.auracode.ispec.util.DateFormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Session log writer: buffers entries in memory and flushes to disk
 * asynchronously, so no file IO ever blocks the main thread.
 * One file per session, archived with a start-end range on close.
 */
public final class SessionLogger {

   private static final String LOG_EXTENSION = ".log";
   private static final int MAX_FILE_ATTEMPTS = 100;
   private static final int FLUSH_INTERVAL_TICKS = 20;
   private static final int MAX_QUEUE_CAPACITY = 10_000;

   private final JavaPlugin plugin;
   private final Player spectator;
   private final Player suspect;
   private final String startTime;
   private final Path logFilePath;
   private final BlockingQueue<String> pendingEntries = new LinkedBlockingQueue<>(MAX_QUEUE_CAPACITY);
   private int flushTaskId = -1;

   public SessionLogger(@NotNull JavaPlugin plugin, @NotNull Player spectator, @NotNull Player suspect) {
      this.plugin = plugin;
      this.spectator = spectator;
      this.suspect = suspect;
      this.startTime = DateFormatUtil.now();
      this.logFilePath = this.createLogFile();
      this.enqueue("Session started at " + this.startTime);
      this.startFlushTask();
   }

   private @NotNull Path createLogFile() {
      Path logsDir = this.plugin.getDataFolder().toPath().resolve("logs").resolve(this.spectator.getUniqueId().toString());
      try {
         Files.createDirectories(logsDir);
      } catch (IOException e) {
         this.plugin.getLogger().severe("Cannot create logs directory " + logsDir + ": " + e.getMessage());
      }
      String fileName = this.startTime.replace(":", "-") + "-" + this.suspect.getName() + LOG_EXTENSION;
      return this.uniquePath(logsDir.resolve(fileName));
   }

   private @NotNull Path uniquePath(@NotNull Path path) {
      if (Files.notExists(path)) {
         return path;
      }
      return IntStream.rangeClosed(1, MAX_FILE_ATTEMPTS)
         .mapToObj(index -> path.getParent().resolve(baseName(path) + "-" + index + LOG_EXTENSION))
         .filter(candidate -> Files.notExists(candidate))
         .findFirst()
         .orElse(path);
   }

   private static @NotNull String baseName(@NotNull Path path) {
      String name = path.getFileName().toString();
      return name.endsWith(LOG_EXTENSION) ? name.substring(0, name.length() - LOG_EXTENSION.length()) : name;
   }

   private void startFlushTask() {
      this.flushTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, this::flushPending, 1L, FLUSH_INTERVAL_TICKS).getTaskId();
   }

   private void enqueue(@NotNull String message) {
      this.pendingEntries.offer("[" + DateFormatUtil.now() + "] " + message);
   }

   /**
    * Runs asynchronously: writes all queued entries to disk.
    */
   private void flushPending() {
      if (this.pendingEntries.isEmpty()) {
         return;
      }
      List<String> batch = new ArrayList<>(this.pendingEntries.size());
      this.pendingEntries.drainTo(batch);
      try (var writer = Files.newBufferedWriter(this.logFilePath, StandardCharsets.UTF_8,
         java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
         for (String line : batch) {
            writer.write(line);
            writer.newLine();
         }
      } catch (IOException e) {
         this.plugin.getLogger().severe("Cannot write to log file " + this.logFilePath + ": " + e.getMessage());
      }
   }

   public void log(@NotNull String message) {
      this.enqueue(message);
   }

   public void logChat(@NotNull String message) {
      this.log("Chat: " + message);
   }

   public void logCommand(@NotNull String command) {
      this.log("Command: " + command);
   }

   public void logLocation() {
      if (!this.spectator.isOnline()) {
         return;
      }
      Location location = this.spectator.getLocation();
      this.log(String.format(java.util.Locale.ROOT, "Location: X=%.2f Y=%.2f Z=%.2f World=%s",
         location.getX(), location.getY(), location.getZ(), location.getWorld().getName()));
   }

   public void close() {
      if (this.flushTaskId != -1) {
         Bukkit.getScheduler().cancelTask(this.flushTaskId);
         this.flushTaskId = -1;
      }
      this.enqueue("Session ended at " + DateFormatUtil.now());
      try {
         Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            this.flushPending();
            this.archive();
         });
      } catch (IllegalStateException e) {
         // Plugin already disabled: flush synchronously as a last resort.
         this.flushPending();
         this.archive();
      }
   }

   private void archive() {
      String endTime = DateFormatUtil.now().replace(":", "-");
      String archiveName = this.startTime.replace(":", "-") + " - " + endTime + " - " + this.suspect.getName() + LOG_EXTENSION;
      try {
         Files.move(this.logFilePath, this.uniquePath(this.logFilePath.getParent().resolve(archiveName)));
      } catch (IOException e) {
         this.plugin.getLogger().severe("Cannot archive log file " + this.logFilePath + ": " + e.getMessage());
      }
   }
}
