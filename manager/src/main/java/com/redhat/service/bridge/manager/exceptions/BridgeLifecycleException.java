package com.redhat.service.bridge.manager.exceptions;

import javax.ws.rs.core.Response;

public class BridgeLifecycleException extends EventBridgeManagerException {

    private static final long serialVersionUID = 1L;

    public BridgeLifecycleException(String message) {
        super(message);
    }

    public BridgeLifecycleException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int getStatusCode() {
        return Response.Status.BAD_REQUEST.getStatusCode();
    }
}
