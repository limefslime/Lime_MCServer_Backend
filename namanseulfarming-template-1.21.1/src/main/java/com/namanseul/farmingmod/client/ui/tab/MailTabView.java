package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import com.namanseul.farmingmod.client.ui.screen.MailScreen;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MailTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Mail");
    }

    @Override
    public void openFromHub(GameHubScreen hubScreen) {
        Minecraft.getInstance().setScreen(new MailScreen(hubScreen));
    }

    @Override
    public List<Component> buildEntryHints(@Nullable HubSummaryData summary) {
        if (summary == null) {
            return List.of(Component.literal("Mailbox signals are loading."));
        }

        int unclaimed = summary.unclaimedMailCount();
        if (unclaimed > 0) {
            return List.of(
                    Component.literal("Rewards are waiting in your mailbox."),
                    Component.literal("Unclaimed rewards: " + unclaimed)
            );
        }
        return List.of(
                Component.literal("No pending rewards right now."),
                Component.literal("Open Mail only when you expect new rewards.")
        );
    }
}
