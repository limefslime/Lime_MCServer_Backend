import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import com.namanseul.farmingmod.server.mail.MailClaimProtocol;

public class MailClaimProtocolTest {
    private static JsonObject receipt() {
        JsonObject result = new JsonObject();
        result.addProperty("playerId","owner"); result.addProperty("requestId","request"); result.addProperty("mailId","mail");
        result.addProperty("claimed",true); result.addProperty("replayed",false);
        result.addProperty("rewardAmount",50); result.addProperty("balanceAfter",100);
        JsonObject mail = new JsonObject();
        mail.addProperty("id","mail"); mail.addProperty("playerId","owner");
        mail.addProperty("isClaimed",true); mail.addProperty("rewardAmount",50); result.add("mail",mail);
        JsonObject info = new JsonObject(), item = new JsonObject();
        info.addProperty("rewardAmount",50);
        item.addProperty("itemId","mod:fish"); item.addProperty("quantity",3);
        info.add("itemReward",item); result.add("rewardInfo",info);
        return result;
    }
    private static MailClaimProtocol.Outcome parse(int status,JsonObject result) {
        return MailClaimProtocol.parse("owner","request","mail",status,result.toString());
    }
    private static void denied(Runnable action) {
        try {action.run();} catch(RuntimeException expected) {return;}
        throw new AssertionError("invalid mail confirmation accepted");
    }
    public static void main(String[] args) {
        if(!parse(200,receipt()).accepted())throw new AssertionError();
        JsonObject replay=receipt(); replay.addProperty("replayed",true);
        if(!parse(200,replay).accepted())throw new AssertionError();
        JsonObject gold=receipt(); gold.getAsJsonObject("rewardInfo").add("itemReward",JsonNull.INSTANCE);
        if(!parse(200,gold).accepted())throw new AssertionError();
        for(String field:new String[]{"playerId","requestId","mailId"}) {
            JsonObject wrong=receipt(); wrong.addProperty(field,"other"); denied(()->parse(200,wrong));
        }
        JsonObject wrongOwner=receipt(); wrongOwner.getAsJsonObject("mail").addProperty("playerId","other");
        denied(()->parse(200,wrongOwner));
        JsonObject wrongMail=receipt(); wrongMail.getAsJsonObject("mail").addProperty("id","other");
        denied(()->parse(200,wrongMail));
        JsonObject wrongMoney=receipt(); wrongMoney.getAsJsonObject("rewardInfo").addProperty("rewardAmount",51);
        denied(()->parse(200,wrongMoney));
        for(Number amount:new Number[]{0,-1,1.5,2147483648L}) {
            JsonObject wrong=receipt(); wrong.getAsJsonObject("rewardInfo").getAsJsonObject("itemReward").addProperty("quantity",amount);
            denied(()->parse(200,wrong));
        }
        JsonObject missing=receipt(); missing.getAsJsonObject("rewardInfo").remove("itemReward"); denied(()->parse(200,missing));
        JsonObject unclaimed=receipt(); unclaimed.addProperty("claimed",false); denied(()->parse(200,unclaimed));
        JsonObject invalid=receipt(); invalid.addProperty("replayed","true"); denied(()->parse(200,invalid));
        JsonObject failure=receipt(); failure.addProperty("code","MAIL_ALREADY_CLAIMED"); failure.addProperty("message","Claimed");
        if(parse(409,failure).accepted())throw new AssertionError();
        denied(()->parse(500,failure)); denied(()->parse(401,failure));
        failure.addProperty("code","REQUEST_CONFLICT"); denied(()->parse(409,failure));
        denied(()->MailClaimProtocol.parse("owner","request","mail",404,"{\"message\":\"not found\"}"));
        System.out.println("Mail protocol validation passed");
    }
}
