package com.namanseul.farmingmod.client.ui.screen;
import com.google.gson.*;
import com.namanseul.farmingmod.network.*;
import com.namanseul.farmingmod.network.payload.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
public final class AdminScreen extends BaseGameScreen {
 private JsonArray operations=new JsonArray(),rows=new JsonArray();private int selected,fieldPage,rowPage;
 private final Map<String,String> values=new HashMap<>();private final Map<String,EditBox> editors=new HashMap<>();
 private String section="wallet",message="",pendingId;private JsonObject pendingBody;private boolean waiting;
 public AdminScreen(){super(Component.literal("경제 관리"));}
 private JsonObject operation(){return operations.isEmpty()?null:operations.get(selected).getAsJsonObject();}
 @Override protected void init(){drawControls();if(operations.isEmpty())request(UiAction.ADMIN_CATALOG,new JsonObject(),null);}
 private Button button(String label,int x,int y,int w,Runnable run){return addRenderableWidget(Button.builder(Component.literal(label),b->run.run()).bounds(x,y,w,20).build());}
 private void capture(){editors.forEach((k,e)->values.put(k,e.getValue()));}
 private void drawControls(){clearWidgets();editors.clear();int left=18,right=width/2+12;
  button("돌아가기",width-88,8,70,this::onClose);
  button("분류: "+display(section),left,36,160,()->{if(waiting||pendingBody!=null)return;capture();List<String> sections=operations.asList().stream().map(e->e.getAsJsonObject().get("section").getAsString()).distinct().toList();if(sections.isEmpty())return;section=sections.get((sections.indexOf(section)+1)%sections.size());selected=0;for(int i=0;i<operations.size();i++)if(operations.get(i).getAsJsonObject().get("section").getAsString().equals(section)){selected=i;break;}fieldPage=0;values.clear();drawControls();});
  button("<",left+166,36,24,()->change(-1));button(">",left+194,36,24,()->change(1));
  JsonObject op=operation();if(op==null)return;
  JsonArray fields=op.getAsJsonArray("fields");int count=Math.max(1,(height-150)/39);int start=fieldPage*count;
  for(int i=start;i<Math.min(fields.size(),start+count);i++){
   JsonObject f=fields.get(i).getAsJsonObject();String key=f.get("key").getAsString(),value=values.getOrDefault(key,f.get("default").getAsString());values.putIfAbsent(key,value);int y=88+(i-start)*39;
   if(f.get("type").getAsString().equals("choice")){JsonArray choices=f.getAsJsonArray("choices");button(display(value),right,y,width-right-18,()->{capture();int index=0;for(int k=0;k<choices.size();k++)if(choices.get(k).getAsString().equals(values.get(key)))index=k;values.put(key,choices.get((index+1)%choices.size()).getAsString());drawControls();});}
   else{EditBox edit=new EditBox(font,right,y,width-right-18,20,Component.literal(f.get("label").getAsString()));edit.setMaxLength(key.equals("message")?4000:500);edit.setValue(value);edit.setEditable(!waiting&&pendingBody==null);editors.put(key,edit);addRenderableWidget(edit);}
  }
  button("<",right,height-54,30,()->{capture();fieldPage=Math.max(0,fieldPage-1);drawControls();});button(">",right+34,height-54,30,()->{capture();fieldPage=Math.min(Math.max(0,(fields.size()-1)/count),fieldPage+1);drawControls();});
  button("손 아이템",right+68,height-54,Math.max(44,width-right-86),()->{if(waiting||pendingBody!=null||minecraft.player==null)return;capture();ItemStack held=minecraft.player.getMainHandItem();if(held.isEmpty())return;values.put("itemId",BuiltInRegistries.ITEM.getKey(held.getItem()).toString());values.put("itemName",held.getHoverName().getString());drawControls();});
  button(pendingBody!=null?"원래 요청 재시도":op.get("write").getAsBoolean()?"변경 확인":"조회",right,height-28,width-right-18,this::submit);
  int per=Math.max(1,(height-158)/24),offset=rowPage*per;
  for(int i=offset;i<Math.min(rows.size(),offset+per);i++){JsonObject row=rows.get(i).getAsJsonObject();String label=rowLabel(row);button(font.plainSubstrByWidth(label,width/2-70),left,88+(i-offset)*24,width/2-36,()->selectRow(row));}
  button("이전 목록",left,height-54,72,()->{rowPage=Math.max(0,rowPage-1);drawControls();});button("다음 목록",left+78,height-54,72,()->{rowPage=Math.min(Math.max(0,(rows.size()-1)/per),rowPage+1);drawControls();});
 }
 private void change(int direction){if(waiting||pendingBody!=null||operations.isEmpty())return;int next=selected;do{next=Math.floorMod(next+direction,operations.size());}while(!operations.get(next).getAsJsonObject().get("section").getAsString().equals(section)&&next!=selected);selected=next;fieldPage=0;values.clear();drawControls();}
 private void selectRow(JsonObject row){if(waiting||pendingBody!=null)return;capture();for(var entry:row.entrySet())if(entry.getValue().isJsonPrimitive()){
  String key=entry.getKey();StringBuilder camel=new StringBuilder();boolean upper=false;for(char c:key.toCharArray()){if(c=='_'){upper=true;continue;}camel.append(upper?Character.toUpperCase(c):c);upper=false;}values.put(camel.toString(),entry.getValue().getAsString());}
  if(row.has("balance")&&row.has("id")){values.put("playerId",row.get("id").getAsString());values.put("expectedBalance",row.get("balance").getAsString());}
  drawControls();
 }
 private static String display(String value){return switch(value){
  case "wallet"->"지갑";case "shop"->"상점";case "rules"->"경제 설정";case "delivery"->"납품 의뢰";case "reward"->"보상";case "mail"->"우편";case "project"->"프로젝트";case "event"->"이벤트";case "region"->"구역";case "logs"->"기록";case "recovery"->"복구";case "permissions"->"관리 권한";
  case "agri"->"농업";case "port"->"어업";case "industry"->"산업";case "global"->"전체 구역";case "misc"->"기타";case "none"->"없음";
  case "true"->"켜짐";case "false"->"꺼짐";case "draft"->"준비 중";case "active"->"활성";case "ended"->"종료";
  case "price_bonus"->"가격 보정";case "xp_bonus"->"경험치 보정";case "focus_bonus"->"집중 구역 보정";default->value;};}
 private static String rowLabel(JsonObject row){
  if(row.has("amount")&&row.has("type"))return (row.get("type").getAsString().equals("add")?"지급 +":"차감 -")+row.get("amount").getAsString()+" · "+row.get("player_id").getAsString();for(String key:new String[]{"username","item_name","itemName","name","title","action","id","playerId"})if(row.has(key)&&!row.get(key).isJsonNull())return row.get(key).getAsString()+(row.has("balance")?" | "+row.get("balance").getAsString():"");return row.toString();}
 private void submit(){if(waiting||operation()==null)return;capture();if(pendingBody!=null){request(UiAction.ADMIN_EXECUTE,pendingBody,pendingId);return;}JsonObject op=operation(),fields=new JsonObject();for(JsonElement f:op.getAsJsonArray("fields")){String key=f.getAsJsonObject().get("key").getAsString();fields.addProperty(key,values.getOrDefault(key,f.getAsJsonObject().get("default").getAsString()));}
  JsonObject body=new JsonObject();body.addProperty("action",op.get("action").getAsString());body.add("fields",fields);
  if(!op.get("write").getAsBoolean()){request(UiAction.ADMIN_READ,body,null);return;}
  StringBuilder preview=new StringBuilder();for(JsonElement e:op.getAsJsonArray("fields")){JsonObject f=e.getAsJsonObject();preview.append(f.get("label").getAsString()).append(": ").append(fields.get(f.get("key").getAsString()).getAsString()).append("\n");}
  minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes){pendingId=UUID.randomUUID().toString();pendingBody=body;request(UiAction.ADMIN_EXECUTE,body,pendingId);}},Component.literal(op.get("label").getAsString()),Component.literal(preview.toString())));
 }
 private void request(UiAction action,JsonObject body,String id){waiting=true;PacketDistributor.sendToServer(new UiRequestPayload(id==null?UUID.randomUUID().toString():id,UiScreenType.ADMIN,action,body.toString()));}
 public void handleServerResponse(UiResponsePayload payload){waiting=false;if(!payload.success()){message=payload.error();if(message!=null&&!message.startsWith("[PENDING]")){pendingBody=null;pendingId=null;}drawControls();return;}JsonObject data=JsonParser.parseString(payload.dataJson()).getAsJsonObject();
  if(payload.action()==UiAction.ADMIN_CATALOG){operations=data.getAsJsonArray("operations");if(!operations.isEmpty())section=operation().get("section").getAsString();}
  else if(payload.action()==UiAction.ADMIN_EXECUTE){pendingBody=null;pendingId=null;message="작업 완료 · 요청 번호 "+data.get("requestId").getAsString();}
  else{rows=data.has("rows")?data.getAsJsonArray("rows"):new JsonArray();if(data.has("ledger"))rows.addAll(data.getAsJsonArray("ledger"));rowPage=0;if(!rows.isEmpty())selectRow(rows.get(0).getAsJsonObject());message=rows.size()+"개 항목을 불러왔습니다.";}
  drawControls();
 }
 @Override protected void renderContents(GuiGraphics g,int mx,int my,float tick){g.drawCenteredString(font,title,width/2,16,0xffffff);JsonObject op=operation();if(op!=null){g.drawString(font,font.plainSubstrByWidth(op.get("label").getAsString(),width-36),18,64,0xffffaa);JsonArray fields=op.getAsJsonArray("fields");int count=Math.max(1,(height-150)/39);for(int i=fieldPage*count;i<Math.min(fields.size(),(fieldPage+1)*count);i++)g.drawString(font,fields.get(i).getAsJsonObject().get("label").getAsString(),width/2+12,76+(i-fieldPage*count)*39,0xffffff);}
  g.drawString(font,font.plainSubstrByWidth(waiting?"처리 중…":message,width-36),18,height-72,0xffdd88);
 }
 @Override public void render(GuiGraphics g,int mx,int my,float tick){
  super.render(g,mx,my,tick);int per=Math.max(1,(height-158)/24),start=rowPage*per;
  for(int i=start;i<Math.min(rows.size(),start+per);i++){
   JsonObject row=rows.get(i).getAsJsonObject();int y=88+(i-start)*24;
   if(row.has("item_id")){ResourceLocation id=ResourceLocation.tryParse(row.get("item_id").getAsString());Item item=id==null?Items.AIR:BuiltInRegistries.ITEM.get(id);if(item!=Items.AIR)g.renderItem(item.getDefaultInstance(),22,y+2);}
   if(mx>=18&&mx<width/2-18&&my>=y&&my<y+20){java.util.List<Component> tip=new java.util.ArrayList<>();tip.add(Component.literal(rowLabel(row)));if(row.has("item_id"))tip.add(Component.literal(row.get("item_id").getAsString()));if(row.has("reason"))tip.add(Component.literal(row.get("reason").getAsString()));if(row.has("pending"))tip.add(Component.literal("미완료 거래: "+row.get("pending").getAsString()));if(row.has("created_at"))tip.add(Component.literal(row.get("created_at").getAsString()));g.renderTooltip(font,tip,java.util.Optional.empty(),mx,my);}
  }
 }
 @Override public void onClose(){minecraft.setScreen(new GameHubScreen());}
}
