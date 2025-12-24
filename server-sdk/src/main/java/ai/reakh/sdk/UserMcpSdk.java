package ai.reakh.sdk;

import ai.reakh.sdk.mcp.McpI18nProxy;

public interface UserMcpSdk {

    UserInfo fetchByAccessKey(String accessKey);

    McpI18nProxy getI18nProxy();
}
