package org.lbl.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.lbl.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 把模型给出的 JSON 参数绑定成类型化输入，并执行 Jakarta Bean Validation。 */
@Component
public class ToolArgumentBinder {
    private final ObjectMapper mapper;
    private final Validator validator;

    public ToolArgumentBinder(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public <I> I bind(Map<String, Object> arguments, Class<I> inputType) {
        I input;
        try {
            input = mapper.convertValue(arguments == null ? Map.of() : arguments, inputType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("工具参数格式不正确");
        }
        Set<ConstraintViolation<I>> violations = validator.validate(input);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining("；"));
            throw new BusinessException("工具参数校验失败：" + message);
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    public <I> I bindJson(String json, Class<I> inputType) {
        Map<String, Object> arguments;
        try {
            arguments = json == null || json.isBlank() ? Map.of() : mapper.readValue(json, Map.class);
        } catch (Exception ex) {
            throw new BusinessException("工具参数不是有效的 JSON 对象");
        }
        return bind(arguments, inputType);
    }
}
