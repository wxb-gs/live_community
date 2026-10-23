package com.example.rpc;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER})
public class InternalRpcFilter implements Filter {
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  private static final Map<String, Long> SEEN = new ConcurrentHashMap<>();
  private static final String SECRET =
      System.getProperty(
          "rpc.shared-secret",
          System.getenv()
              .getOrDefault(
                  "RPC_SHARED_SECRET", "local-development-rpc-secret-change-before-deployment"));

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
    long now = System.currentTimeMillis();
    boolean provider =
        !CommonConstants.CONSUMER.equals(invoker.getUrl().getParameter(CommonConstants.SIDE_KEY));
    if (!provider) {
      String time = Long.toString(now), nonce = UUID.randomUUID().toString();
      invocation.setAttachment("internal-time", time);
      invocation.setAttachment("internal-nonce", nonce);
      invocation.setAttachment("internal-signature", sign(invoker, invocation, time, nonce));
    } else {
      try {
        String time = invocation.getAttachment("internal-time"),
            nonce = invocation.getAttachment("internal-nonce"),
            signature = invocation.getAttachment("internal-signature");
        if (time == null
            || nonce == null
            || signature == null
            || Math.abs(now - Long.parseLong(time)) > 30_000
            || nonce.length() > 64) throw new IllegalArgumentException();
        if (!MessageDigest.isEqual(
            sign(invoker, invocation, time, nonce).getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException();
        SEEN.entrySet().removeIf(e -> now - e.getValue() > 30_000);
        if (SEEN.size() > 100_000 || SEEN.putIfAbsent(nonce, now) != null)
          throw new IllegalArgumentException();
      } catch (RuntimeException e) {
        throw new RpcException(RpcException.FORBIDDEN_EXCEPTION, "Unauthenticated internal RPC");
      }
    }
    return invoker.invoke(invocation);
  }

  private String sign(Invoker<?> invoker, Invocation call, String time, String nonce) {
    try {
      String payload =
          invoker.getInterface().getName()
              + "\n"
              + call.getMethodName()
              + "\n"
              + time
              + "\n"
              + nonce
              + "\n"
              + JSON.writeValueAsString(call.getArguments());
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new RpcException("Cannot sign internal RPC", e);
    }
  }
}
