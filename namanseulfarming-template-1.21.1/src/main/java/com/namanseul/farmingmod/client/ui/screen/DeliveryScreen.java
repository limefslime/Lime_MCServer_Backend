package com.namanseul.farmingmod.client.ui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.widget.UiButton;
import com.namanseul.farmingmod.client.ui.widget.UiListPanel;
import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

public final class DeliveryScreen extends BaseGameScreen {
    private final Screen parent;
    private final List<JsonObject> entries=new ArrayList<>();
    private JsonObject data=new JsonObject();
    private String tab="active", readId, mutationId;
    private boolean loading, waiting;
    private long sentAt, nextPoll;
    private UiListPanel list;
    private Button accept, deliver;
    private int x,y,w,h,listWidth;
    public DeliveryScreen(Screen parent){super(Component.literal("납품 의뢰"));this.parent=parent;}
    @Override protected void init(){
        super.init(); w=Math.min(560,width-20);h=Math.min(310,height-24);x=(width-w)/2;y=(height-h)/2;
        listWidth=Math.max(100,(w-28)*38/100);
        initCommonButtons(x+w-4,y+8);initRefreshButton(x+w-4,y+8);
        closeButton.setMessage(Component.literal("뒤로"));
        String[] keys={"available","active","completed"};String[] labels={"받을 수 있는 의뢰","수락한 의뢰","완료 기록"};
        int tabWidth=(w-28)/3;
        for(int i=0;i<keys.length;i++){
            String key=keys[i];addRenderableWidget(UiButton.create(Component.literal(labels[i]),x+10+i*(tabWidth+4),y+36,tabWidth,20,b->{tab=key;updateEntries();}));
        }
        list=new UiListPanel(x+10,y+68,listWidth,h-108,22);
        accept=addRenderableWidget(UiButton.create(Component.literal("의뢰 수락"),x+10,y+h-30,100,20,b->mutate(UiAction.DELIVERY_ACCEPT)));
        deliver=addRenderableWidget(UiButton.create(Component.literal("보유 물품 납품"),x+116,y+h-30,112,20,b->mutate(UiAction.DELIVERY_SUBMIT)));
        updateEntries(); refresh();
    }
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    @Override protected void onRefreshPressed(){refresh();}
    private void refresh(){
        if(loading)return;loading=true;sentAt=System.currentTimeMillis();
        readId=UiClientNetworking.requestDelivery(UiAction.DELIVERY_LIST,null);buttons();
    }
    private JsonObject selected(){return entries.isEmpty()?null:entries.get(Math.min(list.selectedIndex(),entries.size()-1));}
    private void mutate(UiAction action){
        JsonObject selected=selected();if(selected==null||loading||waiting)return;
        loading=true;sentAt=System.currentTimeMillis();setError(null);
        mutationId=UiClientNetworking.requestDelivery(action,selected.get("id").getAsString());buttons();
    }
    public void handleServerResponse(UiResponsePayload payload){
        if(!payload.requestId().equals(readId)&&!payload.requestId().equals(mutationId))return;
        loading=false;
        if(payload.action()==UiAction.DELIVERY_LIST)readId=null;
        if(!payload.success()){
            setError(payload.error()==null?"납품 처리에 실패했습니다.":payload.error());buttons();return;
        }
        try {
            JsonObject response=JsonParser.parseString(payload.dataJson()).getAsJsonObject();
            if(payload.action()==UiAction.DELIVERY_LIST){
                data=response;waiting=response.has("pendingTrade")&&response.get("pendingTrade").getAsBoolean();updateEntries();
            }else if(response.has("pending")&&response.get("pending").getAsBoolean()){
                waiting=true;nextPoll=System.currentTimeMillis()+2000;
            }else{
                mutationId=null;waiting=false;
                if(response.has("accepted")&&!response.get("accepted").getAsBoolean())setError(response.get("message").getAsString());
                else {setError(null);if(payload.action()==UiAction.DELIVERY_ACCEPT)tab="active";}
                refresh();
            }
        }catch(Exception ex){setError("의뢰 데이터를 읽지 못했습니다. 새로고침해주세요.");}
        buttons();
    }
    @Override public void tick(){
        super.tick();long now=System.currentTimeMillis();
        if(loading&&now-sentAt>15000){loading=false;readId=null;setError("응답이 지연되고 있습니다. 새로고침하면 수락·납품 상태를 확인할 수 있습니다.");buttons();}
        if(waiting&&!loading&&now>=nextPoll){nextPoll=now+2000;refresh();}
    }
    private void updateEntries(){
        if(list==null)return;entries.clear();
        JsonArray rows=data.has(tab)?data.getAsJsonArray(tab):new JsonArray();
        List<Component> names=new ArrayList<>();
        for(var raw:rows){JsonObject row=raw.getAsJsonObject();entries.add(row);names.add(Component.literal(row.get("title").getAsString()));}
        list.setEntries(names);buttons();
    }
    private void buttons(){
        if(accept==null)return;JsonObject row=selected();
        accept.active=!loading&&!waiting&&tab.equals("available")&&row!=null&&row.get("canAccept").getAsBoolean();
        deliver.active=!loading&&!waiting&&tab.equals("active")&&row!=null;
        if(refreshButton!=null)refreshButton.active=!loading;
    }
    @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){
        buttons();renderPanel(g,x,y,w,h);renderSectionTitle(g,title,x+10,y+14);
        list.render(g,font,mx,my);
        int dx=x+listWidth+20,dy=y+68,dw=w-listWidth-30;
        renderPanel(g,dx,dy,dw,h-108);
        JsonObject row=selected();
        List<String> lines=new ArrayList<>();
        if(row==null)lines.add(tab.equals("active")?"수락한 의뢰가 없습니다.":"표시할 의뢰가 없습니다.");
        else{
            lines.add(row.get("title").getAsString());
            String itemId=row.get("itemId").getAsString();
            ResourceLocation item=ResourceLocation.tryParse(itemId);
            lines.add("품목: "+(item==null?itemId:BuiltInRegistries.ITEM.get(item).getDescription().getString()));
            lines.add("납품: "+(row.has("delivered")?row.get("delivered").getAsInt():0)+" / "+row.get("quantity").getAsInt());
            lines.add("완료 보상: "+row.get("reward").getAsInt());
            if(tab.equals("available")&&!row.get("canAccept").getAsBoolean()){
                String reason=row.get("reason").getAsString();
                lines.add(switch(reason){case "already_active"->"이미 수락한 의뢰입니다.";case "active_limit"->"수락 한도: 3개";default->"재수락 대기 중입니다.";});
            }
            if(tab.equals("active")){lines.add("일부 수량도 납품할 수 있습니다.");lines.add("납품한 물품은 사용됩니다.");}
            if(tab.equals("completed"))lines.add("완료 및 보상 지급됨");
        }
        renderClipped(g,dx,dy,dw,h-108,()->{
            int lineY=dy+10;for(String line:lines){UiTextRender.drawEllipsized(g,font,line,dx+8,lineY,dw-16,0xFFEAF1FF);lineY+=14;}
        });
        UiTextRender.drawEllipsized(g,font,waiting?"거래·납품 처리 중. 자동으로 갱신됩니다.":loading?"의뢰를 불러오는 중…":"",x+238,y+h-23,Math.max(0,w-248),0xFFDDE6F9);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(list!=null&&list.mouseClicked(mx,my,button)){buttons();return true;}return super.mouseClicked(mx,my,button);}
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){if(list!=null&&list.mouseScrolled(mx,my,sy))return true;return super.mouseScrolled(mx,my,sx,sy);}
}
