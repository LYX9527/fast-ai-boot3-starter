package io.github.lyx9527.fastai.intent;

/**
 * 业务系统统一使用的意图识别服务。
 */
public interface IntentRecognitionService {

    /**
     * 识别用户消息对应的业务意图和槽位。
     *
     * @param request 意图识别请求
     * @return 意图识别结果；无法可靠识别时返回 unknown
     */
    AiIntentResult recognize(AiIntentRequest request);
}
