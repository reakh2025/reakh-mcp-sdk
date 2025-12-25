package ai.reakh.mcp.sdk.mcp.model.response;

import ai.reakh.mcp.sdk.mcp.model.McpProtocolBase;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;

@Getter
public class McpErrorResponse extends McpProtocolBase {

    @JsonProperty("error")
    private final McpError error;

    public McpErrorResponse(Object id, McpError error){
        this.id = id;
        this.error = error;
    }
}
