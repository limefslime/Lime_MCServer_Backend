package com.namanseul.farmingmod.server.invest;
import com.google.gson.*;
import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.server.admin.BackendAuthorization;
import com.namanseul.farmingmod.server.shop.ShopTradeJournal;
import com.namanseul.farmingmod.network.*;
import com.namanseul.farmingmod.network.payload.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.net.URI;import java.net.http.*;import java.time.Duration;import java.util.*;import java.util.concurrent.*;
public final class ProjectUiService{
 private static final String KEY="nfsInvestPending";private static int ticks;
 private static final Set<UUID> BUSY=ConcurrentHashMap.newKeySet();
 private static final ExecutorService WORKERS=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"nfs-projects");t.setDaemon(true);return t;});
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 private ProjectUiService(){}
 public static void handle(ServerPlayer player,UiRequestPayload request){
  if(request.action()==UiAction.OPEN){PacketDistributor.sendToPlayer(player,UiResponsePayload.openInvest(request.requestId()));return;}
  boolean write=request.action()==UiAction.INVEST_CONTRIBUTE;
  if(!write&&request.action()!=UiAction.INVEST_LIST&&request.action()!=UiAction.INVEST_REFRESH)throw new IllegalArgumentException("Unsupported project action");
  if(!BUSY.add(player.getUUID()))throw new IllegalArgumentException("Project request already processing");
  try{
   JsonObject body=request.payloadJson()==null?new JsonObject():JsonParser.parseString(request.payloadJson()).getAsJsonObject();String path="/invest/projects";
   if(write){
    if(player.getPersistentData().contains(KEY)){CompoundTag saved=player.getPersistentData().getCompound(KEY);body=JsonParser.parseString(saved.getString("body")).getAsJsonObject();path=saved.getString("path");}
    else{
     String projectId=UUID.fromString(body.get("projectId").getAsString()).toString();int amount=new java.math.BigDecimal(body.get("amount").getAsString()).intValueExact();if(amount<=0)throw new IllegalArgumentException("Amount must be positive");
     path+="/"+projectId+"/invest";body=new JsonObject();body.addProperty("playerId",player.getUUID().toString());body.addProperty("amount",amount);body.addProperty("requestId",UUID.fromString(request.requestId()).toString());
     CompoundTag saved=new CompoundTag();saved.putString("body",body.toString());saved.putString("path",path);saved.putString("clientId",request.requestId());player.getPersistentData().put(KEY,saved);ShopTradeJournal.savePlayer(player);
    }
   }
   final String endpoint=path,payload=body.toString();final UUID id=player.getUUID();final var server=player.getServer();
   WORKERS.execute(()->{
    String data=null,error=null;boolean ok=false,definitive=false;
    try{var builder=BackendAuthorization.authorize(HttpRequest.newBuilder(URI.create(Config.backendBaseUrl()+endpoint))).timeout(Duration.ofSeconds(15));if(write)builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload));else builder.GET();var res=HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());ok=res.statusCode()>=200&&res.statusCode()<300;definitive=res.statusCode()<500&&res.statusCode()!=401&&res.statusCode()!=403&&res.statusCode()!=429;JsonElement json=JsonParser.parseString(res.body());if(ok)data=json.toString();else error=json.getAsJsonObject().get("message").getAsString();}
    catch(Exception e){error="[PENDING] 기여 확인 대기 중입니다. 원래 요청을 보관했습니다.";}
    final String response=data,problem=error;final boolean success=ok,confirmed=definitive;
    server.execute(()->{BUSY.remove(id);ServerPlayer online=server.getPlayerList().getPlayer(id);if(online==null)return;
     if(write&&confirmed){CompoundTag saved=online.getPersistentData().getCompound(KEY).copy();online.getPersistentData().remove(KEY);try{ShopTradeJournal.savePlayer(online);}catch(Exception e){online.getPersistentData().put(KEY,saved);PacketDistributor.sendToPlayer(online,UiResponsePayload.failed(request.requestId(),UiScreenType.INVEST,request.action(),"[PENDING] 플레이어 정보를 저장하지 못했습니다."));return;}}
     PacketDistributor.sendToPlayer(online,success?UiResponsePayload.successJson(request.requestId(),UiScreenType.INVEST,request.action(),response):UiResponsePayload.failed(request.requestId(),UiScreenType.INVEST,request.action(),problem));
    });
   });
  }catch(Exception e){BUSY.remove(player.getUUID());throw new IllegalArgumentException(e.getMessage(),e);}
 }
 public static void tick(ServerTickEvent.Post event){if(++ticks%100!=0)return;for(ServerPlayer p:event.getServer().getPlayerList().getPlayers())if(p.getPersistentData().contains(KEY)&&!BUSY.contains(p.getUUID())){CompoundTag saved=p.getPersistentData().getCompound(KEY);try{handle(p,new UiRequestPayload(saved.getString("clientId"),UiScreenType.INVEST,UiAction.INVEST_CONTRIBUTE,saved.getString("body")));}catch(Exception ignored){}}}
}
