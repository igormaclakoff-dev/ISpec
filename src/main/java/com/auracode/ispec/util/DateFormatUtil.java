package com.auracode.ispec.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Central date formatter configured from config.yml.
 */
public final class DateFormatUtil {

   private static final String DEFAULT_PATTERN = "dd.MM.yyyy - HH:mm:ss";
   private static final String DEFAULT_ZONE = "Europe/Moscow";

   private static DateTimeFormatter formatter;
   private static ZoneId zoneId;
   private static boolean initialized;

   private DateFormatUtil() {
   }

   public static void initialize(@NotNull JavaPlugin plugin, @NotNull String pattern, @NotNull String zone) {
      try {
         formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
      } catch (IllegalArgumentException e) {
         formatter = DateTimeFormatter.ofPattern(DEFAULT_PATTERN, Locale.ROOT);
         plugin.getLogger().log(Level.WARNING, "Invalid date pattern '" + pattern + "', using default " + DEFAULT_PATTERN);
      }
      try {
         zoneId = ZoneId.of(zone);
      } catch (RuntimeException e) {
         zoneId = ZoneId.of(DEFAULT_ZONE);
         plugin.getLogger().log(Level.WARNING, "Invalid time zone '" + zone + "', using default " + DEFAULT_ZONE);
      }
      initialized = true;
   }

   public static @NotNull String now() {
      if (!initialized) {
         formatter = DateTimeFormatter.ofPattern(DEFAULT_PATTERN, Locale.ROOT);
         zoneId = ZoneId.of(DEFAULT_ZONE);
         initialized = true;
      }
      return ZonedDateTime.now(zoneId).format(formatter);
   }
}
