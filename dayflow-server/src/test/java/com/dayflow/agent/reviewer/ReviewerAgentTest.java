package com.dayflow.agent.reviewer;

import com.dayflow.agent.AgentInvoker;
import com.dayflow.agent.model.AgentResult;
import com.dayflow.agent.model.CollectedMaterial;
import com.dayflow.agent.model.DraftReport;
import com.dayflow.agent.model.ReviewResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReviewerAgent 测试：验证审校调用与结果透传。
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class ReviewerAgentTest {

    @Mock
    private AgentInvoker invoker;
    @Mock
    private ChatClient reviewerChatClient;

    @InjectMocks
    private ReviewerAgent reviewer;

    @Test
    void reviewInvokesReviewerChatClient() {
        DraftReport draft = new DraftReport();
        CollectedMaterial material = new CollectedMaterial();
        ReviewResult review = new ReviewResult();
        review.setPassed(true);
        when(invoker.invoke(eq(reviewerChatClient), any(String.class), eq(ReviewResult.class)))
                .thenReturn(new AgentResult<>(review, 70, 300));

        AgentResult<ReviewResult> result = reviewer.review(draft, material);

        assertSame(review, result.payload());
        verify(invoker).invoke(eq(reviewerChatClient), any(String.class), eq(ReviewResult.class));
    }
}
