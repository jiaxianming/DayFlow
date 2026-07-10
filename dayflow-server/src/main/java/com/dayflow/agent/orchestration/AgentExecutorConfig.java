package com.dayflow.agent.orchestration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 异步执行线程池配置。
 * <p>报告生成是重 LLM 任务，单用户低并发：core=2/max=4/queue=10 足够；高并发留 M5。
 * 拒绝策略 CallerRunsPolicy：队列满时由调用线程兜底执行，不丢任务。</p>
 *
 * @author jiaxianming
 */
@Configuration
public class AgentExecutorConfig {

    /**
     * 专用线程池 dayflow-agent-executor
     * <p>编排层（ReportOrchestrationService）将 run(...) 提交到此 executor 执行，
     * 不使用 @Async 以规避同类自调用失效的坑。</p>
     *
     * @return 配置好的 ThreadPoolTaskExecutor
     */
    @Bean(name = "dayflow-agent-executor")
    public ThreadPoolTaskExecutor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("agent-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
