package com.namanseul.farmingmod.server.command;

import com.mojang.brigadier.context.CommandContext;
import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.NamanseulFarming;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HubCommandRegistrar {
    private HubCommandRegistrar() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("hub")
                        .requires(source -> source.hasPermission(0))
                        .executes(HubCommandRegistrar::executeHubOpen)
        );
    }

    private static int executeHubOpen(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        String requestId = UUID.randomUUID().toString();
        PacketDistributor.sendToPlayer(player, UiResponsePayload.openHub(requestId));
        context.getSource().sendSuccess(() -> Component.literal("Opening hub UI..."), false);

        if (Config.networkDebugLog()) {
            NamanseulFarming.LOGGER.info("[UI] /hub command sent OPEN packet requestId={} player={}",
                    requestId, player.getGameProfile().getName());
        }
        return 1;
    }
}
