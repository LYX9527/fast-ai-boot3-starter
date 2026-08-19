package io.github.lyx9527.fastai.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import io.github.lyx9527.fastai.annotation.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 将带 {@link LLMFunctionCalling} 注解的业务方法编译生成为 Spring AI Tool Adapter。
 */
@AutoService(Processor.class)
public final class LLMFunctionProcessor extends AbstractProcessor {

    /** DeepSeek/OpenAI Tool 名称允许使用的字符格式。 */
    private static final Pattern TOOL_NAME = Pattern.compile("^[a-zA-Z0-9_-]+$");
    /** 工具集名称允许使用的字符格式；工具集名称不会直接发送给模型。 */
    private static final Pattern TOOL_SET_NAME = Pattern.compile("^[a-zA-Z0-9_.-]+$");
    /** 生成 Adapter 中统一使用的桥接方法名称。 */
    private static final String BRIDGE_METHOD = "invokeTool";

    /** 已处理方法签名，避免多轮 APT 重复生成。 */
    private final Set<String> processedMethods = new HashSet<>();
    /** 当前编译任务已使用的 Tool 名称，用于重复名称校验。 */
    private final Set<String> toolNames = new HashSet<>();

    /** 向编译器输出提示和错误信息的消息工具。 */
    private Messager messager;
    /** 向编译输出目录写入生成源码的文件工具。 */
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        this.messager = processingEnvironment.getMessager();
        this.filer = processingEnvironment.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(LLMFunctionCalling.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_17;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(LLMFunctionCalling.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                error(element, "@LLMFunctionCalling 只能标注在方法上");
                continue;
            }
            ExecutableElement method = (ExecutableElement) element;
            String methodKey = method.getEnclosingElement() + "#" + method;
            if (!this.processedMethods.add(methodKey) || !validate(method)) {
                continue;
            }
            try {
                generate(method);
            }
            catch (IOException exception) {
                error(method, "生成 LLM Tool Adapter 失败：" + exception.getMessage());
            }
        }
        return true;
    }

    private boolean validate(ExecutableElement method) {
        LLMFunctionCalling annotation = method.getAnnotation(LLMFunctionCalling.class);
        TypeElement owner = (TypeElement) method.getEnclosingElement();
        LLMToolSet toolSet = owner.getAnnotation(LLMToolSet.class);
        boolean valid = true;
        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            error(method, "@LLMFunctionCalling 方法必须是 public");
            valid = false;
        }
        if (method.getModifiers().contains(Modifier.STATIC)) {
            error(method, "@LLMFunctionCalling 方法必须是实例方法");
            valid = false;
        }
        if (!method.getTypeParameters().isEmpty()) {
            error(method, "暂不支持泛型 @LLMFunctionCalling 方法");
            valid = false;
        }
        if (!TOOL_NAME.matcher(annotation.name()).matches()) {
            error(method, "Tool 名称只能包含字母、数字、下划线或短横线");
            valid = false;
        }
        if (!this.toolNames.add(annotation.name())) {
            error(method, "LLM Tool 名称重复：" + annotation.name());
            valid = false;
        }
        if (toolSet != null && !TOOL_SET_NAME.matcher(toolSet.name()).matches()) {
            error(owner, "工具集名称只能包含字母、数字、下划线、短横线或点号");
            valid = false;
        }
        for (VariableElement parameter : method.getParameters()) {
            if (parameter.getAnnotation(InjectCtx.class) != null && parameter.asType().getKind().isPrimitive()) {
                error(parameter, "@InjectCtx 不支持基本类型参数，请使用对应包装类型");
                valid = false;
            }
        }
        return valid;
    }

    private void generate(ExecutableElement method) throws IOException {
        TypeElement owner = (TypeElement) method.getEnclosingElement();
        LLMFunctionCalling tool = method.getAnnotation(LLMFunctionCalling.class);
        String packageName = this.processingEnv.getElementUtils().getPackageOf(owner).getQualifiedName().toString();
        String signature = owner.getQualifiedName() + "#" + method;
        String suffix = String.format(Locale.ROOT, "%08x", signature.hashCode());
        String generatedClassName = owner.getSimpleName() + toPascalCase(method.getSimpleName().toString())
                + suffix + "ToolAdapter";

        ClassName generatedTool = ClassName.get("io.github.lyx9527.fastai.tool", "AiGeneratedTool");
        ClassName toolCallback = ClassName.get("org.springframework.ai.tool", "ToolCallback");
        ClassName toolContext = ClassName.get("org.springframework.ai.chat.model", "ToolContext");
        ClassName toolParam = ClassName.get("org.springframework.ai.tool.annotation", "ToolParam");
        ClassName callbackSupport = ClassName.get("io.github.lyx9527.fastai.tool", "AiToolCallbacks");
        ClassName contextValues = ClassName.get("io.github.lyx9527.fastai.tool", "AiToolContextValues");
        ClassName securityMetadata = ClassName.get("io.github.lyx9527.fastai.tool", "AiToolSecurityMetadata");
        ClassName riskLevel = ClassName.get("io.github.lyx9527.fastai.tool", "AiToolRiskLevel");
        ClassName component = ClassName.get("org.springframework.stereotype", "Component");
        ClassName set = ClassName.get(Set.class);

        TypeName ownerType = TypeName.get(owner.asType());
        FieldSpec targetField = FieldSpec.builder(ownerType, "target", Modifier.PRIVATE, Modifier.FINAL)
                .addJavadoc("原始业务 Service 实例。\n")
                .build();
        FieldSpec callbackField = FieldSpec.builder(toolCallback, "callback", Modifier.PRIVATE, Modifier.FINAL)
                .addJavadoc("Spring AI ToolCallback。\n")
                .build();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ownerType, "target")
                .addStatement("this.target = target")
                .addStatement("this.callback = $T.forBridge(this, $S, $S, $S, $L)", callbackSupport,
                        BRIDGE_METHOD, tool.name(), tool.description(), tool.returnDirect())
                .build();

        MethodSpec bridge = buildBridgeMethod(method, toolContext, toolParam, contextValues);
        MethodSpec callbackMethod = MethodSpec.methodBuilder("toolCallback")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(toolCallback)
                .addStatement("return this.callback")
                .build();

        LLMToolSet toolSet = owner.getAnnotation(LLMToolSet.class);
        String toolSetName = toolSet == null ? "" : toolSet.name();
        String toolSetDescription = toolSet == null ? "" : toolSet.description();
        MethodSpec toolSetMethod = MethodSpec.methodBuilder("toolSet")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", toolSetName)
                .build();
        MethodSpec toolSetDescriptionMethod = MethodSpec.methodBuilder("toolSetDescription")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", toolSetDescription)
                .build();

        Set<String> groups = mergedGroups(owner, method, tool);
        MethodSpec groupsMethod = MethodSpec.methodBuilder("groups")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(this.processingEnv.getTypeUtils().getDeclaredType(
                        this.processingEnv.getElementUtils().getTypeElement(Set.class.getCanonicalName()),
                        this.processingEnv.getElementUtils().getTypeElement(String.class.getCanonicalName()).asType())))
                .addStatement("return $L", stringSet(set, groups))
                .build();

        LLMToolSecurity security = method.getAnnotation(LLMToolSecurity.class);
        if (security == null) {
            security = owner.getAnnotation(LLMToolSecurity.class);
        }
        LLMToolRiskLevel risk = security == null ? LLMToolRiskLevel.READ_ONLY : security.risk();
        Set<String> permissions = security == null ? Set.of() : normalizedSet(security.permissions());
        boolean requireConfirmation = security != null && security.requireConfirmation();
        boolean audit = security == null || security.audit();
        MethodSpec securityMethod = MethodSpec.methodBuilder("security")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(securityMetadata)
                .addStatement("return new $T($T.$L, $L, $L, $L)", securityMetadata, riskLevel, risk.name(),
                        stringSet(set, permissions), requireConfirmation, audit)
                .build();

        TypeSpec adapter = TypeSpec.classBuilder(generatedClassName)
                .addJavadoc("为 {@link $T#$L} 生成的 LLM Tool Adapter。\n", ownerType,
                        method.getSimpleName())
                .addAnnotation(component)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", LLMFunctionProcessor.class.getName())
                        .build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(generatedTool)
                .addField(targetField)
                .addField(callbackField)
                .addMethod(constructor)
                .addMethod(callbackMethod)
                .addMethod(toolSetMethod)
                .addMethod(toolSetDescriptionMethod)
                .addMethod(groupsMethod)
                .addMethod(securityMethod)
                .addMethod(bridge)
                .build();

        JavaFile.builder(packageName, adapter)
                .skipJavaLangImports(true)
                .indent("    ")
                .build()
                .writeTo(this.filer);
        this.messager.printMessage(Diagnostic.Kind.NOTE,
                "已生成 LLM Tool Adapter：" + packageName + '.' + generatedClassName, method);
    }

    private static Set<String> mergedGroups(TypeElement owner, ExecutableElement method,
            LLMFunctionCalling tool) {
        Set<String> groups = new LinkedHashSet<>();
        LLMToolGroup typeGroups = owner.getAnnotation(LLMToolGroup.class);
        if (typeGroups != null) {
            addNormalized(groups, typeGroups.value());
        }
        LLMToolGroup methodGroups = method.getAnnotation(LLMToolGroup.class);
        if (methodGroups != null) {
            addNormalized(groups, methodGroups.value());
        }
        addNormalized(groups, tool.groups());
        return groups;
    }

    private static Set<String> normalizedSet(String[] values) {
        Set<String> result = new LinkedHashSet<>();
        addNormalized(result, values);
        return result;
    }

    private static void addNormalized(Set<String> target, String[] values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                target.add(value);
            }
        }
    }

    private static CodeBlock stringSet(ClassName set, Set<String> values) {
        CodeBlock.Builder code = CodeBlock.builder().add("$T.of(", set);
        int index = 0;
        for (String value : values) {
            if (index++ > 0) {
                code.add(", ");
            }
            code.add("$S", value);
        }
        return code.add(")").build();
    }

    private MethodSpec buildBridgeMethod(ExecutableElement method, ClassName toolContext, ClassName toolParam,
            ClassName contextValues) {
        MethodSpec.Builder bridge = MethodSpec.methodBuilder(BRIDGE_METHOD)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(method.getReturnType()));
        method.getThrownTypes().forEach(type -> bridge.addException(TypeName.get(type)));

        List<CodeBlock> invocationArguments = new ArrayList<>();
        boolean needsContext = false;
        for (VariableElement parameter : method.getParameters()) {
            String parameterName = parameter.getSimpleName().toString();
            InjectCtx injectCtx = parameter.getAnnotation(InjectCtx.class);
            if (injectCtx == null) {
                ParameterSpec.Builder parameterSpec = ParameterSpec
                        .builder(TypeName.get(parameter.asType()), parameterName);
                LLMParameter llmParameter = parameter.getAnnotation(LLMParameter.class);
                String description = llmParameter == null ? "参数 " + parameterName : llmParameter.description();
                boolean required = llmParameter == null || llmParameter.required();
                parameterSpec.addAnnotation(AnnotationSpec.builder(toolParam)
                        .addMember("description", "$S", description)
                        .addMember("required", "$L", required)
                        .build());
                bridge.addParameter(parameterSpec.build());
                invocationArguments.add(CodeBlock.of("$L", parameterName));
            }
            else {
                needsContext = true;
                String contextKey = injectCtx.value().isBlank() ? parameterName : injectCtx.value();
                TypeMirror erasedType = this.processingEnv.getTypeUtils().erasure(parameter.asType());
                invocationArguments.add(CodeBlock.of("$T.required(toolContext, $S, $T.class)", contextValues,
                        contextKey, TypeName.get(erasedType)));
            }
        }
        if (needsContext) {
            bridge.addParameter(toolContext, "toolContext");
        }

        CodeBlock.Builder invocation = CodeBlock.builder().add("this.target.$L(", method.getSimpleName());
        for (int index = 0; index < invocationArguments.size(); index++) {
            if (index > 0) {
                invocation.add(", ");
            }
            invocation.add("$L", invocationArguments.get(index));
        }
        invocation.add(")");
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            bridge.addStatement("$L", invocation.build());
        }
        else {
            bridge.addStatement("return $L", invocation.build());
        }
        return bridge.build();
    }

    private static String toPascalCase(String value) {
        StringBuilder result = new StringBuilder();
        boolean upper = true;
        for (char character : value.toCharArray()) {
            if (!Character.isLetterOrDigit(character)) {
                upper = true;
            }
            else if (upper) {
                result.append(Character.toUpperCase(character));
                upper = false;
            }
            else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private void error(Element element, String message) {
        this.messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
