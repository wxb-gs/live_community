package com.example.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.*;
import org.junit.jupiter.api.Test;

class InternalRpcFilterTest {
  interface Api {
    String echo(String s);
  }

  Invoker<Api> invoker(String side) {
    var i = mock(Invoker.class);
    when(i.getInterface()).thenReturn(Api.class);
    when(i.getUrl()).thenReturn(URL.valueOf("tri://127.0.0.1:20880/example?side=" + side));
    when(i.invoke(any())).thenReturn(new AppResponse("ok"));
    return i;
  }

  RpcInvocation call() {
    return new RpcInvocation(
        "echo",
        Api.class.getName(),
        Api.class.getName(),
        new Class<?>[] {String.class},
        new Object[] {"hello"});
  }

  @Test
  void unsignedInternalCallIsRejected() {
    var filter = new InternalRpcFilter();
    assertThrows(RpcException.class, () -> filter.invoke(invoker("provider"), call()));
  }

  @Test
  void signedCallWorksAndReplayIsRejected() {
    var f = new InternalRpcFilter();
    var call = call();
    f.invoke(invoker("consumer"), call);
    var provider = invoker("provider");
    assertDoesNotThrow(() -> f.invoke(provider, call));
    assertThrows(RpcException.class, () -> f.invoke(provider, call));
  }

  @Test
  void signedArgumentsCannotBeTamperedWith() {
    var f = new InternalRpcFilter();
    var call = call();
    f.invoke(invoker("consumer"), call);
    call.setArguments(new Object[] {"tampered"});
    assertThrows(RpcException.class, () -> f.invoke(invoker("provider"), call));
  }
}
