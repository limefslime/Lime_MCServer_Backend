package com.namanseul.farmingmod.server.delivery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.server.shop.ShopTradeProtocol;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class BackendDeliveryBridge {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(700)).build();
    private BackendDeliveryBridge() {}
    private static HttpResponse<String> send(String path, JsonObject body) throws Exception {
        String token = System.getenv("NFS_INTEGRATION_API_TOKEN");
        if (token == null || token.length() < 32 || Config.backendBaseUrl().isBlank())
            throw new IllegalArgumentException("납품 서버 연결 설정을 확인해주세요.");
        HttpRequest.Builder request = com.namanseul.farmingmod.server.admin.BackendAuthorization.authorize(HttpRequest.newBuilder(URI.create(Config.backendBaseUrl().replaceAll("/+$", "")+"/integration/deliveries"+path)))
                .setHeader("Authorization","Bearer "+token).timeout(Duration.ofMillis(Config.backendTimeoutMs()));
        if (body == null) request.GET();
        else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private static JsonObject read(String path,JsonObject body) throws Exception {
        var response=send(path,body);
        if(response.statusCode()!=200) throw new IllegalArgumentException("납품 의뢰를 불러오지 못했습니다. 새로고침해주세요.");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    public static JsonObject list(String playerId) throws Exception {return read("/"+playerId,null);}
    public static JsonObject detail(String playerId,String id) throws Exception {return read("/"+playerId+"/"+id,null);}
    public static JsonObject accept(String playerId,String requestId,String templateId) throws Exception {
        JsonObject body=new JsonObject(); body.addProperty("playerId",playerId);body.addProperty("requestId",requestId);body.addProperty("templateId",templateId);
        return read("/accept",body);
    }
    public static ShopTradeProtocol.Outcome submit(DeliveryProtocol.Request r) throws Exception {
        JsonObject body=new JsonObject();body.addProperty("playerId",r.playerId());body.addProperty("requestId",r.requestId());
        body.addProperty("contractId",r.contractId());body.addProperty("itemId",r.itemId());body.addProperty("quantity",r.quantity());
        var response=send("/submit",body);
        return DeliveryProtocol.parse(r,response.statusCode(),response.body());
    }
}
