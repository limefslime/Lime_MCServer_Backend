package com.namanseul.farmingmod;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.namanseul.farmingmod.network.ModNetwork;
import com.namanseul.farmingmod.server.command.HubCommandRegistrar;
import com.namanseul.farmingmod.server.command.QuestRewardCommand;
import com.namanseul.farmingmod.server.shop.ShopTradeJournal;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(NamanseulFarming.MODID)
public class NamanseulFarming {
    public static final String MODID = "namanseulfarming";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NamanseulFarming(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(HubCommandRegistrar::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(QuestRewardCommand::register);
        NeoForge.EVENT_BUS.addListener(QuestRewardCommand::tick);
        NeoForge.EVENT_BUS.addListener(ShopTradeJournal::tick);
        NeoForge.EVENT_BUS.addListener(ShopTradeJournal::stopped);
        NeoForge.EVENT_BUS.addListener(ShopTradeJournal::clonePlayer);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Namanseul Farming common setup complete.");
    }
}
