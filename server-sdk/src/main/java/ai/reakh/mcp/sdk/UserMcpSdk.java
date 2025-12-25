package ai.reakh.mcp.sdk;

import ai.reakh.mcp.sdk.mcp.McpI18nProxy;

public interface UserMcpSdk {

    UserInfo fetchByAccessKey(String accessKey);

    McpI18nProxy getI18nProxy();
}
