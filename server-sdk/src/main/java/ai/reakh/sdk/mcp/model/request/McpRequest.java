package ai.reakh.sdk.mcp.model.request;

import ai.reakh.sdk.constants.McpClientMethod;
import ai.reakh.sdk.mcp.model.McpProtocolBase;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class McpRequest extends McpProtocolBase {

    @JsonProperty("method")
    private McpClientMethod method;

    @JsonProperty("params")
    private Object          params;
}
