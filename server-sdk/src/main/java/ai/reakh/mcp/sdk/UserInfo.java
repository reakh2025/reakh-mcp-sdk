package ai.reakh.mcp.sdk;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Setter
@Getter
public class UserInfo {

    private String              secretKey;

    private Map<String, String> attrsFillToReq;
}
