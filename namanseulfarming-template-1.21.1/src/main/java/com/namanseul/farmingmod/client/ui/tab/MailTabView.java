package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MailTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Mail");
    }

    @Override
    public Component openButtonLabel() {
        return Component.translatable("screen.namanseulfarming.mail.open_button");
    }

    @Override
    public Component actionTitle() {
        return Component.literal("Open Mailbox");
    }

    @Override
    public Component actionHint() {
        return Component.literal("Claim pending rewards and clear unread items.");
    }

    @Override
    public List<Component> summaryLines(@Nullable HubSummaryData summary) {
        if (summary == null) {
            return List.of(Component.literal("Loading mailbox snapshot..."));
        }

        return List.of(
                Component.literal("Unclaimed rewards: " + summary.unclaimedMailCount()),
                Component.literal("Visit mailbox if this number is not zero.")
        );
    }
}
