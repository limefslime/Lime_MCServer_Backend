package com.namanseul.farmingmod.client.ui.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

public record PlayerOverviewData(
        JsonObject wallet,
        JsonObject activity,
        JsonObject summary,
        boolean partial,
        List<String> partialNotes
) {
    public JsonArray activityItems() {
        if (activity == null) {
            return new JsonArray();
        }
        var items = activity.get("items");
        return items != null && items.isJsonArray() ? items.getAsJsonArray() : new JsonArray();
    }
}

