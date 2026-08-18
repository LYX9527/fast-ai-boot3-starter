package io.github.lyx9527.fastai.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;

class LLMFunctionProcessorTest {

    @Test
    void generatesToolBridgeAndHidesContextParameter() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceString("sample.OrderService", """
                package sample;

                import io.github.lyx9527.fastai.annotation.InjectCtx;
                import io.github.lyx9527.fastai.annotation.LLMFunctionCalling;
                import io.github.lyx9527.fastai.annotation.LLMParameter;

                public class OrderService {
                    @LLMFunctionCalling(
                        name = "order.query",
                        description = "Query an order",
                        groups = {"order"}
                    )
                    public String query(
                            @LLMParameter(description = "Order number") String orderNo,
                            @InjectCtx("userId") String userId) {
                        return orderNo + userId;
                    }
                }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new LLMFunctionProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        String generated = compilation.generatedSourceFiles().stream()
                .map(this::content)
                .collect(Collectors.joining("\n"));
        assertThat(generated).contains("implements AiGeneratedTool");
        assertThat(generated).contains("@ToolParam");
        assertThat(generated).contains("AiToolContextValues.required(toolContext, \"userId\", String.class)");
        assertThat(generated).contains("Set.of(\"order\")");
    }

    @Test
    void generatesToolSetMergedGroupsAndSecurityMetadata() {
        JavaFileObject source = JavaFileObjects.forSourceString("sample.OrderService", """
                package sample;

                import io.github.lyx9527.fastai.annotation.LLMFunctionCalling;
                import io.github.lyx9527.fastai.annotation.LLMToolGroup;
                import io.github.lyx9527.fastai.annotation.LLMToolRiskLevel;
                import io.github.lyx9527.fastai.annotation.LLMToolSecurity;
                import io.github.lyx9527.fastai.annotation.LLMToolSet;

                @LLMToolSet(name = "order-tools", description = "Order tools")
                @LLMToolGroup({"service", "shared"})
                @LLMToolSecurity(
                    risk = LLMToolRiskLevel.READ_ONLY,
                    permissions = {"order:read"}
                )
                public class OrderService {

                    @LLMFunctionCalling(
                        name = "order.query",
                        description = "Query an order",
                        groups = {"legacy", "shared"}
                    )
                    @LLMToolGroup({"query", "shared"})
                    public String query(String orderNo) {
                        return orderNo;
                    }

                    @LLMFunctionCalling(
                        name = "order.delete",
                        description = "Delete an order"
                    )
                    @LLMToolSecurity(
                        risk = LLMToolRiskLevel.DANGEROUS,
                        permissions = {"order:delete"},
                        requireConfirmation = true,
                        audit = false
                    )
                    public boolean delete(String orderNo) {
                        return true;
                    }
                }
                """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new LLMFunctionProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        String generated = compilation.generatedSourceFiles().stream()
                .map(this::content)
                .collect(Collectors.joining("\n"));
        assertThat(generated).contains("return \"order-tools\";");
        assertThat(generated).contains("return \"Order tools\";");
        assertThat(generated).contains("Set.of(\"service\", \"shared\", \"query\", \"legacy\")");
        assertThat(generated).contains("AiToolRiskLevel.READ_ONLY");
        assertThat(generated).contains("Set.of(\"order:read\")");
        assertThat(generated).contains("AiToolRiskLevel.DANGEROUS");
        assertThat(generated).contains("Set.of(\"order:delete\")");
        assertThat(generated).contains("true, false");

        String deleteAdapter = compilation.generatedSourceFiles().stream()
                .filter(file -> file.getName().contains("OrderServiceDelete"))
                .findFirst()
                .map(this::content)
                .orElseThrow();
        assertThat(deleteAdapter).contains("Set.of(\"order:delete\")");
        assertThat(deleteAdapter).doesNotContain("order:read");
    }

    private String content(JavaFileObject source) {
        try {
            return source.getCharContent(false).toString();
        }
        catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
