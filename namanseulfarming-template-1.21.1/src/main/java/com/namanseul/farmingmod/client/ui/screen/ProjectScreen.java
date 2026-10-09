package com.namanseul.farmingmod.client.ui.screen;
import com.google.gson.*;
import com.namanseul.farmingmod.network.*;
import com.namanseul.farmingmod.network.payload.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;
public final class ProjectScreen extends BaseGameScreen {
 private JsonArray projects=new JsonArray();private int selected,page,x,y,w,h;private EditBox amount;private String message="",savedAmount="100";private boolean waiting;
 public ProjectScreen(){super(Component.literal("공동 프로젝트"));}
 public static ProjectScreen openStandalone(){ProjectScreen screen=new ProjectScreen();Minecraft.getInstance().setScreen(screen);return screen;}
 private int perPage(){return Math.max(1,(h-128)/28);}
 @Override protected void init(){w=Math.min(500,width-20);h=Math.min(300,height-34);x=(width-w)/2;y=(height-h)/2;drawControls();if(!waiting)refresh();}
 private void drawControls(){if(amount!=null)savedAmount=amount.getValue();clearWidgets();
  amount=addRenderableWidget(new EditBox(font,x+w/2-65,y+h-54,130,19,Component.literal("기여 금액")));amount.setValue(savedAmount);
  addRenderableWidget(Button.builder(Component.literal("기여하기"),b->contribute()).bounds(x+w/2-65,y+h-30,130,20).build()).active=!waiting&&!projects.isEmpty();
  addRenderableWidget(Button.builder(Component.literal("새로고침"),b->refresh()).bounds(x+10,y+28,65,19).build()).active=!waiting;
  addRenderableWidget(Button.builder(Component.literal("돌아가기"),b->onClose()).bounds(x+w-75,y+28,65,19).build());
  addRenderableWidget(Button.builder(Component.literal("이전"),b->{page--;drawControls();}).bounds(x+81,y+28,35,19).build()).active=!waiting&&page>0;
  addRenderableWidget(Button.builder(Component.literal("다음"),b->{page++;drawControls();}).bounds(x+120,y+28,35,19).build()).active=!waiting&&(page+1)*perPage()<projects.size();
 }
 private void refresh(){if(waiting)return;send(UiAction.INVEST_LIST,new JsonObject());}
 private void contribute(){if(waiting||projects.isEmpty())return;try{int value=Integer.parseInt(amount.getValue());if(value<=0)throw new IllegalArgumentException();JsonObject body=new JsonObject();body.addProperty("projectId",projects.get(selected).getAsJsonObject().get("id").getAsString());body.addProperty("amount",value);send(UiAction.INVEST_CONTRIBUTE,body);}catch(Exception e){message="1 이상의 정수 금액을 입력하세요.";}}
 private void send(UiAction action,JsonObject body){waiting=true;PacketDistributor.sendToServer(new UiRequestPayload(UUID.randomUUID().toString(),UiScreenType.INVEST,action,body.toString()));drawControls();}
 public void handleServerResponse(UiResponsePayload p){waiting=false;if(!p.success()){message=p.error();drawControls();return;}if(p.action()==UiAction.INVEST_CONTRIBUTE){message="기여 완료";refresh();return;}JsonElement data=JsonParser.parseString(p.dataJson());projects=data.isJsonArray()?data.getAsJsonArray():data.getAsJsonObject().getAsJsonArray("items");selected=Math.min(selected,Math.max(0,projects.size()-1));page=0;drawControls();}
 @Override protected void renderContents(GuiGraphics g,int mx,int my,float tick){renderPanel(g,x,y,w,h);g.drawString(font,title,x+10,y+10,0xffffff);g.drawString(font,(page+1)+" / "+Math.max(1,(projects.size()+perPage()-1)/perPage())+" 페이지",x+163,y+33,0xaaddff);
  int count=perPage(),start=page*count;for(int i=start;i<Math.min(projects.size(),start+count);i++){JsonObject row=projects.get(i).getAsJsonObject();int ry=y+57+(i-start)*28;g.fill(x+10,ry,x+w-10,ry+25,i==selected?0xff314469:0xff1c2536);g.drawString(font,font.plainSubstrByWidth(row.get("name").getAsString(),w-32),x+15,ry+3,0xffffff);String text=row.has("currentAmount")?row.get("currentAmount")+" / "+row.get("targetAmount")+"원":"기여금 확인 중";g.drawString(font,font.plainSubstrByWidth(text,w-32),x+15,ry+14,0xaaddff);}
  if(projects.isEmpty())g.drawCenteredString(font,waiting?"불러오는 중…":"진행 중인 프로젝트가 없습니다.",x+w/2,y+80,0xbbccdd);
  g.drawString(font,font.plainSubstrByWidth(waiting?"처리 중…":message,w-20),x+10,y+h-69,0xffdd88);
 }
 @Override public boolean mouseClicked(double mx,double my,int b){int index=page*perPage()+(int)(my-y-57)/28;if(!waiting&&b==0&&mx>=x+10&&mx<=x+w-10&&my>=y+57&&my<y+57+perPage()*28&&index<projects.size()){selected=index;return true;}return super.mouseClicked(mx,my,b);}
 @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){if(!waiting&&mx>=x&&mx<x+w&&my>=y+57&&my<y+h-70){page=Math.max(0,Math.min(Math.max(0,(projects.size()-1)/perPage()),page+(sy<0?1:-1)));drawControls();return true;}return super.mouseScrolled(mx,my,sx,sy);}
 @Override public void onClose(){minecraft.setScreen(new GameHubScreen());}
}
