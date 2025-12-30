package ai.reakh.mcp.sdk.mcp;

import static ai.reakh.mcp.sdk.constants.McpConstants.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import ai.reakh.mcp.sdk.UserMcpSdk;
import ai.reakh.mcp.sdk.annotation.McpApiProvider;
import ai.reakh.mcp.sdk.annotation.McpTool;
import ai.reakh.mcp.sdk.constants.McpClientMethod;
import ai.reakh.mcp.sdk.mcp.model.McpProtocolBase;
import ai.reakh.mcp.sdk.mcp.model.request.McpRequest;
import ai.reakh.mcp.sdk.mcp.model.request.ToolCallParams;
import ai.reakh.mcp.sdk.mcp.model.response.*;
import ai.reakh.mcp.sdk.openapi.OpenApiHttpClient;
import ai.reakh.mcp.sdk.utils.JacksonHelper;
import ai.reakh.mcp.sdk.utils.Json;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class McpControllerHelper {

    private static final Integer OPEN_API_TIME_OUT = 120;

    private Map<String, String>  toolApi           = new HashMap<>();

    private ToolsListResult      toolsListResult   = new ToolsListResult(Collections.emptyList(), null);

    private InitializeResult     initResult;

    private Map<String, Object>  pongResult;

    @Resource
    private ApplicationContext   context;

    @Resource
    private UserMcpSdk           userMcpSdk;

    public void initTools() {
        log.info("[MCP] Tools loading...");

        Map<String, Object> providers = context.getBeansWithAnnotation(McpApiProvider.class);

        List<ToolsListResult.ToolDesc> tools = new ArrayList<>();
        Map<String, String> api = new HashMap<>();
        for (Object bean : providers.values()) {
            try {
                initToolInner(bean, tools, api);
            } catch (Exception e) {
                String error = "[MPC] Tools init failed,but ignore it.msg:" + ExceptionUtils.getRootCauseMessage(e);
                log.error(error, e);
                throw new IllegalStateException(error, e);
            }
        }

        this.toolApi = Collections.unmodifiableMap(api);
        this.toolsListResult = new ToolsListResult(Collections.unmodifiableList(tools), null);

        InitializeResult.ServerInfo serverInfo = new InitializeResult.ServerInfo("CloudCanal-MCP", "1.0.0");
        InitializeResult.Tools capabilitiesTools = new InitializeResult.Tools(true);
        InitializeResult.Capabilities capabilities = new InitializeResult.Capabilities(capabilitiesTools);
        this.initResult = new InitializeResult("2025-06-18", capabilities, serverInfo);

        Map<String, Object> pong = new HashMap<>();
        pong.put("ok", true);
        this.pongResult = Collections.unmodifiableMap(pong);

        log.info("[MCP] Tools loaded: {}", this.toolApi.keySet());
    }

    public McpProtocolBase handleRequest(McpRequest req, HttpServletRequest request) {
        if (req == null) {
            McpError err = new McpError(McpError.INTERNAL_ERROR, "Empty request");
            return new McpErrorResponse(null, err);
        }

        Object id = req.getId();
        McpClientMethod method = req.getMethod();

        try {
            switch (method) {
                case INITIALIZE: {
                    return new McpResponse(String.valueOf(id), initResult);
                }
                case PING: {
                    return new McpResponse(String.valueOf(id), pongResult);
                }
                case TOOLS_LIST: {
                    return new McpResponse(String.valueOf(id), toolsListResult);
                }
                case TOOLS_CALL: {
                    String host = (String) request.getAttribute(MCP_API_REQUEST_API_HOST);
                    String ak = (String) request.getAttribute(MCP_API_REQUEST_AK);
                    String sk = (String) request.getAttribute(MCP_API_REQUEST_SK);
                    Object params = req.getParams();
                    ToolCallParams callParams = JacksonHelper.convert(params, ToolCallParams.class);
                    return handleToolCall(id, host, ak, sk, callParams);
                }
                default: {
                    McpError err = new McpError(McpError.METHOD_NOT_FOUND, "Unknown method: " + method);
                    return new McpErrorResponse(String.valueOf(id), err);
                }
            }
        } catch (Exception e) {
            log.warn("[MCP] Handle request error,method:{}.msg:{}", method, ExceptionUtils.getRootCauseMessage(e), e);
            McpError err = new McpError(McpError.INTERNAL_ERROR, "Internal error: " + ExceptionUtils.getRootCauseMessage(e));
            return new McpErrorResponse(String.valueOf(id), err);
        }
    }

    private McpResponse handleToolCall(Object id, String host, String ak, String sk, ToolCallParams callParams) throws Exception {
        String api = toolApi.get(callParams.getName());
        if (api == null) {
            throw new RuntimeException("Tool " + callParams.getName() + " not found.");
        }

        if (StringUtils.isBlank(host)) {
            host = "127.0.0.1:8111";
        }

        if (StringUtils.isBlank(ak) || StringUtils.isBlank(sk)) {
            throw new RuntimeException("AccessKey/SecretKey is missing.");
        }

        OpenApiHttpClient client = new OpenApiHttpClient(host, OPEN_API_TIME_OUT, ak, sk);
        String paramStr = Json.toJson(callParams.getArguments());
        String result = client.doJsonPost(api, paramStr);

        ToolCallResult toolCallResult = ToolCallResult.fromText(result);
        return new McpResponse(id, toolCallResult);
    }

    private void initToolInner(Object bean, List<ToolsListResult.ToolDesc> tools, Map<String, String> api) {
        Class<?> clazz = bean.getClass();
        RequestMapping classMapping = clazz.getAnnotation(RequestMapping.class);

        String classBase = (classMapping != null) ? firstNotEmpty(classMapping.value(), classMapping.path()) : "";

        McpI18nProxy i18nProxy = userMcpSdk.getI18nProxy();

        for (Method method : clazz.getDeclaredMethods()) {
            McpTool mcpTool = method.getAnnotation(McpTool.class);
            if (mcpTool == null) {
                continue;
            }

            Parameter input = findRequestBodyParam(method);
            //            if (input == null) {
            //                log.warn("[MCP] Skip method without @RequestBody parameter: {}.{}", clazz.getSimpleName(), method.getName());
            //                continue;
            //            }
            //
            //            if (isListLike(input)) {
            //                log.warn("[MCP] Skip method with list/array @RequestBody (not supported by MCP): {}.{} param={}", clazz.getSimpleName(), method.getName(), input.getType()
            //                        .getName());
            //                continue;
            //            }

            String toolName = mcpTool.name();
            if (StringUtils.isBlank(toolName)) {
                toolName = method.getName();
            }

            if (api.containsKey(toolName)) {
                String error = String.format("[MCP] Duplicate tool name detected: '%s'. " //
                                             + "Class: %s. MCP tool names must be unique. " //
                                             + "Please provide a unique 'name' attribute in the @McpTool annotation " //
                                             + "or rename the conflicting method.", toolName, clazz.getName());
                throw new IllegalStateException(error);
            }

            String mcpLabel = mcpTool.value();
            String desc = i18nProxy.getMessage(mcpLabel);
            String methodPath = resolveMethodPath(method);
            String fullPath = normalizePath(classBase, methodPath);

            tools.add(new ToolsListResult.ToolDesc(toolName, desc, input, i18nProxy));
            api.put(toolName, fullPath);
        }
    }

    private Parameter findRequestBodyParam(Method method) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                return parameter;
            }
        }
        return null;
    }

    private boolean isListLike(Parameter p) {
        Class<?> rawType = p.getType();
        return rawType.isArray() || Collection.class.isAssignableFrom(rawType);
    }

    private String resolveMethodPath(Method method) {
        RequestMapping m = method.getAnnotation(RequestMapping.class);
        if (m != null) {
            return firstNotEmpty(m.value(), m.path());
        }

        PostMapping pm = method.getAnnotation(PostMapping.class);
        if (pm != null) {
            return firstNotEmpty(pm.value(), pm.path());
        }

        GetMapping gm = method.getAnnotation(GetMapping.class);
        if (gm != null) {
            return firstNotEmpty(gm.value(), gm.path());
        }

        return "";
    }

    private String normalizePath(String classBase, String methodPath) {
        String base = StringUtils.defaultString(classBase, "");
        String m = StringUtils.defaultString(methodPath, "");
        if (!base.startsWith("/")) {
            base = "/" + base;
        }

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        if (!m.startsWith("/")) {
            m = "/" + m;
        }

        return base + m;
    }

    private String firstNotEmpty(String[] value, String[] path) {
        String p = firstOrEmpty(value);
        if (StringUtils.isNotBlank(p)) {
            return p;
        }
        return firstOrEmpty(path);
    }

    private String firstOrEmpty(String[] arr) {
        return (arr != null && arr.length > 0) ? StringUtils.defaultString(arr[0]) : "";
    }
}
