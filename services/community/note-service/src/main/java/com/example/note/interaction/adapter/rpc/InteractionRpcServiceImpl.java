package com.example.note.interaction.adapter.rpc;

import com.example.common.*;
import com.example.note.interaction.application.InteractionApplicationService;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class InteractionRpcServiceImpl implements InteractionRpcService {
  private final InteractionApplicationService app;

  public InteractionRpcServiceImpl(InteractionApplicationService app) {
    this.app = app;
  }

  public ToggleResponse toggle(ToggleRequest r) {
    return app.toggle(r);
  }

  public ToggleResponse getStatus(ToggleRequest r) {
    return app.getStatus(r);
  }

  public BatchStatusResponse batchStatus(BatchStatusRequest r) {
    return app.batchStatus(r);
  }

  public java.util.List<Long> getUserInteractions(String a, String b, Long c, int d, int e) {
    return app.getUserInteractions(a, b, c, d, e);
  }
}
