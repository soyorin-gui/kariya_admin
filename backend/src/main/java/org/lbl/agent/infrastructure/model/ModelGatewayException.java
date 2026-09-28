package org.lbl.agent.infrastructure.model;

/** 模型网关失败的统一异常；对用户展示时应转换成稳定文案，不直接暴露 cause。 */
public class ModelGatewayException extends RuntimeException {
    public ModelGatewayException(String message) {
        super(message);
    }

    public ModelGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
