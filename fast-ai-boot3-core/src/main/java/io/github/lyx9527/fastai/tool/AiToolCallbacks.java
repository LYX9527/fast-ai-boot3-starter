package io.github.lyx9527.fastai.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * APT 生成桥接类使用的 Spring AI ToolCallback 运行时构造工具。
 */
public final class AiToolCallbacks {

    private AiToolCallbacks() {
    }

    public static ToolCallback forBridge(Object bridge, String bridgeMethodName, String toolName,
            String description, boolean returnDirect) {
        Method method = Arrays.stream(bridge.getClass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(bridgeMethodName))
                .filter(candidate -> !candidate.isSynthetic())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Generated tool bridge method not found: " + bridge.getClass().getName() + '#'
                                + bridgeMethodName));
        ToolDefinition definition = ToolDefinition.builder()
                .name(toolName)
                .description(description)
                .inputSchema(JsonSchemaGenerator.generateForMethodInput(method))
                .build();
        ToolMetadata metadata = ToolMetadata.builder().returnDirect(returnDirect).build();
        return MethodToolCallback.builder()
                .toolDefinition(definition)
                .toolMetadata(metadata)
                .toolMethod(method)
                .toolObject(bridge)
                .build();
    }
}
