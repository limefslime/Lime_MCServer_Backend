package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public interface HubTabView {
    Component tabLabel();

    void openFromHub(GameHubScreen hubScreen);

    default List<Component> buildEntryHints(@Nullable HubSummaryData summary) {
        return List.of();
    }
}
