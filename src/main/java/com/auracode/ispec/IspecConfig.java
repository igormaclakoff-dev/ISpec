package com.auracode.ispec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Strongly typed, immutable view of config.yml.
 */
public final class IspecConfig {

   private static final Set<String> BOSS_BAR_COLORS = Set.of("BLUE", "GREEN", "PINK", "PURPLE", "RED", "WHITE", "YELLOW");
   private static final String DEFAULT_PREFIX = "<gradient:#7c4dff:#00d4ff><bold>ISpec</bold></gradient> <dark_gray>» <gray>";
   private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "ru");
   private static final String DEFAULT_LANGUAGE = "en";

   private final JavaPlugin plugin;

   private String language;

   private String prefix;
   private boolean suspectGlow;
   private boolean nightVision;
   private boolean bossBarEnabled;
   private String bossBarName;
   private String bossBarColor;
   private String bossBarOverlay;
   private boolean actionBarEnabled;
   private int actionBarIntervalTicks;
   private double maxDistance;
   private ReasonSettings reasons;
   private boolean stopOnGamemodeChange;
   private List<String> blockedCommands;
   private boolean notify;
   private boolean teleportAfterStop;
   private boolean returnToOldLocation;
   private boolean returnToOldGamemode;
   private String returnWorld;
   private double returnX;
   private double returnY;
   private double returnZ;
   private String returnGamemodeName;
   private String webhookUrl;
   private String webhookStartPayload;
   private String webhookStopPayload;
   private String datePattern;
   private String timeZone;
   private String durationFormat;
   private boolean startSoundEnabled;
   private String startSoundName;
   private boolean stopSoundEnabled;
   private String stopSoundName;
   private ConfigurationSection messages;

   public IspecConfig(@NotNull JavaPlugin plugin) {
      this.plugin = plugin;
   }

   public void load() {
      this.plugin.saveDefaultConfig();
      this.plugin.reloadConfig();
      var config = this.plugin.getConfig();

      this.language = normalizeLanguage(resolveLanguage(config));
      this.messages = loadLanguageSection();
      this.suspectGlow = config.getBoolean("settings.suspect-glow", true);
      this.nightVision = config.getBoolean("settings.night-vision", true);
      this.bossBarEnabled = config.getBoolean("settings.boss-bar.enabled", true);
      this.bossBarName = config.getString("settings.boss-bar.name", "{0}");
      this.bossBarColor = normalizeColor(config.getString("settings.boss-bar.color", "PURPLE"));
      this.bossBarOverlay = normalizeOverlay(config.getString("settings.boss-bar.overlay", "PROGRESS"));
      this.actionBarEnabled = config.getBoolean("settings.action-bar.enabled", true);
      this.actionBarIntervalTicks = Math.max(1, config.getInt("settings.action-bar.interval-ticks", 20));
      this.maxDistance = Math.max(3.0D, config.getDouble("settings.max-distance", 25.0D));

      this.reasons = new ReasonSettings(
         config.getBoolean("settings.reason.required", false),
         config.getBoolean("settings.reason.any", true),
         config.getBoolean("settings.reason.strict", false),
         config.getStringList("settings.reason.presets")
      );

      this.stopOnGamemodeChange = config.getBoolean("settings.stop-on-gamemode-change", true);
      this.blockedCommands = config.getStringList("settings.blocked-commands").stream()
         .map(raw -> raw.toLowerCase(Locale.ROOT))
         .collect(Collectors.toUnmodifiableList());
      this.notify = config.getBoolean("settings.notify", true);
      this.teleportAfterStop = config.getBoolean("settings.teleport-after-stop", true);
      this.returnToOldLocation = config.getBoolean("settings.return-to-old-location", false);
      this.returnToOldGamemode = config.getBoolean("settings.return-to-old-gamemode", false);
      this.returnWorld = config.getString("settings.return-world", "world");
      this.returnX = config.getDouble("settings.return-x", 0.5D);
      this.returnY = config.getDouble("settings.return-y", 90.0D);
      this.returnZ = config.getDouble("settings.return-z", 0.5D);
      this.returnGamemodeName = config.getString("settings.return-gamemode", "SURVIVAL");
      this.webhookUrl = config.getString("settings.webhook-url", "");
      this.webhookStartPayload = config.getString("settings.webhook-start-payload", "");
      this.webhookStopPayload = config.getString("settings.webhook-stop-payload", "");
      this.datePattern = config.getString("settings.date-pattern", "dd.MM.yyyy - HH:mm:ss");
      this.timeZone = config.getString("settings.time-zone", "Europe/Moscow");
      this.durationFormat = config.getString("settings.duration-format", "{0} h, {1} min, {2} sec");

      this.startSoundEnabled = config.getBoolean("settings.sounds.start.enabled", true);
      this.startSoundName = config.getString("settings.sounds.start.sound", "entity.experience_orb.pickup");
      this.stopSoundEnabled = config.getBoolean("settings.sounds.stop.enabled", true);
      this.stopSoundName = config.getString("settings.sounds.stop.sound", "entity.player.levelup");

      this.language = normalizeLanguage(resolveLanguage(config));
      this.messages = loadLanguageSection();

      this.plugin.getLogger().info("Configuration loaded (version " + this.plugin.getDescription().getVersion() + ")");
   }

   private @NotNull String resolveLanguage(@NotNull org.bukkit.configuration.Configuration config) {
      // Player command override has priority over config.yml
      File override = new File(this.plugin.getDataFolder(), "language.yml");
      if (override.exists()) {
         String value = YamlConfiguration.loadConfiguration(override).getString("language", "");
         if (value != null && !value.isEmpty()) {
            return value;
         }
      }
      return config.getString("language", DEFAULT_LANGUAGE);
   }

   public void setLanguage(@NotNull String language) {
      try {
         File override = new File(this.plugin.getDataFolder(), "language.yml");
         if (!override.exists()) {
            Files.write(override.toPath(), new byte[0], java.nio.file.StandardOpenOption.CREATE);
         }
         YamlConfiguration yaml = YamlConfiguration.loadConfiguration(override);
         yaml.set("language", language);
         yaml.save(override);
      } catch (IOException e) {
         this.plugin.getLogger().severe("Cannot save language override: " + e.getMessage());
      }
      this.language = normalizeLanguage(language);
      this.messages = loadLanguageSection();
   }

   private @NotNull ConfigurationSection loadLanguageSection() {
      String resource = "lang/" + this.language + ".yml";
      File file = new File(this.plugin.getDataFolder(), resource);
      if (!file.exists()) {
         this.plugin.saveResource(resource, false);
      }
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      var section = yaml.getConfigurationSection("messages");
      if (section == null) {
         this.plugin.getLogger().severe("Language file " + resource + " is missing 'messages' section");
         throw new IllegalStateException("Invalid language file: " + resource);
      }
      return section;
   }

   private static @NotNull String normalizeLanguage(String raw) {
      String value = raw == null ? DEFAULT_LANGUAGE : raw.toLowerCase(Locale.ROOT);
      return SUPPORTED_LANGUAGES.contains(value) ? value : DEFAULT_LANGUAGE;
   }

   private static @NotNull String normalizeColor(String raw) {
      String value = raw == null ? "PURPLE" : raw.toUpperCase(Locale.ROOT);
      return BOSS_BAR_COLORS.contains(value) ? value : "PURPLE";
   }

   private static @NotNull String normalizeOverlay(String raw) {
      String value = raw == null ? "PROGRESS" : raw.toUpperCase(Locale.ROOT);
      return switch (value) {
         case "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20" -> value;
         default -> "PROGRESS";
      };
   }

   public record ReasonSettings(boolean required, boolean any, boolean strict, @NotNull List<String> presets) {
      public boolean matches(@NotNull String reason) {
         return strict ? any || presets.stream().anyMatch(preset -> preset.equalsIgnoreCase(reason)) : true;
      }
   }

   // ---- Getters ----
   public @NotNull String getPrefix() { return this.getLangPrefix(); }
   public boolean isSuspectGlow() { return this.suspectGlow; }
   public boolean isNightVision() { return this.nightVision; }
   public boolean isBossBarEnabled() { return this.bossBarEnabled; }
   public @NotNull String getBossBarName() { return this.bossBarName; }
   public @NotNull String getBossBarColor() { return this.bossBarColor; }
   public @NotNull String getBossBarOverlay() { return this.bossBarOverlay; }
   public boolean isActionBarEnabled() { return this.actionBarEnabled; }
   public int getActionBarIntervalTicks() { return this.actionBarIntervalTicks; }
   public double getMaxDistance() { return this.maxDistance; }
   public @NotNull ReasonSettings getReasons() { return this.reasons; }
   public boolean isStopOnGamemodeChange() { return this.stopOnGamemodeChange; }
   public @NotNull List<String> getBlockedCommands() { return this.blockedCommands; }
   public boolean isNotify() { return this.notify; }
   public boolean isTeleportAfterStop() { return this.teleportAfterStop; }
   public boolean isReturnToOldLocation() { return this.returnToOldLocation; }
   public boolean isReturnToOldGamemode() { return this.returnToOldGamemode; }
   public @NotNull String getReturnWorld() { return this.returnWorld; }
   public double getReturnX() { return this.returnX; }
   public double getReturnY() { return this.returnY; }
   public double getReturnZ() { return this.returnZ; }
   public @NotNull String getReturnGamemodeName() { return this.returnGamemodeName; }
   public @NotNull String getWebhookUrl() { return this.webhookUrl; }
   public @NotNull String getWebhookStartPayload() { return this.webhookStartPayload; }
   public @NotNull String getWebhookStopPayload() { return this.webhookStopPayload; }
   public @NotNull String getDatePattern() { return this.datePattern; }
   public @NotNull String getTimeZone() { return this.timeZone; }
   public @NotNull String getDurationFormat() { return this.durationFormat; }
   public @NotNull ConfigurationSection getMessages() { return this.messages; }
   public @NotNull String getLanguage() { return this.language; }
   public boolean isStartSoundEnabled() { return this.startSoundEnabled; }
   public @NotNull String getStartSoundName() { return this.startSoundName; }
   public boolean isStopSoundEnabled() { return this.stopSoundEnabled; }
   public @NotNull String getStopSoundName() { return this.stopSoundName; }
   public @NotNull Set<String> getSupportedLanguages() { return SUPPORTED_LANGUAGES; }

   public @NotNull String getLangPrefix() {
      var root = this.messages.getRoot();
      return root != null ? root.getString("prefix", DEFAULT_PREFIX) : DEFAULT_PREFIX;
   }

   public @NotNull String getLangBossBarName() {
      var root = this.messages.getRoot();
      return root != null ? root.getString("boss-bar.name", "{0}") : "{0}";
   }
}
