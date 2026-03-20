package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public interface HubTabView {
    Component tabLabel();

    Component openButtonLabel();

    Component actionTitle();

    Component actionHint();

    List<Component> summaryLines(@Nullable HubSummaryData summary);
}
