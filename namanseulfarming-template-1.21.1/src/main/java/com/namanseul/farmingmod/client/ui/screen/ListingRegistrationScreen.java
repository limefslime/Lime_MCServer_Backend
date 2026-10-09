package com.namanseul.farmingmod.client.ui.screen;
import com.google.gson.JsonObject;
import com.namanseul.farmingmod.network.*;
import com.namanseul.farmingmod.network.payload.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;
public final class ListingRegistrationScreen extends BaseGameScreen {
 private final ShopScreen parent;private final int slot;private final ItemStack stack;private final String itemId;
 private EditBox quantity,price;private String requestId,message="";private JsonObject saved;private boolean waiting;
 public ListingRegistrationScreen(ShopScreen parent,int slot,ItemStack stack,String itemId){super(Component.literal("상품 등록"));this.parent=parent;this.slot=slot;this.stack=stack.copy();this.itemId=itemId;}
 @Override protected void init(){int x=width/2-100,y=height/2-44;quantity=addRenderableWidget(new EditBox(font,x,y,200,20,Component.literal("수량")));quantity.setValue(saved==null?"1":saved.get("quantity").getAsString());price=addRenderableWidget(new EditBox(font,x,y+42,200,20,Component.literal("개당 가격")));price.setValue(saved==null?"":saved.get("unitPrice").getAsString());
  addRenderableWidget(Button.builder(Component.literal("등록 / 재시도"),b->submit()).bounds(x,y+78,200,20).build());addRenderableWidget(Button.builder(Component.literal("돌아가기"),b->onClose()).bounds(x,y+104,200,20).build());quantity.setEditable(saved==null);price.setEditable(saved==null);
 }
 private void submit(){if(waiting)return;try{if(saved==null){int amount=Integer.parseInt(quantity.getValue()),unit=Integer.parseInt(price.getValue());if(amount<1||amount>1000||unit<1||((long)amount*unit)>Integer.MAX_VALUE)throw new IllegalArgumentException();saved=new JsonObject();saved.addProperty("itemId",itemId);saved.addProperty("quantity",amount);saved.addProperty("slot",slot);saved.addProperty("unitPrice",unit);requestId=UUID.randomUUID().toString();}waiting=true;quantity.setEditable(false);price.setEditable(false);PacketDistributor.sendToServer(new UiRequestPayload(requestId,UiScreenType.SHOP,UiAction.SHOP_REGISTER,saved.toString()));}catch(Exception e){message="수량과 개당 가격에 양의 정수를 입력하세요.";}}
 public void handleServerResponse(UiResponsePayload p){if(!p.requestId().equals(requestId))return;waiting=false;if(p.success()){
   JsonObject data=com.google.gson.JsonParser.parseString(p.dataJson()).getAsJsonObject();if(data.has("pending")&&data.get("pending").getAsBoolean()){message="등록 확인 대기 중입니다. 원래 요청을 재시도하세요.";return;}minecraft.setScreen(parent);parent.handleServerResponse(p);
  }else{message=p.error();saved=null;requestId=null;quantity.setEditable(true);price.setEditable(true);}}
 @Override protected void renderContents(GuiGraphics g,int mx,int my,float tick){int x=width/2-100,y=height/2-44;g.renderItem(stack,x,y-48);g.drawString(font,font.plainSubstrByWidth(stack.getHoverName().getString(),180),x+22,y-44,0xffffff);g.drawString(font,"수량",x,y-12,0xffffff);g.drawString(font,"개당 가격",x,y+30,0xffffff);g.drawCenteredString(font,waiting?"처리 중…":message,width/2,y+135,0xffdd88);if(mx>=x&&mx<x+18&&my>=y-48&&my<y-30)g.renderTooltip(font,stack,mx,my);}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
