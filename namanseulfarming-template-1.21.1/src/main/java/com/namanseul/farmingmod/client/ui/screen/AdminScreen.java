package com.namanseul.farmingmod.client.ui.screen;

import com.google.gson.*;
import com.namanseul.farmingmod.network.*;
import com.namanseul.farmingmod.network.payload.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.ItemStack;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Compact navigation, paged lists and contextual ID pickers. All writes remain server-authorized. */
public final class AdminScreen extends BaseGameScreen {
    private JsonArray operations=new JsonArray(),rows=new JsonArray(),lookupRows=new JsonArray();
    private final Map<String,String> values=new HashMap<>();
    private final Map<String,EditBox> editors=new HashMap<>();
    private final List<Button> magnifiers=new ArrayList<>();
    private final List<Hint> hints=new ArrayList<>();
    private String section="wallet",message="",pendingId,lookupKey,lookupSearch="";
    private JsonObject pendingBody,confirmBody,selectedLookup;
    private boolean waiting,lookupOpen;
    private int selected,fieldPage,rowPage,menuPage,lookupPage,lookupTotal,confirmPage;
    private int x,y,w,h,navX,navW,listX,listW,formX,formW,top,bottom;
    private EditBox searchBox;
    private record Hint(int x,int y,int w,int h,String text) {}
    public AdminScreen(){super(Component.literal("경제 관리"));}
    private JsonObject operation(){return operations.isEmpty()?null:operations.get(selected).getAsJsonObject();}
    private List<Integer> sectionOps(){List<Integer> result=new ArrayList<>();for(int i=0;i<operations.size();i++)if(operations.get(i).getAsJsonObject().get("section").getAsString().equals(section))result.add(i);return result;}
    private List<String> sections(){return operations.asList().stream().map(e->e.getAsJsonObject().get("section").getAsString()).distinct().toList();}
    private boolean editable(){return !waiting&&pendingBody==null;}
    @Override protected void init(){drawControls();if(operations.isEmpty()&&!waiting)request(UiAction.ADMIN_CATALOG,new JsonObject(),null);}
    private void layout(){
        w=Math.min(660,width-16);h=Math.min(350,height-30);x=(width-w)/2;y=(height-h)/2;
        navX=x+8;navW=Math.max(82,Math.min(122,w/5));listX=navX+navW+6;listW=Math.max(90,(w-navW-30)*4/10);
        formX=listX+listW+6;formW=x+w-8-formX;top=y+72;bottom=y+h-38;
    }
    private Button button(String label,int bx,int by,int bw,int bh,Runnable action,boolean enabled){
        Button b=Button.builder(Component.literal(font.plainSubstrByWidth(label,Math.max(1,bw-8))),ignored->action.run()).bounds(bx,by,Math.max(12,bw),bh).build();
        if(!label.isBlank())hints.add(new Hint(bx,by,bw,bh,label));
        b.active=enabled;b.setTooltip(Tooltip.create(Component.literal(label)));return addRenderableWidget(b);
    }
    private void capture(){editors.forEach((key,edit)->values.put(key,edit.getValue()));}
    private void drawControls(){
        clearWidgets();editors.clear();magnifiers.clear();hints.clear();layout();
        if(lookupOpen){drawLookupControls();return;}
        if(confirmBody!=null){drawConfirmationControls();return;}
        button("돌아가기",x+w-67,y+5,59,18,this::onClose,!waiting);
        List<String> cats=sections();int columns=Math.max(1,Math.min(6,cats.size())),cw=(w-16)/columns;
        for(int i=0;i<cats.size();i++){
            String cat=cats.get(i);button((cat.equals(section)?"• ":"")+display(cat),x+8+(i%columns)*cw,y+27+(i/columns)*18,cw-3,17,()->changeSection(cat),editable());
        }
        JsonObject op=operation();if(op==null)return;
        List<Integer> menu=sectionOps();int menuSize=Math.max(1,(bottom-top-23)/19),menus=Math.max(1,(menu.size()+menuSize-1)/menuSize);
        menuPage=Math.min(menuPage,menus-1);
        for(int i=menuPage*menuSize;i<Math.min(menu.size(),(menuPage+1)*menuSize);i++){
            final int index=menu.get(i);String label=operations.get(index).getAsJsonObject().get("label").getAsString();
            button((index==selected?"• ":"")+label,navX,top+(i-menuPage*menuSize)*19,navW,18,()->chooseOperation(index),editable());
        }
        pager(navX,bottom-20,navW,menuPage,menus,p->{menuPage=p;drawControls();},editable());
        drawResultControls();drawFieldControls(op);
        button(pendingBody!=null?"원래 요청 재시도":op.get("write").getAsBoolean()?"변경 확인":"조회",formX,bottom+4,formW,20,this::submit,!waiting);
    }
    private void changeSection(String next){if(!editable())return;section=next;selected=sectionOps().get(0);fieldPage=menuPage=rowPage=0;values.clear();rows=new JsonArray();message="";drawControls();}
    private void chooseOperation(int index){if(!editable())return;selected=index;fieldPage=0;values.clear();drawControls();}
    private int rowSize(){return Math.max(1,(bottom-top-43)/20);}
    private int fieldColumns(){return formW>=230?2:1;}
    private int fieldSize(){return Math.max(1,(bottom-top-48)/33)*fieldColumns();}
    private void drawResultControls(){
        int size=rowSize(),pages=Math.max(1,(rows.size()+size-1)/size);rowPage=Math.min(rowPage,pages-1);
        for(int i=rowPage*size;i<Math.min(rows.size(),(rowPage+1)*size);i++){
            JsonObject row=rows.get(i).getAsJsonObject();String label=rowLabel(row);int ry=top+15+(i-rowPage*size)*20;
            button("",listX,ry,listW,19,()->selectRow(row),editable());hints.add(new Hint(listX,ry,listW,19,rowTooltip(row)));
        }
        pager(listX,bottom-20,listW,rowPage,pages,p->{rowPage=p;drawControls();},!waiting);
    }
    private void drawFieldControls(JsonObject op){
        JsonArray fields=op.getAsJsonArray("fields");int size=fieldSize(),cols=fieldColumns(),pages=Math.max(1,(fields.size()+size-1)/size),cell=(formW-4*(cols-1))/cols;
        fieldPage=Math.min(fieldPage,pages-1);
        for(int i=fieldPage*size;i<Math.min(fields.size(),(fieldPage+1)*size);i++){
            JsonObject f=fields.get(i).getAsJsonObject();String key=f.get("key").getAsString(),value=values.getOrDefault(key,f.get("default").getAsString());values.putIfAbsent(key,value);
            int local=i-fieldPage*size,bx=formX+(local%cols)*(cell+4),by=top+15+(local/cols)*33;
            String label=f.get("label").getAsString();hints.add(new Hint(bx,by,cell,10,label));
            if(f.get("type").getAsString().equals("choice")){
                JsonArray choices=f.getAsJsonArray("choices");button(display(value),bx,by+11,cell,19,()->{capture();int index=0;for(int k=0;k<choices.size();k++)if(choices.get(k).getAsString().equals(values.get(key)))index=k;values.put(key,choices.get((index+1)%choices.size()).getAsString());drawControls();},editable());
            }else{
                boolean lookup=f.has("lookup"),auto=f.has("autoId")&&f.get("autoId").getAsBoolean();int reserved=(lookup?22:0)+(auto?30:0),ew=cell-reserved;
                EditBox edit=new EditBox(font,bx,by+11,Math.max(18,ew),19,Component.literal(label));edit.setMaxLength(key.equals("message")?4000:500);edit.setValue(value);edit.setEditable(editable());
                if(auto)edit.setHint(Component.literal("자동 생성"));edit.setTooltip(Tooltip.create(Component.literal(label)));editors.put(key,edit);addRenderableWidget(edit);
                if(lookup){Button b=button("",bx+ew+2,by+11,20,19,()->openLookup(key),editable());b.setTooltip(Tooltip.create(Component.literal("검색해서 선택")));magnifiers.add(b);hints.add(new Hint(b.getX(),b.getY(),20,19,"검색해서 선택"));}
                if(auto)button("신규",bx+cell-28,by+11,28,19,()->{capture();values.put(key,"");drawControls();},editable()).setTooltip(Tooltip.create(Component.literal("기존 ID를 지우고 새 항목으로 등록합니다. 저장할 때 자동 생성됩니다.")));
            }
        }
        pager(formX,bottom-20,formW,fieldPage,pages,p->{capture();fieldPage=p;drawControls();},!waiting);
        if(op.getAsJsonArray("fields").asList().stream().anyMatch(e->e.getAsJsonObject().get("key").getAsString().equals("itemId")))
            button("손 아이템",formX+formW-64,top-5,64,17,()->{if(!editable()||minecraft.player==null)return;capture();ItemStack held=minecraft.player.getMainHandItem();if(held.isEmpty())return;values.put("itemId",BuiltInRegistries.ITEM.getKey(held.getItem()).toString());values.put("itemName",held.getHoverName().getString());drawControls();},editable());
    }
    private void pager(int bx,int by,int bw,int page,int pages,java.util.function.IntConsumer change,boolean enabled){
        button("‹",bx,by,18,18,()->change.accept(Math.max(0,page-1)),enabled&&page>0);
        button("›",bx+bw-18,by,18,18,()->change.accept(Math.min(pages-1,page+1)),enabled&&page+1<pages);
        String label=(page+1)+" / "+pages+" 페이지";hints.add(new Hint(bx+20,by,bw-40,18,label));
    }
    private void selectRow(JsonObject row){
        if(!editable())return;capture();for(var entry:row.entrySet())if(entry.getValue().isJsonPrimitive()){
            StringBuilder camel=new StringBuilder();boolean upper=false;for(char c:entry.getKey().toCharArray()){if(c=='_'){upper=true;continue;}camel.append(upper?Character.toUpperCase(c):c);upper=false;}values.put(camel.toString(),entry.getValue().getAsString());
        }
        if(row.has("permissions")&&row.get("permissions").isJsonArray())values.put("permissions",String.join(", ",row.getAsJsonArray("permissions").asList().stream().map(e->display(e.getAsString())).toList()));
        if(row.has("balance")&&row.has("id")){values.put("playerId",str(row,"id"));values.put("expectedBalance",str(row,"balance"));}
        drawControls();
    }
    private void openLookup(String key){capture();lookupOpen=true;lookupKey=key;lookupSearch="";lookupPage=0;lookupTotal=0;lookupRows=new JsonArray();selectedLookup=null;drawControls();queryLookup();}
    private int lookupSize(){return Math.max(1,Math.min(10,(h-106)/23));}
    private void queryLookup(){if(waiting)return;selectedLookup=null;if(searchBox!=null)lookupSearch=searchBox.getValue();JsonObject fields=new JsonObject();fields.addProperty("operation",operation().get("action").getAsString());fields.addProperty("field",lookupKey);fields.addProperty("search",lookupSearch);fields.addProperty("page",lookupPage);fields.addProperty("pageSize",lookupSize());JsonObject body=new JsonObject();body.addProperty("action","lookup");body.add("fields",fields);request(UiAction.ADMIN_READ,body,null);drawControls();}
    private void drawLookupControls(){
        int searchW=Math.max(80,w-166);searchBox=new EditBox(font,x+10,y+29,searchW,20,Component.literal("검색어"));searchBox.setMaxLength(80);searchBox.setValue(lookupSearch);searchBox.setHint(Component.literal("닉네임 · UUID · 항목 이름"));addRenderableWidget(searchBox);
        button("검색",x+searchW+16,y+29,55,20,()->{lookupPage=0;queryLookup();},!waiting);
        button("닫기",x+w-65,y+29,55,20,()->{lookupOpen=false;drawControls();},!waiting);
        int timeW=Math.max(65,w/5),playerW=Math.max(65,w/5),idW=w-timeW-playerW-20;
        for(int i=0;i<lookupRows.size();i++){
            JsonObject row=lookupRows.get(i).getAsJsonObject();int ry=y+72+i*23;
            Button b=button("",x+10,ry,w-20,22,()->{selectedLookup=row;lookupSearch=searchBox.getValue();drawControls();},!waiting);
            b.setTooltip(Tooltip.create(Component.literal(str(row,"label")+"\n"+str(row,"value")+"\n"+str(row,"playerName")+" · "+formatTime(str(row,"occurredAt")))));
        }
        int pages=Math.max(1,(lookupTotal+lookupSize()-1)/lookupSize());
        button("이전",x+10,y+h-29,40,20,()->{lookupPage--;queryLookup();},!waiting&&lookupPage>0);
        button("다음",x+54,y+h-29,40,20,()->{lookupPage++;queryLookup();},!waiting&&(lookupPage+1)<pages);
        int first=Math.max(0,Math.min(lookupPage-2,pages-5));for(int i=first;i<Math.min(pages,first+5);i++){final int page=i;button((page==lookupPage?"[":"")+(page+1)+(page==lookupPage?"]":""),x+100+(i-first)*24,y+h-29,22,20,()->{lookupPage=page;queryLookup();},!waiting);}
        button("선택 확인",x+w-90,y+h-29,80,20,()->{values.put(lookupKey,str(selectedLookup,"value"));if(lookupKey.equals("playerId")&&selectedLookup.has("balance"))values.put("expectedBalance",str(selectedLookup,"balance"));lookupOpen=false;message="선택: "+str(selectedLookup,"playerName")+" · "+str(selectedLookup,"label");drawControls();},!waiting&&selectedLookup!=null);
    }
    private void submit(){
        if(waiting||operation()==null)return;capture();if(pendingBody!=null){request(UiAction.ADMIN_EXECUTE,pendingBody,pendingId);drawControls();return;}
        JsonObject op=operation(),fields=new JsonObject();for(JsonElement e:op.getAsJsonArray("fields")){JsonObject f=e.getAsJsonObject();String key=f.get("key").getAsString();fields.addProperty(key,values.getOrDefault(key,f.get("default").getAsString()));}
        JsonObject body=new JsonObject();body.addProperty("action",op.get("action").getAsString());body.add("fields",fields);
        if(!op.get("write").getAsBoolean()){request(UiAction.ADMIN_READ,body,null);drawControls();return;}
        confirmBody=body;confirmPage=0;drawControls();
    }
    private int confirmSize(){return Math.max(1,(h-85)/22);}
    private void drawConfirmationControls(){
        int pages=Math.max(1,(operation().getAsJsonArray("fields").size()+confirmSize()-1)/confirmSize());
        pager(x+10,y+h-29,w-194,confirmPage,pages,p->{confirmPage=p;drawControls();},true);
        button("취소",x+w-176,y+h-29,76,20,()->{confirmBody=null;drawControls();},true);
        button("확인·실행",x+w-94,y+h-29,84,20,()->{pendingBody=confirmBody;confirmBody=null;pendingId=UUID.randomUUID().toString();request(UiAction.ADMIN_EXECUTE,pendingBody,pendingId);drawControls();},true);
    }
    private void request(UiAction action,JsonObject body,String id){waiting=true;PacketDistributor.sendToServer(new UiRequestPayload(id==null?UUID.randomUUID().toString():id,UiScreenType.ADMIN,action,body.toString()));}
    public void handleServerResponse(UiResponsePayload payload){
        waiting=false;if(!payload.success()){message=payload.error();if(message!=null&&!message.startsWith("[PENDING]")){pendingBody=null;pendingId=null;}drawControls();return;}
        JsonObject data=JsonParser.parseString(payload.dataJson()).getAsJsonObject();
        if(payload.action()==UiAction.ADMIN_CATALOG){operations=data.getAsJsonArray("operations");if(!operations.isEmpty())section=operation().get("section").getAsString();}
        else if(payload.action()==UiAction.ADMIN_EXECUTE){pendingBody=null;pendingId=null;message="작업 완료";if(data.has("id")){values.put("id",str(data,"id"));message+=" · ID: "+str(data,"id");}}
        else if(data.has("lookup")){lookupRows=data.getAsJsonArray("rows");lookupPage=data.get("page").getAsInt();lookupTotal=data.get("total").getAsInt();selectedLookup=null;message=lookupTotal+"개 검색됨";}
        else{rows=data.has("rows")?data.getAsJsonArray("rows"):new JsonArray();if(data.has("ledger"))rows.addAll(data.getAsJsonArray("ledger"));rowPage=0;message=rows.size()+"개 항목을 불러왔습니다."+(data.has("hasMore")?" 검색 범위를 좁혀 주세요.":"");}
        drawControls();
    }
    @Override protected void renderBalanceHud(GuiGraphics graphics){} // Admin panels display audited values, not shop previews.
    @Override protected void renderContents(GuiGraphics g,int mx,int my,float tick){
        renderPanel(g,x,y,w,h);g.drawString(font,lookupOpen?"검색해서 ID 선택":confirmBody!=null?"변경 내용 확인":title.getString(),x+10,y+10,0xffffff);
        if(lookupOpen){renderLookup(g,mx,my);return;}if(confirmBody!=null){renderConfirmation(g,mx,my);return;}
        JsonObject op=operation();if(op==null)return;
        g.drawString(font,"기능 선택",navX,top-10,0xaaccee);g.drawString(font,"조회 목록",listX,top-10,0xaaccee);g.drawString(font,font.plainSubstrByWidth(op.get("label").getAsString(),formW-70),formX,top-10,0xffffaa);
        JsonArray fields=op.getAsJsonArray("fields");int size=fieldSize(),cols=fieldColumns(),cell=(formW-4*(cols-1))/cols;
        for(int i=fieldPage*size;i<Math.min(fields.size(),(fieldPage+1)*size);i++){int local=i-fieldPage*size;g.drawString(font,font.plainSubstrByWidth(fields.get(i).getAsJsonObject().get("label").getAsString(),cell),formX+(local%cols)*(cell+4),top+15+(local/cols)*33,0xdbe5f7);}
        renderPageLabel(g,navX,bottom-20,navW,menuPage,Math.max(1,(sectionOps().size()+Math.max(1,(bottom-top-23)/19)-1)/Math.max(1,(bottom-top-23)/19)));
        renderPageLabel(g,listX,bottom-20,listW,rowPage,Math.max(1,(rows.size()+rowSize()-1)/rowSize()));
        renderPageLabel(g,formX,bottom-20,formW,fieldPage,Math.max(1,(fields.size()+fieldSize()-1)/fieldSize()));
        g.drawString(font,font.plainSubstrByWidth(waiting?"처리 중…":message,w-formW-25),x+8,bottom+10,0xffd788);
    }
    private void renderPageLabel(GuiGraphics g,int bx,int by,int bw,int page,int pages){g.drawCenteredString(font,(page+1)+" / "+pages,bx+bw/2,by+5,0xbbccee);}
    private void renderLookup(GuiGraphics g,int mx,int my){
        int timeW=Math.max(65,w/5),playerW=Math.max(65,w/5),idX=x+10+timeW+playerW,idW=w-timeW-playerW-20;
        g.drawString(font,"등록·발생 시각",x+12,y+59,0xaaccee);g.drawString(font,"대상 플레이어",x+12+timeW,y+59,0xaaccee);g.drawString(font,"ID / UUID",idX+2,y+59,0xaaccee);
        if(lookupRows.isEmpty())g.drawCenteredString(font,waiting?"검색 중…":"검색 결과가 없습니다.",x+w/2,y+95,0xbbccdd);
        g.drawString(font,font.plainSubstrByWidth(waiting?"검색 중…":message,w-20),x+10,y+h-43,0xffd788);
        int pages=Math.max(1,(lookupTotal+lookupSize()-1)/lookupSize());g.drawString(font,lookupTotal+"개 · "+(lookupPage+1)+" / "+pages+" 페이지",x+w-190,y+11,0xbbccdd);
    }
    private void renderConfirmation(GuiGraphics g,int mx,int my){
        g.drawString(font,operation().get("label").getAsString(),x+10,y+31,0xffdd88);JsonArray fields=operation().getAsJsonArray("fields");int size=confirmSize();
        for(int i=confirmPage*size;i<Math.min(fields.size(),(confirmPage+1)*size);i++){
            JsonObject f=fields.get(i).getAsJsonObject();String key=f.get("key").getAsString(),value=str(confirmBody.getAsJsonObject("fields"),key);
            if(value.isBlank()&&f.has("autoId")&&f.get("autoId").getAsBoolean())value="저장 시 자동 생성";
            String line=f.get("label").getAsString()+": "+display(value);int ry=y+51+(i-confirmPage*size)*22;g.drawString(font,font.plainSubstrByWidth(line,w-20),x+10,ry,0xdbe5f7);
            if(mx>=x+10&&mx<x+w-10&&my>=ry&&my<ry+20)g.renderTooltip(font,Component.literal(line),mx,my);
        }
        renderPageLabel(g,x+10,y+h-29,w-194,confirmPage,Math.max(1,(fields.size()+size-1)/size));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float tick){
        super.render(g,mx,my,tick);
        if(lookupOpen){int timeW=Math.max(65,w/5),playerW=Math.max(65,w/5),idW=w-timeW-playerW-20;
            for(int i=0;i<lookupRows.size();i++){
                JsonObject row=lookupRows.get(i).getAsJsonObject();int ry=y+72+i*23;
                if(row==selectedLookup)g.fill(x+11,ry+1,x+w-11,ry+21,0x88629ac9);
                g.drawString(font,font.plainSubstrByWidth(formatTime(str(row,"occurredAt")),timeW-5),x+13,ry+7,0xe3edff);
                g.drawString(font,font.plainSubstrByWidth(str(row,"playerName"),playerW-5),x+13+timeW,ry+7,0xe3edff);
                g.drawString(font,font.plainSubstrByWidth(str(row,"value"),idW-7),x+13+timeW+playerW,ry+7,0xe3edff);
                if(mx>=x+10&&mx<x+w-10&&my>=ry&&my<ry+22)g.renderTooltip(font,List.of(Component.literal(str(row,"label")),Component.literal(str(row,"value")),Component.literal(str(row,"playerName")+" · "+formatTime(str(row,"occurredAt")))),Optional.empty(),mx,my);
            }
            return;
        }
        if(!lookupOpen&&confirmBody==null){
            int size=rowSize();for(int i=rowPage*size;i<Math.min(rows.size(),(rowPage+1)*size);i++){
                JsonObject row=rows.get(i).getAsJsonObject();int ry=top+15+(i-rowPage*size)*20,tx=listX+5;
                if(row.has("item_id")){
                    var id=net.minecraft.resources.ResourceLocation.tryParse(str(row,"item_id"));
                    var item=id==null?net.minecraft.world.item.Items.AIR:BuiltInRegistries.ITEM.get(id);
                    if(item!=net.minecraft.world.item.Items.AIR){g.renderItem(item.getDefaultInstance(),tx,ry+1);tx+=19;}
                }
                g.drawString(font,font.plainSubstrByWidth(rowLabel(row),listX+listW-tx-5),tx,ry+6,0xe3edff);
            }
        }
        for(Button b:magnifiers){int bx=b.getX()+6,by=b.getY()+5,color=b.active?0xffe3edff:0xff778899;g.fill(bx+1,by,bx+6,by+1,color);g.fill(bx,by+1,bx+1,by+6,color);g.fill(bx+6,by+1,bx+7,by+6,color);g.fill(bx+1,by+6,bx+6,by+7,color);g.fill(bx+6,by+6,bx+8,by+8,color);g.fill(bx+8,by+8,bx+10,by+10,color);}
        if(!message.isBlank()&&mx>=x+8&&mx<x+w-formW-17&&my>=bottom+4&&my<bottom+24)g.renderTooltip(font,Component.literal(message),mx,my);
        for(Hint hint:hints)if(mx>=hint.x&&mx<hint.x+hint.w&&my>=hint.y&&my<hint.y+hint.h){g.renderTooltip(font,Component.literal(hint.text),mx,my);break;}
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){
        if(waiting||sy==0)return super.mouseScrolled(mx,my,sx,sy);int direction=sy<0?1:-1;
        if(lookupOpen){int pages=Math.max(1,(lookupTotal+lookupSize()-1)/lookupSize());int next=Math.max(0,Math.min(pages-1,lookupPage+direction));if(next!=lookupPage){lookupPage=next;queryLookup();}return true;}
        if(confirmBody!=null){int pages=Math.max(1,(operation().getAsJsonArray("fields").size()+confirmSize()-1)/confirmSize());confirmPage=Math.max(0,Math.min(pages-1,confirmPage+direction));drawControls();return true;}
        if(mx>=listX&&mx<listX+listW){rowPage=Math.max(0,Math.min(Math.max(0,(rows.size()-1)/rowSize()),rowPage+direction));drawControls();return true;}
        if(mx>=formX&&mx<formX+formW){capture();fieldPage=Math.max(0,Math.min(Math.max(0,(operation().getAsJsonArray("fields").size()-1)/fieldSize()),fieldPage+direction));drawControls();return true;}
        if(mx>=navX&&mx<navX+navW){int size=Math.max(1,(bottom-top-23)/19);menuPage=Math.max(0,Math.min(Math.max(0,(sectionOps().size()-1)/size),menuPage+direction));drawControls();return true;}
        return super.mouseScrolled(mx,my,sx,sy);
    }
    private static String str(JsonObject row,String key){return row.has(key)&&!row.get(key).isJsonNull()?row.get(key).getAsString():"";}
    private static String formatTime(String value){if(value.isBlank())return "기록 없음";try{return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));}catch(Exception ignored){return value;}}
    private static String rowTooltip(JsonObject row){StringBuilder out=new StringBuilder(rowLabel(row));for(String key:new String[]{"id","player_id","actor_id","request_id","item_id","reason","created_at","accepted_at"})if(row.has(key))out.append("\n").append(displayKey(key)).append(": ").append(str(row,key));return out.toString();}
    private static String rowLabel(JsonObject row){
        if(row.has("amount")&&row.has("type"))return (str(row,"type").equals("add")?"지급 +":"차감 -")+str(row,"amount")+"원";
        for(String key:new String[]{"username","item_name","itemName","name","title","action","id","playerId"})if(row.has(key)&&!row.get(key).isJsonNull())return display(str(row,key))+(row.has("balance")?" · "+str(row,"balance")+"원":"");
        List<String> parts=new ArrayList<>();for(var e:row.entrySet())if(e.getValue().isJsonPrimitive())parts.add(displayKey(e.getKey())+": "+display(e.getValue().getAsString()));return String.join(" · ",parts);
    }
    private static String displayKey(String key){return switch(key){case "id"->"기록 ID";case "player_id","playerId"->"대상 UUID";case "actor_id"->"관리자 UUID";case "request_id"->"요청 UUID";case "item_id","itemId"->"아이템 ID";case "reason"->"작업 사유";case "created_at"->"발생 시각";case "accepted_at"->"수락 시각";case "database_time"->"DB 시각";case "pending_audit"->"기록 저장 대기";case "region"->"구역";case "xp"->"경험치";case "level"->"레벨";case "pending"->"미완료 거래";case "buyFeeRate"->"구매 수수료율";case "sellFeeRate"->"판매 수수료율";case "minFee"->"최소 수수료";case "maxSellQuantity"->"최대 판매 수량";case "flipCooldownSeconds"->"반대 거래 대기 시간";case "deliveryActiveLimit"->"동시 진행 의뢰 수";case "projectRewardRate"->"프로젝트 보상 비율";case "walletLimit"->"지갑 잔액 상한";case "maintenance"->"경제 점검 상태";case "frozen"->"경제 이용 정지";case "balance"->"잔액";case "amount"->"금액";case "cooldownSeconds"->"반복 대기 시간";case "status"->"상태";case "count"->"처리 수";case "username"->"플레이어";case "quantity"->"수량";case "before"->"변경 전";case "stockQuantity"->"재고 수량";default->key;};}
    private static String display(String value){return switch(value){
        case "wallet"->"지갑";case "shop"->"상점";case "rules"->"경제 설정";case "delivery"->"납품 의뢰";case "reward"->"보상";case "mail"->"우편";case "project"->"프로젝트";case "event"->"이벤트";case "region"->"구역";case "logs"->"기록";case "recovery"->"복구";case "permissions"->"관리 권한";
        case "agri"->"농업";case "port"->"어업";case "industry"->"산업";case "global"->"전체 구역";case "misc"->"기타";case "none"->"없음";case "true"->"켜짐";case "false"->"꺼짐";case "draft"->"준비 중";case "active"->"활성";case "ended"->"종료";case "price_bonus"->"가격 보정";case "xp_bonus"->"경험치 보정";case "focus_bonus"->"집중 구역 보정";default->com.namanseul.farmingmod.network.UiKoreanText.value(value);};
    }
    @Override public void onClose(){if(lookupOpen&&!waiting){lookupOpen=false;drawControls();return;}if(confirmBody!=null){confirmBody=null;drawControls();return;}minecraft.setScreen(new GameHubScreen());}
}
