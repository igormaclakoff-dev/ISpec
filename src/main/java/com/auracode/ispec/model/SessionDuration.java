package com.auracode.ispec.model;

import java.text.MessageFormat;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of a session duration split into human readable units.
 */
public record SessionDuration(long seconds, long minutes, long hours, long totalHours, long totalMinutes, long totalSeconds) {

   private static final long MILLIS_PER_SECOND = 1000L;
   private static final long MILLIS_PER_MINUTE = 60L * MILLIS_PER_SECOND;
   private static final long MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE;
   private static final long SECONDS_PER_MINUTE = 60L;
   private static final long SECONDS_PER_HOUR = 3600L;
   private static final long HOURS_PER_DAY = 24L;

   public static @NotNull SessionDuration of(long startMillis, long endMillis) {
      long duration = Math.max(0L, endMillis - startMillis);
      long totalSeconds = duration / MILLIS_PER_SECOND;
      long totalMinutes = duration / MILLIS_PER_MINUTE;
      long totalHours = duration / MILLIS_PER_HOUR;
      return new SessionDuration(
         totalSeconds % SECONDS_PER_MINUTE,
         totalMinutes % SECONDS_PER_MINUTE,
         totalHours % HOURS_PER_DAY,
         totalHours,
         totalMinutes,
         totalSeconds
      );
   }

   public @NotNull String format(@NotNull String pattern) {
      return MessageFormat.format(pattern, this.hours, this.minutes, this.seconds, this.totalHours, this.totalMinutes, this.totalSeconds);
   }

   @Override
   public @NotNull String toString() {
      return String.format(Locale.ROOT, "%02d:%02d:%02d", this.hours, this.minutes, this.seconds);
   }
}
