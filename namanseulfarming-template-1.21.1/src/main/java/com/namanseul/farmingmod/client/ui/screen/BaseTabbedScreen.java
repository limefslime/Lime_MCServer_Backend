package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.ui.widget.UiButton;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public abstract class BaseTabbedScreen extends BaseGameScreen {
    private final Map<String, Component> tabs = new LinkedHashMap<>();
    private final Map<String, Button> tabButtons = new LinkedHashMap<>();
    private String activeTabId;

    protected BaseTabbedScreen(Component title) {
        super(title);
    }

    protected void addTab(String tabId, Component label) {
        tabs.put(tabId, label);
        if (activeTabId == null) {
            activeTabId = tabId;
        }
    }

    protected void initTabButtons(int startX, int y, int width, int height, int gap) {
        tabButtons.clear();
        int index = 0;
        for (Map.Entry<String, Component> entry : tabs.entrySet()) {
            final String tabId = entry.getKey();
            Button tabButton = addRenderableWidget(UiButton.create(
                    entry.getValue(),
                    startX + index * (width + gap),
                    y,
                    width,
                    height,
                    button -> switchToTab(tabId)
            ));
            tabButtons.put(tabId, tabButton);
            index++;
        }
        refreshTabButtons();
    }

    protected void switchToTab(String tabId) {
        if (!tabs.containsKey(tabId) || tabId.equals(activeTabId)) {
            return;
        }
        activeTabId = tabId;
        refreshTabButtons();
        onTabChanged(tabId);
    }

    protected String activeTabId() {
        return activeTabId;
    }

    protected void setInitialTab(String tabId) {
        if (tabs.containsKey(tabId)) {
            activeTabId = tabId;
        }
    }

    private void refreshTabButtons() {
        for (Map.Entry<String, Button> entry : tabButtons.entrySet()) {
            entry.getValue().active = !entry.getKey().equals(activeTabId);
        }
    }

    protected abstract void onTabChanged(String tabId);
}
