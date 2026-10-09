package com.namanseul.farmingmod.server.admin;
import java.net.http.HttpRequest;
public final class BackendAuthorization {
 private BackendAuthorization(){}
 public static HttpRequest.Builder authorize(HttpRequest.Builder builder){
  String token=System.getenv("NFS_INTEGRATION_API_TOKEN");
  if(token==null||token.length()<32)throw new IllegalStateException("Integration token is not configured");
  return builder.setHeader("Authorization","Bearer "+token);
 }
}
