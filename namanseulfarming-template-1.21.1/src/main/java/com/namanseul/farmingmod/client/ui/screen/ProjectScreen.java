package com.namanseul.farmingmod.client.ui.screen;
import com.google.gson.*;
import com.namanseul.farmingmod.network.*;
import com.namanseul.farmingmod.network.payload.*;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.*;import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;
public final class ProjectScreen extends BaseGameScreen{
 private JsonArray projects=new JsonArray();private int selected,page;private EditBox amount;private String message="";private boolean waiting;
 public ProjectScreen(){super(Component.literal("공동 프로젝트"));}
 public static ProjectScreen openStandalone(){ProjectScreen screen=new ProjectScreen();Minecraft.getInstance().setScreen(screen);return screen;}
 @Override protected void init(){amount=addRenderableWidget(new EditBox(font,width/2-70,height-63,140,20,Component.literal("기여 금액")));amount.setValue("100");
  addRenderableWidget(Button.builder(Component.literal("기여하기"),b->contribute()).bounds(width/2-70,height-38,140,20).build());
  addRenderableWidget(Button.builder(Component.literal("새로고침"),b->refresh()).bounds(18,36,70,20).build());addRenderableWidget(Button.builder(Component.literal("돌아가기"),b->onClose()).bounds(width-88,36,70,20).build());
  addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);}).bounds(94,36,22,20).build());addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(Math.max(0,(projects.size()-1)/perPage()),page+1);}).bounds(120,36,22,20).build());refresh();}
 private int perPage(){return Math.max(1,(height-157)/30);}
 private void refresh(){if(waiting)return;send(UiAction.INVEST_LIST,new JsonObject());}
 private void contribute(){if(waiting||projects.isEmpty())return;try{int value=Integer.parseInt(amount.getValue());if(value<=0)throw new IllegalArgumentException();JsonObject body=new JsonObject();body.addProperty("projectId",projects.get(selected).getAsJsonObject().get("id").getAsString());body.addProperty("amount",value);send(UiAction.INVEST_CONTRIBUTE,body);}catch(Exception e){message="양의 정수 금액을 입력하세요.";}}
 private void send(UiAction action,JsonObject body){waiting=true;PacketDistributor.sendToServer(new UiRequestPayload(UUID.randomUUID().toString(),UiScreenType.INVEST,action,body.toString()));}
 public void handleServerResponse(UiResponsePayload p){waiting=false;if(!p.success()){message=p.error();return;}if(p.action()==UiAction.INVEST_CONTRIBUTE){message="기여 완료";refresh();return;}JsonElement data=JsonParser.parseString(p.dataJson());projects=data.isJsonArray()?data.getAsJsonArray():data.getAsJsonObject().getAsJsonArray("items");selected=Math.min(selected,Math.max(0,projects.size()-1));page=0;}
 @Override protected void renderContents(GuiGraphics g,int mx,int my,float tick){g.drawCenteredString(font,title,width/2,16,0xffffff);int count=perPage(),start=page*count;for(int i=start;i<Math.min(projects.size(),start+count);i++){JsonObject row=projects.get(i).getAsJsonObject();int y=66+(i-start)*30;g.fill(18,y,width-18,y+27,i==selected?0xff314469:0xff1c2536);g.drawString(font,font.plainSubstrByWidth(row.get("name").getAsString(),width-52),24,y+3,0xffffff);String text=row.has("currentAmount")?row.get("currentAmount")+" / "+row.get("targetAmount"):row.toString();g.drawString(font,font.plainSubstrByWidth(text,width-52),24,y+15,0xaaddff);}g.drawString(font,font.plainSubstrByWidth(waiting?"처리 중…":message,width-36),18,height-88,0xffdd88);}
 @Override public boolean mouseClicked(double x,double y,int b){int index=page*perPage()+(int)(y-66)/30;if(!waiting&&x>=18&&x<=width-18&&y>=66&&y<height-91&&index<projects.size()){selected=index;return true;}return super.mouseClicked(x,y,b);}
 @Override public void onClose(){minecraft.setScreen(new GameHubScreen());}
}
