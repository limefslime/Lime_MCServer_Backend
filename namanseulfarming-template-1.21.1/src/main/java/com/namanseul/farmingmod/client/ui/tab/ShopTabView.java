package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Shop");
    }

    @Override
    public List<Component> buildListEntries(@Nullable HubSummaryData summary) {
        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Shop UI ready for stage 2"));
        entries.add(Component.literal("Use Open Shop button to enter"));
        entries.add(Component.literal("Preview / Buy / Sell flow enabled"));
        if (summary != null) {
            entries.add(Component.literal("Hub price preview: " + summary.shopPricePreview()));
        }
        return entries;
    }

    @Override
    public List<Component> buildDetailLines(@Nullable HubSummaryData summary, int selectedIndex) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("[Shop]"));
        lines.add(Component.literal("Open Shop screen for list/detail/preview/buy/sell actions."));
        if (summary != null) {
            lines.add(Component.literal("Server summary price preview: " + summary.shopPricePreview()));
        }
        return lines;
    }
}