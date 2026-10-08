package com.namanseul.farmingmod.server.shop;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;

/** At-least-once local audit append; caller acknowledges only after this and player save succeed. */
public final class ListingAuditWriter {
    private ListingAuditWriter() {}
    public static void append(Path directory, JsonObject event) throws IOException {
        Instant time = Instant.parse(event.get("recordedAt").getAsString());
        Files.createDirectories(directory);
        Path file = directory.resolve(time.atZone(ZoneOffset.UTC).toLocalDate() + ".jsonl");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            try (var lock = channel.tryLock()) {
                if (lock == null) throw new IOException("listing audit file is busy");
                discardIncompleteTail(channel);
                channel.position(channel.size());
                ByteBuffer buffer = StandardCharsets.UTF_8.encode(event + "\n");
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
        }
        try (FileChannel folder = FileChannel.open(directory, StandardOpenOption.READ)) { folder.force(true); }
        try (FileChannel parent = FileChannel.open(directory.toAbsolutePath().getParent(), StandardOpenOption.READ)) { parent.force(true); }
    }
    private static void discardIncompleteTail(FileChannel channel) throws IOException {
        long end = channel.size();
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        while (end > 0) {
            long start = Math.max(0, end - buffer.capacity());
            buffer.clear();
            buffer.limit((int) (end - start));
            long position = start;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, position);
                if (read <= 0) throw new IOException("cannot inspect audit tail");
                position += read;
            }
            for (int i = buffer.limit() - 1; i >= 0; i--) {
                if (buffer.get(i) == '\n') {
                    channel.truncate(start + i + 1);
                    return;
                }
            }
            end = start;
        }
        channel.truncate(0);
    }

}
