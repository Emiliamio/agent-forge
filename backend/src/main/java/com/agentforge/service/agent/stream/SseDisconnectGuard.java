package com.agentforge.service.agent.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 流式长连接断网即时止血与保活卫士 (SSE Disconnect Guard)
 * 1. 客户端中途关闭标签页/断网时，毫秒级触发 doOnCancel 停止向上游大模型拉取 Token (立停计费)
 * 2. 每隔 15 秒注入 SSE 心跳信号，防止企业 Nginx/防火墙超时掐断连接
 */
@Slf4j
@Component
public class SseDisconnectGuard {

    /**
     * 包装流式 Flux，附加断网止血与心跳保活装甲
     *
     * @param sourceStream 原始大模型流式 Token 输出
     * @param sessionTitle 会话追踪标识
     * @return 装甲保护后的 SSE 流
     */
    public Flux<ServerSentEvent<String>> wrapStreamWithArmor(Flux<String> sourceStream, String sessionTitle) {
        AtomicBoolean isClientActive = new AtomicBoolean(true);

        // 1. 业务内容流
        Flux<ServerSentEvent<String>> contentFlux = sourceStream
                .map(token -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(token)
                        .build())
                .doOnCancel(() -> {
                    isClientActive.set(false);
                    log.warn("🛑 [客户端断网/关闭窗口] 触发即时止血拦截: session={}, 已中断大模型继续生成，停止扣费！", sessionTitle);
                })
                .doFinally(signalType -> {
                    log.info("🏁 SSE 流式会话正常终结: session={}, signal={}", sessionTitle, signalType);
                });

        // 2. 15秒心跳流 (防止企业级 Nginx 60s proxy_read_timeout 掐断)
        Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .takeWhile(i -> isClientActive.get())
                .map(i -> ServerSentEvent.<String>builder()
                        .event("ping")
                        .data("keep-alive")
                        .build());

        // 3. 混合输出
        return Flux.merge(contentFlux, heartbeatFlux);
    }
}
