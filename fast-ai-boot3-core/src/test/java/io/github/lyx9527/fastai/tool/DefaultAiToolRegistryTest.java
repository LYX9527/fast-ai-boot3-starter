package io.github.lyx9527.fastai.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultAiToolRegistryTest {

    @Test
    void resolvesToolsBySetAndGroupWithoutDuplicates() {
        AiGeneratedTool query = tool("order.query", Set.of("order", "query"), "order-tools",
                AiToolSecurityMetadata.defaults(), new AtomicInteger());
        AiGeneratedTool delete = tool("order.delete", Set.of("order", "write"), "order-tools",
                AiToolSecurityMetadata.defaults(), new AtomicInteger());
        AiGeneratedTool customer = tool("customer.query", Set.of("customer"), "customer-tools",
                AiToolSecurityMetadata.defaults(), new AtomicInteger());
        DefaultAiToolRegistry registry = new DefaultAiToolRegistry(Set.of(query, delete, customer));

        Collection<ToolCallback> bySet = registry.resolve(Set.of(), Set.of(), Set.of("order-tools"), false);
        assertEquals(Set.of("order.query", "order.delete"), names(bySet));

        Collection<ToolCallback> mixed = registry.resolve(Set.of("order.query"), Set.of("order"), Set.of(), false);
        assertEquals(Set.of("order.query", "order.delete"), names(mixed));

        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(Set.of(), Set.of(), Set.of("missing-tools"), false));
    }

    @Test
    void enforcesPermissionsAndDangerousToolConfirmationBeforeExecution() {
        AtomicInteger invocations = new AtomicInteger();
        AiToolSecurityMetadata security = new AiToolSecurityMetadata(AiToolRiskLevel.DANGEROUS,
                Set.of("order:delete"), false, true);
        DefaultAiToolRegistry registry = new DefaultAiToolRegistry(
                Set.of(tool("order.delete", Set.of("order"), "order-tools", security, invocations)));
        ToolCallback callback = registry.resolve(Set.of("order.delete"), Set.of(), false).iterator().next();

        assertThrows(AiToolSecurityException.class,
                () -> callback.call("{}", new ToolContext(Map.of())));
        assertThrows(AiToolSecurityException.class,
                () -> callback.call("{}", new ToolContext(Map.of(
                        AiToolContextValues.PERMISSIONS, Set.of("order:delete")))));

        String result = callback.call("{}", new ToolContext(Map.of(
                AiToolContextValues.PERMISSIONS, Set.of("order:delete"),
                AiToolContextValues.CONFIRMED_TOOLS, Set.of("order.delete"))));

        assertEquals("ok", result);
        assertEquals(1, invocations.get());
    }

    private static Set<String> names(Collection<ToolCallback> callbacks) {
        return callbacks.stream().map(callback -> callback.getToolDefinition().name())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static AiGeneratedTool tool(String name, Set<String> groups, String toolSet,
            AiToolSecurityMetadata security, AtomicInteger invocations) {
        ToolCallback callback = new ToolCallback() {
            private final ToolDefinition definition = ToolDefinition.builder()
                    .name(name)
                    .description(name)
                    .inputSchema("{\"type\":\"object\"}")
                    .build();

            @Override
            public ToolDefinition getToolDefinition() {
                return this.definition;
            }

            @Override
            public String call(String toolInput) {
                invocations.incrementAndGet();
                return "ok";
            }
        };
        return new AiGeneratedTool() {
            @Override
            public ToolCallback toolCallback() {
                return callback;
            }

            @Override
            public Set<String> groups() {
                return groups;
            }

            @Override
            public String toolSet() {
                return toolSet;
            }

            @Override
            public AiToolSecurityMetadata security() {
                return security;
            }
        };
    }
}
