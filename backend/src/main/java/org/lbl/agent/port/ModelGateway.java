package org.lbl.agent.port;

import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.port.model.ModelRequest;
import org.lbl.agent.port.model.ModelResponse;

/** 模型供应商端口。Agent 应用层只依赖它，不认识 OpenAI、DeepSeek 或其它协议 DTO。 */
public interface ModelGateway {
    ModelResponse complete(ModelRequest request, AgentExecutionContext context);
}
