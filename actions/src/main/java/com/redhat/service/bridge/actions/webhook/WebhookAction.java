package com.redhat.service.bridge.actions.webhook;

import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.redhat.service.bridge.actions.ActionInvoker;
import com.redhat.service.bridge.actions.ActionProviderException;
import com.redhat.service.bridge.actions.InvokableActionProvider;
import com.redhat.service.bridge.infra.models.actions.BaseAction;
import com.redhat.service.bridge.infra.models.dto.ProcessorDTO;

import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;

@ApplicationScoped
public class WebhookAction implements InvokableActionProvider {

    public static final String TYPE = "Webhook";
    public static final String ENDPOINT_PARAM = "endpoint";

    private WebClient client;

    @Inject
    WebhookActionValidator validator;

    @Inject
    Vertx vertx;

    @PostConstruct
    private void onPostConstruct() {
        client = WebClient.create(vertx);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public WebhookActionValidator getParameterValidator() {
        return validator;
    }

    @Override
    public ActionInvoker getActionInvoker(ProcessorDTO processor, BaseAction baseAction) {
        String endpoint = Optional.ofNullable(baseAction.getParameters().get(ENDPOINT_PARAM))
                .orElseThrow(() -> buildNoEndpointException(processor));
        return new WebhookInvoker(endpoint, client);
    }

    private ActionProviderException buildNoEndpointException(ProcessorDTO processor) {
        String message = String.format("There is no endpoint specified in the parameters for Action on Processor '%s' on Bridge '%s'",
                processor.getId(), processor.getBridgeId());
        return new ActionProviderException(message);
    }
}
