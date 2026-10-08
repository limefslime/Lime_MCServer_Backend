import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.server.shop.ListingAuditWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class ListingAuditWriterTest {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("listing-audit-");
        try {
            Path directory=root.resolve("audit");
            JsonObject event=new JsonObject();
            event.addProperty("recordedAt","2026-10-08T02:00:00Z"); event.addProperty("auditId","player:request");
            event.addProperty("itemName","Named\nfish");
            ListingAuditWriter.append(directory,event); ListingAuditWriter.append(directory,event);
            var lines=Files.readAllLines(directory.resolve("2026-10-08.jsonl"));
            if(lines.size()!=2)throw new AssertionError();
            for(String line:lines)if(!JsonParser.parseString(line).getAsJsonObject().get("auditId").getAsString().equals("player:request"))throw new AssertionError();
            // A failed append can leave a partial line; retry preserves complete lines and replaces only the tail.
            Path file=directory.resolve("2026-10-08.jsonl");
            Files.writeString(file,"{partial",java.nio.file.StandardOpenOption.APPEND);
            ListingAuditWriter.append(directory,event);
            lines=Files.readAllLines(file);
            if(lines.size()!=3)throw new AssertionError();
            for(String line:lines)JsonParser.parseString(line).getAsJsonObject();
            Path emptyTail=root.resolve("tail"); Files.createDirectories(emptyTail);
            Files.writeString(emptyTail.resolve("2026-10-08.jsonl"),"incomplete");
            ListingAuditWriter.append(emptyTail,event);
            if(Files.readAllLines(emptyTail.resolve("2026-10-08.jsonl")).size()!=1)throw new AssertionError();
            Path blocked=root.resolve("blocked"); Files.writeString(blocked,"file");
            boolean failed=false;try {ListingAuditWriter.append(blocked,event);}catch(java.io.IOException expected){failed=true;}
            if(!failed || !Files.readString(blocked).equals("file"))throw new AssertionError();
            System.out.println("Listing audit file append passed");
        } finally {
            try(var paths=Files.walk(root)){for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(path);}
        }
    }
}
