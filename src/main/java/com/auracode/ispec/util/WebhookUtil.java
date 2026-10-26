package com.auracode.ispec.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Asynchronous Discord webhook sender via java.net.http.
 */
public final class WebhookUtil {

   private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

   private static HttpClient httpClient;

   private WebhookUtil() {
   }

   public static void initialize() {
      if (httpClient == null) {
         httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
      }
   }

   public static void sendAsync(@NotNull JavaPlugin plugin, @NotNull String url, @NotNull String payload) {
      if (url == null || url.isEmpty()) {
         return;
      }
      if (httpClient == null) {
         initialize();
      }
      try {
         HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

         httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenAccept(response -> {
               int status = response.statusCode();
               if (status < 200 || status >= 300) {
                  plugin.getLogger().warning("Webhook responded with status " + status);
               }
            })
            .exceptionally(e -> {
               plugin.getLogger().severe("Webhook error: " + e.getMessage());
               return null;
            });
      } catch (IllegalArgumentException e) {
         plugin.getLogger().severe("Invalid webhook URL: " + e.getMessage());
      }
   }
}
