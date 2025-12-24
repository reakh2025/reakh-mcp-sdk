package ai.reakh.sdk.mcp;

import static ai.reakh.sdk.constants.McpConstants.*;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.uuid.Generators;

import ai.reakh.sdk.UserInfo;
import ai.reakh.sdk.UserMcpSdk;
import ai.reakh.sdk.openapi.OpenApiSigner;

/**
 * @author bucketli 2021/10/11 19:29
 */
public class McpSessionManager implements HandlerInterceptor {

    private static final int COMMON_PARAMS_HAS_EMTPY = 499;

    private static final int USER_NOT_EXIST          = 498;

    private static final int SIGNATURE_ERROR         = 497;

    private final String     MCP_URI_PREFIX;

    @Resource
    private UserMcpSdk       userMcpSdk;

    public McpSessionManager(String mcpUriPrefix){
        this.MCP_URI_PREFIX = mcpUriPrefix;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (!uri.startsWith(MCP_URI_PREFIX)) {
            return true;
        }

        String ak = request.getParameter("AccessKeyId");
        String signature = request.getParameter("Signature");
        String signatureMethod = request.getParameter("SignatureMethod");
        String signatureNonce = request.getParameter("SignatureNonce");

        if (StringUtils.isBlank(ak) || StringUtils.isBlank(signature) || StringUtils.isBlank(signatureMethod) || StringUtils.isBlank(signatureNonce)) {
            // also support get parameter from header
            ak = request.getHeader("AccessKeyId");
            signature = request.getHeader("Signature");
            signatureMethod = request.getHeader("SignatureMethod");
            signatureNonce = request.getHeader("SignatureNonce");
        }

        if (StringUtils.isBlank(ak) || StringUtils.isBlank(signature) || StringUtils.isBlank(signatureMethod) || StringUtils.isBlank(signatureNonce)) {
            responseSystemError(response, COMMON_PARAMS_HAS_EMTPY);
            return false;
        }

        UserInfo userInfo = userMcpSdk.fetchByAccessKey(ak);
        if (userInfo == null) {
            responseSystemError(response, USER_NOT_EXIST);
            return false;
        }

        Map<String, String> paramToSign = new HashMap<>();
        paramToSign.put("SignatureMethod", signatureMethod);
        paramToSign.put("SignatureNonce", signatureNonce);
        paramToSign.put("AccessKeyId", ak);

        String paramStr = OpenApiSigner.composeStringToSign(paramToSign);
        String regenSignature = OpenApiSigner.signString(paramStr, userInfo.getSecretKey());

        if (!signature.equals(regenSignature)) {
            responseSystemError(response, SIGNATURE_ERROR);
            return false;
        }

        if (userInfo.getAttrsFillToReq() != null) {
            for (Map.Entry<String, String> attr : userInfo.getAttrsFillToReq().entrySet()) {
                request.setAttribute(attr.getKey(), attr.getValue());
            }
        }

        request.setAttribute(MCP_API_REQUEST_ID, generateRequestId());
        request.setAttribute(MCP_API_REQUEST_ID, generateRequestId());
        request.setAttribute(MCP_API_REQUEST_AK, ak);
        request.setAttribute(MCP_API_REQUEST_SK, userInfo.getSecretKey());

        String api = request.getServerName() + ":" + request.getServerPort();
        request.setAttribute(MCP_API_REQUEST_API_HOST, api);
        return true;
    }

    protected void responseSystemError(HttpServletResponse response, int code) throws Exception {
        response.setStatus(code);
        try (PrintWriter writer = response.getWriter()) {
            writer.write("{}");
            writer.flush();
        }
    }

    protected String generateRequestId() {
        return Generators.timeBasedGenerator().generate().toString();
    }
}
