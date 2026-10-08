package com.namanseul.farmingmod.server.shop;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Executes a listing mutation once. The caller commits receipt and inventory in the same save. */
public final class ListingRequestReceipts {
    private ListingRequestReceipts() {}
    public record Request(String requestId, String action, String itemId, int quantity, int slot) {
        public Request {
            if (!UUID.fromString(requestId).toString().equals(requestId)) throw new IllegalArgumentException("invalid listing request UUID");
            if (!action.equals("register") && !action.equals("cancel")) throw new IllegalArgumentException("invalid listing action");
            if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("itemId is required");
            if (action.equals("register") ? quantity <= 0 : quantity != 0 || slot != -1) {
                throw new IllegalArgumentException("invalid listing quantity or slot");
            }
        }
    }
    public record Receipt(Request request, JsonObject result) {}

    public static JsonObject perform(Request request, Receipt existing, Supplier<JsonObject> mutation, Consumer<Receipt> commit) {
        if (existing != null) {
            if (!request.equals(existing.request())) throw new IllegalArgumentException("listing request ID reused for another operation");
            JsonObject result = existing.result().deepCopy();
            result.addProperty("replayed", true);
            return result;
        }
        JsonObject result = mutation.get().deepCopy();
        result.addProperty("replayed", false);
        commit.accept(new Receipt(request, result.deepCopy()));
        return result;
    }
}
