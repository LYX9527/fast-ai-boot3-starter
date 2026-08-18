package io.github.lyx9527.fastai.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicAiTokenEstimatorTest {

    private final HeuristicAiTokenEstimator estimator = new HeuristicAiTokenEstimator();

    @Test
    void estimatesCjkAndLatinText() {
        assertEquals(5, this.estimator.estimate("上下文压缩"));
        assertEquals(1, this.estimator.estimate("test"));
        assertTrue(this.estimator.estimate("企业级 AI context compression") > 4);
    }
}
