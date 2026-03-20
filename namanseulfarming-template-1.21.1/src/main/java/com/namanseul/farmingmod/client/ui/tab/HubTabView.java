package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public interface HubTabView {
    Component tabLabel();

    List<Component> buildListEntries(@Nullable HubSummaryData summary);

    List<Component> buildDetailLines(@Nullable HubSummaryData summary, int selectedIndex);
}
