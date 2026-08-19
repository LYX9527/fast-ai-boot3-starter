package io.github.lyx9527.fastai.autoconfigure;

import io.github.lyx9527.fastai.memory.AiMemoryExtractor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认长期记忆提取器。
 * <p>
 * 只从用户明确表达的稳定事实、偏好或记忆指令中提取内容，不发起额外的模型请求。
 * 助手回复不参与默认提取，避免将模型推断或幻觉写入用户长期记忆。
 */
final class DefaultAiMemoryExtractor implements AiMemoryExtractor {

    /** 单个候选记忆允许进入持久化流程的最大字符数。 */
    private static final int MAX_CANDIDATE_LENGTH = 1000;
    /** 按中英文句末符号切分用户消息。 */
    private static final Pattern SENTENCE_SEPARATOR = Pattern.compile("[\\r\\n。！？!?；;.]+");
    /** 用户明确要求系统记住后续内容的表达。 */
    private static final Pattern EXPLICIT_MEMORY = Pattern.compile(
            "^\\s*(?:请|麻烦)?(?:你)?(?:帮我)?(?:记住|记得)(?:一下)?(?:这件事)?[\\s：:，,]*(.+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** 用户明确拒绝保存当前信息的表达，优先级高于其他提取规则。 */
    private static final Pattern MEMORY_OPT_OUT = Pattern.compile(
            "(?:不要|别|无需|不用|不必).{0,6}(?:记住|记得|保存|记录)|"
                    + "(?:do not|don't|never)\\s+(?:remember|store|save|record)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** 不应进入长期记忆的凭证、身份号码和支付信息。 */
    private static final Pattern SENSITIVE_CONTENT = Pattern.compile(
            "密码|口令|验证码|密钥|秘钥|身份证|银行卡|信用卡|支付密码|"
                    + "api[ _-]?key|access[ _-]?key|secret|password|passcode|private[ _-]?key|"
                    + "bearer\\s+[a-z0-9._-]+|sk-[a-z0-9_-]{8,}|cvv|social security",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** 明确只在当前或近期有效的临时信息。 */
    private static final Pattern TRANSIENT_CONTENT = Pattern.compile(
            "今天|明天|后天|今晚|今早|本次|这次|临时|暂时|稍后|一会儿|一次性|"
                    + "today|tomorrow|tonight|temporary|one[- ]time|for this (?:request|turn|time)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** 可直接认定为稳定用户事实或偏好的中文表达。 */
    private static final List<Pattern> CHINESE_DURABLE_FACTS = List.of(
            Pattern.compile("((?:我|本人)(?:一直|通常|平时|一般|比较|非常|特别|最|更)?"
                    + "(?:喜欢|偏好|偏爱|爱吃|爱喝|常用|习惯于|习惯使用|不喜欢|讨厌|不吃|不喝)"
                    + "[^，,。！？!?；;]+)"),
            Pattern.compile("((?:我|本人)对[^，,。！？!?；;]{1,80}过敏)"),
            Pattern.compile("((?:我的|本人)(?:名字|姓名|称呼|昵称)(?:是|叫)[^，,。！？!?；;]+)"),
            Pattern.compile("((?:我|本人)(?:来自|住在|居住在|长期居住在)[^，,。！？!?；;]+)"),
            Pattern.compile("((?:我|本人)(?:在[^，,。！？!?；;]{1,60})?(?:工作|任职|就职)[^，,。！？!?；;]*)"),
            Pattern.compile("((?:我的|本人)(?:职业|岗位|职位|母语|常用语言|首选语言)(?:是|为)"
                    + "[^，,。！？!?；;]+)"));
    /** 可直接认定为稳定用户事实或偏好的英文表达。 */
    private static final List<Pattern> ENGLISH_DURABLE_FACTS = List.of(
            Pattern.compile("\\b(i\\s+(?:always\\s+|usually\\s+|generally\\s+)?"
                    + "(?:like|love|prefer|favor|use|dislike|hate|avoid|do not eat|don't eat|"
                    + "am allergic to)\\b[^.!?;]*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(i\\s+(?:live|reside|work)\\s+(?:in|at|for)\\s+[^.!?;]+)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(my\\s+(?:name|nickname|job|occupation|role|native language|"
                    + "preferred language)\\s+is\\s+[^.!?;]+)", Pattern.CASE_INSENSITIVE));

    @Override
    public List<String> extract(String userMessage, String assistantResponse) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }

        Set<String> memories = new LinkedHashSet<>();
        for (String sentence : SENTENCE_SEPARATOR.split(userMessage)) {
            collectSentenceMemories(normalize(sentence), memories);
        }
        return new ArrayList<>(memories);
    }

    private void collectSentenceMemories(String sentence, Set<String> memories) {
        if (sentence.isBlank() || MEMORY_OPT_OUT.matcher(sentence).find()
                || containsUnsafeOrTransientContent(sentence)) {
            return;
        }

        Matcher explicitMatcher = EXPLICIT_MEMORY.matcher(sentence);
        if (explicitMatcher.find()) {
            addCandidate(explicitMatcher.group(1), memories);
            return;
        }

        if (collectFirstMatch(sentence, CHINESE_DURABLE_FACTS, memories)) {
            return;
        }
        collectFirstMatch(sentence, ENGLISH_DURABLE_FACTS, memories);
    }

    private boolean collectFirstMatch(String sentence, List<Pattern> patterns, Set<String> memories) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(sentence);
            if (matcher.find()) {
                addCandidate(matcher.group(1), memories);
                return true;
            }
        }
        return false;
    }

    private void addCandidate(String value, Set<String> memories) {
        String candidate = normalize(value);
        if (candidate.isBlank() || candidate.length() > MAX_CANDIDATE_LENGTH
                || containsUnsafeOrTransientContent(candidate)) {
            return;
        }
        memories.add(candidate);
    }

    private boolean containsUnsafeOrTransientContent(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        return SENSITIVE_CONTENT.matcher(normalized).find() || TRANSIENT_CONTENT.matcher(normalized).find();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.strip()
                .replaceAll("^[：:，,\\s]+", "")
                .replaceAll("[。！？!?；;\\s]+$", "")
                .replaceAll("\\s+", " ");
    }
}
