import com.google.gson.JsonObject;
import com.namanseul.farmingmod.server.shop.ShopTradeProtocol;

public class ShopTradeProtocolTest {
    private static final ShopTradeProtocol.Request REQUEST = new ShopTradeProtocol.Request(
            "player", "request", "buy", "mod:fish", 2);
    private static JsonObject receipt() {
        JsonObject result = new JsonObject();
        result.addProperty("playerId", "player"); result.addProperty("requestId", "request");
        result.addProperty("transactionType", "buy"); result.addProperty("itemId", "mod:fish");
        result.addProperty("quantity", 2); result.addProperty("replayed", false);
        result.addProperty("balanceAfter", 100); result.addProperty("totalPrice", 50);
        result.addProperty("stockAfter", 20); result.addProperty("unitPrice", 25);
        return result;
    }
    private static void rejected(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("unverified response accepted");
    }
    public static void main(String[] args) {
        if (!ShopTradeProtocol.parse(REQUEST,200,receipt().toString()).accepted()) throw new AssertionError();
        JsonObject replay = receipt(); replay.addProperty("replayed",true);
        if (!ShopTradeProtocol.parse(REQUEST,200,replay.toString()).accepted()) throw new AssertionError();
        for (String key : new String[]{"playerId","requestId","transactionType","itemId"}) {
            JsonObject wrong = receipt(); wrong.addProperty(key,"wrong");
            rejected(() -> ShopTradeProtocol.parse(REQUEST,200,wrong.toString()));
        }
        for (Number value : new Number[]{3, 2.5}) {
            JsonObject wrong = receipt(); wrong.addProperty("quantity",value);
            rejected(() -> ShopTradeProtocol.parse(REQUEST,200,wrong.toString()));
        }
        for (String key : new String[]{"balanceAfter","totalPrice","stockAfter","unitPrice"}) {
            JsonObject wrong = receipt(); wrong.addProperty(key,-1);
            rejected(() -> ShopTradeProtocol.parse(REQUEST,200,wrong.toString()));
        }
        JsonObject missing = receipt(); missing.remove("replayed");
        rejected(() -> ShopTradeProtocol.parse(REQUEST,200,missing.toString()));
        JsonObject wrongType = receipt(); wrongType.addProperty("replayed","true");
        rejected(() -> ShopTradeProtocol.parse(REQUEST,200,wrongType.toString()));
        JsonObject failure = receipt(); failure.addProperty("code","INSUFFICIENT_STOCK"); failure.addProperty("message","No stock");
        if (ShopTradeProtocol.parse(REQUEST,409,failure.toString()).accepted()) throw new AssertionError();
        rejected(() -> ShopTradeProtocol.parse(REQUEST,500,failure.toString()));
        rejected(() -> ShopTradeProtocol.parse(REQUEST,401,failure.toString()));
        failure.addProperty("code","REQUEST_CONFLICT");
        rejected(() -> ShopTradeProtocol.parse(REQUEST,409,failure.toString()));
        rejected(() -> ShopTradeProtocol.parse(REQUEST,404,"{\"message\":\"proxy not found\"}"));
        rejected(() -> ShopTradeProtocol.parse(REQUEST,200,"not-json"));
        System.out.println("Protocol validation passed");
    }
}
