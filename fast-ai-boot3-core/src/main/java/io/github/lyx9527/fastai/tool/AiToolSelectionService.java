package io.github.lyx9527.fastai.tool;

/**
 * 基于用户语义自动选择请求级 Tool 的服务。
 */
public interface AiToolSelectionService {

    /**
     * 从已注册 Tool 目录中选择当前请求真正需要的 Tool。
     *
     * @param request Tool 语义选择请求
     * @return Tool 选择结果；不需要或无法可靠选择时返回空集合
     */
    AiToolSelectionResult select(AiToolSelectionRequest request);
}
