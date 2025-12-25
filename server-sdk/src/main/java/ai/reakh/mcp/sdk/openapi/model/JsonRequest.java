package ai.reakh.mcp.sdk.openapi.model;

import com.google.gson.Gson;

public class JsonRequest {

    public String toJson() {
        return new Gson().toJson(this);
    }
}
