import com.google.gson.JsonObject;
import com.namanseul.farmingmod.server.delivery.DeliveryProtocol;

public class DeliveryProtocolTest {
    private static final DeliveryProtocol.Request REQUEST = new DeliveryProtocol.Request("owner","request","contract","minecraft:wheat",20);
    private static JsonObject receipt(boolean complete) {
        JsonObject r=new JsonObject(), c=new JsonObject();
        r.addProperty("playerId","owner"); r.addProperty("requestId","request"); r.addProperty("contractId","contract");
        r.addProperty("operation","submit"); r.addProperty("itemId","minecraft:wheat"); r.addProperty("quantity",20);
        r.addProperty("accepted",true); r.addProperty("reward",complete?70:0); r.addProperty("balance",70);
        c.addProperty("id","contract"); c.addProperty("itemId","minecraft:wheat"); c.addProperty("quantity",64);
        c.addProperty("delivered",complete?64:20); c.addProperty("reward",70); c.addProperty("status",complete?"completed":"active");
        r.add("contract",c); return r;
    }
    private static void denied(int status,JsonObject r) {
        try {DeliveryProtocol.parse(REQUEST,status,r.toString());} catch(RuntimeException expected){return;}
        throw new AssertionError("unconfirmed receipt accepted");
    }
    public static void main(String[] args) {
        for(boolean complete:new boolean[]{false,true}) {
            if(!DeliveryProtocol.parse(REQUEST,200,receipt(complete).toString()).accepted())throw new AssertionError();
        }
        for(String field:new String[]{"playerId","requestId","contractId","itemId","operation"}) {
            JsonObject r=receipt(true);r.addProperty(field,"other");denied(200,r);
        }
        for(Number n:new Number[]{19,20.5,2147483648L}) {JsonObject r=receipt(true);r.addProperty("quantity",n);denied(200,r);}
        JsonObject r=receipt(false);r.addProperty("reward",70);denied(200,r);
        r=receipt(true);r.getAsJsonObject("contract").addProperty("status","active");denied(200,r);
        for(int status:new int[]{401,403,409,500,503})denied(status,receipt(true));
        r=receipt(false);r.addProperty("accepted",false);r.addProperty("code","MISMATCH");r.addProperty("message","Wrong item");
        if(DeliveryProtocol.parse(REQUEST,200,r.toString()).accepted())throw new AssertionError();
        r.addProperty("code","UNKNOWN");denied(200,r);
        System.out.println("Delivery receipt validation passed");
    }
}
