package com.namanseul.farmingmod.server.admin;
import com.google.gson.*;
import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.network.*;
import com.namanseul.farmingmod.network.payload.*;
import com.namanseul.farmingmod.server.shop.ShopTradeJournal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
public final class AdminUiService {
 private static final String KEY="nfsAdminPending";
 private static final ExecutorService WORKERS=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"nfs-admin");t.setDaemon(true);return t;});
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
 private static final Set<UUID> BUSY=ConcurrentHashMap.newKeySet();private static int ticks;
 private AdminUiService(){}
 public static void handle(ServerPlayer player,UiRequestPayload request){
  if(!player.hasPermissions(2))throw new IllegalArgumentException("OP 권한이 필요합니다.");
  if(request.action()==UiAction.OPEN){reply(player,request,true,"{}",null);return;}
  if(!BUSY.add(player.getUUID()))throw new IllegalArgumentException("다른 관리자 요청을 처리 중입니다.");
  try{
   boolean execute=request.action()==UiAction.ADMIN_EXECUTE;
   if(!execute&&request.action()!=UiAction.ADMIN_CATALOG&&request.action()!=UiAction.ADMIN_READ)throw new IllegalArgumentException("지원하지 않는 관리 작업입니다.");
   JsonObject body=request.payloadJson()==null?new JsonObject():JsonParser.parseString(request.payloadJson()).getAsJsonObject();
   if(execute&&player.getPersistentData().contains(KEY))body=JsonParser.parseString(player.getPersistentData().getCompound(KEY).getString("body")).getAsJsonObject();
   else if(request.action()!=UiAction.ADMIN_CATALOG){
    JsonObject fields=body.has("fields")?body.getAsJsonObject("fields"):new JsonObject();body.add("fields",fields);
    if(fields.has("playerId")&&!fields.get("playerId").getAsString().isBlank()){
     String value=fields.get("playerId").getAsString();UUID id;
     try{id=UUID.fromString(value);}catch(Exception e){id=player.getServer().getProfileCache().get(value).orElseThrow(()->new IllegalArgumentException("플레이어를 찾을 수 없습니다. UUID를 사용하세요.")).getId();}
     fields.addProperty("playerId",id.toString());
    }
    if(execute){
     body.addProperty("requestId",UUID.fromString(request.requestId()).toString());
     if(fields.has("copyHeld")&&fields.get("copyHeld").getAsString().equals("true")){
      var held=player.getMainHandItem();if(held.isEmpty())throw new IllegalArgumentException("복구할 아이템을 주 손에 드세요.");
      fields.addProperty("itemId",BuiltInRegistries.ITEM.getKey(held.getItem()).toString());fields.addProperty("stack",held.copyWithCount(1).save(player.registryAccess()).toString());
     }
     if(fields.has("itemId")&&!fields.get("itemId").getAsString().isBlank()){
      var id=ResourceLocation.tryParse(fields.get("itemId").getAsString());var item=id==null?Items.AIR:BuiltInRegistries.ITEM.get(id);if(item==Items.AIR)throw new IllegalArgumentException("서버에 없는 아이템 ID입니다.");
      if(body.get("action").getAsString().equals("shop_save"))fields.addProperty("itemName",item.getDefaultInstance().getHoverName().getString());
     }
     CompoundTag saved=new CompoundTag();saved.putString("body",body.toString());saved.putString("clientId",request.requestId());player.getPersistentData().put(KEY,saved);ShopTradeJournal.savePlayer(player);
    }
   }
   final JsonObject input=body;final UUID id=player.getUUID();final var server=player.getServer();final String actorName=player.getGameProfile().getName();
   final String path=request.action()==UiAction.ADMIN_CATALOG?"/catalog":execute?"/execute":"/read";
   WORKERS.execute(()->{
    String response=null,error=null;boolean definitive=false,success=false;
    try{
     var builder=BackendAuthorization.authorize(HttpRequest.newBuilder(URI.create(Config.backendBaseUrl()+"/admin/manage"+path)))
      .setHeader("X-NFS-Admin-Id",id.toString()).setHeader("X-NFS-Admin-Name",actorName).timeout(Duration.ofSeconds(15));
     if(path.equals("/catalog"))builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(input.toString()));
     var result=HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());definitive=result.statusCode()<500&&result.statusCode()!=401&&result.statusCode()!=403&&result.statusCode()!=429;success=result.statusCode()>=200&&result.statusCode()<300;
     JsonElement parsed=JsonParser.parseString(result.body());
     if(success){response=parsed.toString();
      if(parsed.isJsonObject()&&parsed.getAsJsonObject().has("rows")){
       JsonObject out=parsed.getAsJsonObject();JsonArray rows=out.getAsJsonArray("rows");
       while(out.toString().length()>30000&&rows.size()>1){rows.remove(rows.size()-1);out.addProperty("hasMore",true);}
       response=out.toString();
      }
      if(response.length()>30000)throw new IllegalStateException("Response too large; narrow search");}
     else error=parsed.getAsJsonObject().has("message")?parsed.getAsJsonObject().get("message").getAsString():"관리 작업이 거절됐습니다.";
    }catch(Exception e){definitive=false;success=false;error="[PENDING] 처리 확인 대기 중입니다. 원래 요청을 보관했습니다.";}
    final String json=response,message=error;final boolean confirmed=definitive,ok=success;
    server.execute(()->{
     BUSY.remove(id);ServerPlayer online=server.getPlayerList().getPlayer(id);if(online==null)return;
     boolean done=confirmed,accepted=ok;String data=json,problem=message;
     try{
      if(ok&&input.has("action")){
       String action=input.get("action").getAsString();
       if(Set.of("game_pending","game_retry","listing_return").contains(action)){
        if(!online.hasPermissions(2))throw new IllegalStateException("관리 권한이 변경됐습니다.");
        JsonObject fields=input.getAsJsonObject("fields");ServerPlayer target=server.getPlayerList().getPlayer(UUID.fromString(fields.get("playerId").getAsString()));
        if(target==null)throw new IllegalStateException("인벤토리 복구 대상이 서버에 접속해야 합니다.");
        if(action.equals("game_pending")){JsonObject out=new JsonObject();JsonArray rows=new JsonArray();JsonObject state=ShopTradeJournal.describe(target);JsonObject pending=state.deepCopy();pending.remove("listings");rows.add(pending);rows.addAll(state.getAsJsonArray("listings"));out.add("rows",rows);data=out.toString();}
        else if(action.equals("game_retry"))ShopTradeJournal.retrySaved(target);
        else ShopTradeJournal.submitListing(target,new UiRequestPayload(input.get("requestId").getAsString(),UiScreenType.SHOP,UiAction.SHOP_CANCEL_SELL),fields.get("itemId").getAsString(),0,-1,0);
       }
      }
      if(execute&&done){CompoundTag previous=online.getPersistentData().getCompound(KEY).copy();online.getPersistentData().remove(KEY);try{ShopTradeJournal.savePlayer(online);}catch(Exception e){online.getPersistentData().put(KEY,previous);throw e;}}
     }catch(Exception e){accepted=false;done=false;problem="[PENDING] "+e.getMessage();}
     reply(online,request,accepted,data,problem);
    });
   });
  }catch(Exception e){BUSY.remove(player.getUUID());throw new IllegalArgumentException(e.getMessage(),e);}
 }
 public static void loggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){
  if(!(event.getEntity() instanceof ServerPlayer player))return;JsonObject body=new JsonObject();body.addProperty("playerId",player.getUUID().toString());body.addProperty("username",player.getGameProfile().getName());
  WORKERS.execute(()->{try{HTTP.send(BackendAuthorization.authorize(HttpRequest.newBuilder(URI.create(Config.backendBaseUrl()+"/players/sync"))).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.discarding());}catch(Exception ignored){}});
 }
 private static void reply(ServerPlayer p,UiRequestPayload r,boolean success,String data,String error){PacketDistributor.sendToPlayer(p,success?UiResponsePayload.successJson(r.requestId(),UiScreenType.ADMIN,r.action(),data):UiResponsePayload.failed(r.requestId(),UiScreenType.ADMIN,r.action(),error));}
 public static void tick(ServerTickEvent.Post event){if(++ticks%100!=0)return;for(ServerPlayer p:event.getServer().getPlayerList().getPlayers())if(p.hasPermissions(2)&&p.getPersistentData().contains(KEY)&&!BUSY.contains(p.getUUID())){
  CompoundTag saved=p.getPersistentData().getCompound(KEY);try{handle(p,new UiRequestPayload(saved.getString("clientId"),UiScreenType.ADMIN,UiAction.ADMIN_EXECUTE,saved.getString("body")));}catch(Exception ignored){}
 }}
}
