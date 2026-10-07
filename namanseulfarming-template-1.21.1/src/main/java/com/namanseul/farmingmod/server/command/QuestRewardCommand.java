package com.namanseul.farmingmod.server.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.NamanseulFarming;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-only reward command. Persist before FTB marks a reward claimed;
 * retry the same request UUID after restarts or a lost HTTP response. */
public final class QuestRewardCommand {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static long lastWarning;
    private static int ticks;
    private QuestRewardCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nfsreward")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("rewardId", StringArgumentType.word()).executes(context -> {
                    String rewardId = StringArgumentType.getString(context, "rewardId");
                    if (!rewardId.matches("[0-9A-F]{16}")) return 0;
                    var player = EntityArgument.getPlayer(context, "player");
                    Path directory = context.getSource().getServer().getWorldPath(LevelResource.ROOT).resolve("nfs-rewards");
                    try {
                        Files.createDirectories(directory);
                        // One pending claim per player/reward. Cooldowns are authoritative in PostgreSQL.
                        Path destination = directory.resolve(player.getUUID() + "-" + rewardId + ".json");
                        if (!Files.exists(destination)) {
                            JsonObject body = new JsonObject();
                            body.addProperty("playerId", player.getUUID().toString());
                            body.addProperty("rewardId", rewardId);
                            body.addProperty("requestId", UUID.randomUUID().toString());
                            Path temporary = Files.createTempFile(directory, "claim-", ".tmp");
                            try {
                                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                                    ByteBuffer bytes = StandardCharsets.UTF_8.encode(body.toString());
                                    while (bytes.hasRemaining()) channel.write(bytes);
                                    channel.force(true);
                                }
                                try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
                                catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, destination); }
                            } finally { Files.deleteIfExists(temporary); }
                        }
                        return 1;
                    } catch (Exception ex) {
                        context.getSource().sendFailure(Component.literal("Reward could not be queued; check server storage."));
                        throw new IllegalStateException("Cannot persist quest reward", ex);
                    }
                }))));
    }

    public static void tick(ServerTickEvent.Post event) {
        if (++ticks < 100) return;
        ticks = 0;
        String base = Config.backendBaseUrl();
        String token = System.getenv("NFS_INTEGRATION_API_TOKEN");
        if (base.isBlank() || token == null || token.length() < 32) return;
        Path directory = event.getServer().getWorldPath(LevelResource.ROOT).resolve("nfs-rewards");
        if (!Files.isDirectory(directory) || !BUSY.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).limit(100).toList()) {
                    try {
                        String body = Files.readString(file);
                        JsonObject request = JsonParser.parseString(body).getAsJsonObject();
                        HttpRequest http = HttpRequest.newBuilder(URI.create(base.replaceAll("/+$", "") + "/integration/rewards"))
                                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + token)
                                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                        HttpResponse<String> response = HTTP.send(http, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            JsonObject receipt = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (!request.get("requestId").getAsString().equals(receipt.get("requestId").getAsString())
                                    || !request.get("playerId").getAsString().equals(receipt.get("playerId").getAsString())
                                    || !request.get("rewardId").getAsString().equals(receipt.get("rewardId").getAsString())
                                    || !receipt.has("granted") || !receipt.get("granted").isJsonPrimitive()
                                    || !receipt.get("granted").getAsJsonPrimitive().isBoolean()) {
                                throw new IllegalStateException("Mismatched reward receipt");
                            }
                            Files.delete(file);
                        } else { warnPending(); }
                    } catch (Exception ex) { warnPending(); }
                }
            } catch (Exception ex) { warnPending(); }
            finally { BUSY.set(false); }
        });
    }

    private static void warnPending() {
        if (System.currentTimeMillis() - lastWarning > 60000) {
            lastWarning = System.currentTimeMillis();
            NamanseulFarming.LOGGER.warn("Quest rewards remain queued; check integration API, policy and server storage.");
        }
    }
}
