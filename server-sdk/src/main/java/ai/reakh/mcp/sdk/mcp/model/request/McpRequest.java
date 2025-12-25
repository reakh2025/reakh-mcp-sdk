package ai.reakh.mcp.sdk.mcp.model.request;

import ai.reakh.mcp.sdk.constants.McpClientMethod;
import ai.reakh.mcp.sdk.mcp.model.McpProtocolBase;
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
