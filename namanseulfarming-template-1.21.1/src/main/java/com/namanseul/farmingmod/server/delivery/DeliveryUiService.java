package com.namanseul.farmingmod.server.delivery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.UiScreenType;
import com.namanseul.farmingmod.network.payload.UiRequestPayload;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import com.namanseul.farmingmod.server.shop.ShopTradeJournal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DeliveryUiService {
    private static final Set<UUID> BUSY=ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ExecutorService HTTP=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"nfs-delivery-ui");t.setDaemon(true);return t;});
    private DeliveryUiService() {}
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { BUSY.clear(); }
    public static void handle(ServerPlayer player,UiRequestPayload request) {
        String playerId=player.getUUID().toString();
        UUID.fromString(request.requestId());
        JsonObject data=request.payloadJson()==null?new JsonObject():JsonParser.parseString(request.payloadJson()).getAsJsonObject();
        String id=switch(request.action()) {
            case DELIVERY_LIST->"";
            case DELIVERY_ACCEPT->data.get("templateId").getAsString();
            case DELIVERY_SUBMIT->UUID.fromString(data.get("contractId").getAsString()).toString();
            default->throw new IllegalArgumentException("unsupported delivery action");
        };
        if (id.length()>64 || (request.action()==UiAction.DELIVERY_ACCEPT && !id.matches("[a-z0-9_-]{1,64}")))
            throw new IllegalArgumentException("invalid delivery ID");
        if (!BUSY.add(player.getUUID())) throw new IllegalArgumentException("납품 요청을 처리 중입니다.");
        var server=player.getServer();
        HTTP.execute(()->{
            try {
                JsonObject result=switch(request.action()) {
                    case DELIVERY_LIST->BackendDeliveryBridge.list(playerId);
                    case DELIVERY_ACCEPT->BackendDeliveryBridge.accept(playerId,request.requestId(),id);
                    case DELIVERY_SUBMIT->BackendDeliveryBridge.detail(playerId,id);
                    default->throw new IllegalArgumentException("unsupported delivery action");
                };
                server.execute(()->{
                    try {
                        if(server.getPlayerList().getPlayer(player.getUUID())!=player)return;
                        if(request.action()==UiAction.DELIVERY_SUBMIT) ShopTradeJournal.submitDelivery(player,request,result);
                        else {
                            if(request.action()==UiAction.DELIVERY_LIST)result.addProperty("pendingTrade",ShopTradeJournal.hasPending(player));
                            PacketDistributor.sendToPlayer(player,UiResponsePayload.successJson(request.requestId(),UiScreenType.DELIVERY,request.action(),result.toString()));
                        }
                    } catch(Exception ex){replyError(player,request,ex.getMessage());}
                    finally {BUSY.remove(player.getUUID());}
                });
            } catch(Exception ex) {
                if(ex instanceof InterruptedException)Thread.currentThread().interrupt();
                server.execute(()->{try{if(server.getPlayerList().getPlayer(player.getUUID())==player)replyError(player,request,"납품 서버에 연결하지 못했습니다. 새로고침해주세요.");}finally{BUSY.remove(player.getUUID());}});
            }
        });
    }
    private static void replyError(ServerPlayer player,UiRequestPayload request,String message){
        PacketDistributor.sendToPlayer(player,UiResponsePayload.failed(request.requestId(),UiScreenType.DELIVERY,request.action(),message));
    }
}
