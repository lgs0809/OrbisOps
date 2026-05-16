package cn.lgs.orbisops.trigger.ops.runtime;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.*;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/** Bounds the complete response wait, including SSE. Recovery belongs to the outer logical call. */
final class OpsMcpSingleAttemptTransport implements McpClientTransport {
    private final McpClientTransport delegate;
    private final java.util.function.ToIntFunction<cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository.Dispatch> dispatchBudget;
    private final Map<Object, Sinks.One<Void>> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Throwable broken;
    private volatile Runnable invalidator = () -> {};

    OpsMcpSingleAttemptTransport(McpClientTransport delegate,
            java.util.function.ToIntFunction<cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository.Dispatch> dispatchBudget) {
        this.delegate = delegate;
        this.dispatchBudget = dispatchBudget;
    }

    void onBroken(Runnable invalidator) {
        this.invalidator = invalidator;
        if (broken != null) invalidator.run();
    }

    @Override public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        return delegate.connect(message -> handler.apply(message.doOnNext(this::received)))
                .doOnError(this::markBroken).doOnSuccess(ignored -> watchStdioExit());
    }

    private void received(McpSchema.JSONRPCMessage message) {
        if (message instanceof McpSchema.JSONRPCResponse response) {
            Sinks.One<Void> sink = pending.remove(response.id());
            if (sink != null) sink.tryEmitEmpty();
        }
    }

    @Override public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        return Mono.deferContextual(context -> {
            if (closed.get() || broken != null) return Mono.error(failure("CONNECTION_CLOSED", false, broken));
            if (!(message instanceof McpSchema.JSONRPCRequest request)) {
                return delegate.sendMessage(message).onErrorMap(this::mapTransportFailure);
            }
            McpTransportContext metadata = context.getOrDefault(McpTransportContext.KEY, McpTransportContext.EMPTY);
            OpsMcpRequestScope scope = (OpsMcpRequestScope) metadata.get(OpsMcpRequestScope.KEY);
            boolean toolCall = "tools/call".equals(request.method());
            Duration wait = scope == null ? Duration.ofSeconds(15) : scope.remaining(toolCall);
            AtomicBoolean canceled = new AtomicBoolean();
            return Mono.defer(() -> {
                if (toolCall && scope != null) scope.dispatch(request.id(), dispatchBudget);
                if (canceled.get()) return Mono.<Void>error(failure("LOCAL_WAIT_ENDED_BEFORE_SEND", false, null));
                Sinks.One<Void> response = Sinks.one();
                pending.put(request.id(), response);
                return Mono.when(delegate.sendMessage(message), response.asMono())
                        .doFinally(ignored -> pending.remove(request.id(), response));
            }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .doOnCancel(() -> canceled.set(true)).timeout(wait)
                    .onErrorMap(this::mapTransportFailure)
                    .doFinally(ignored -> pending.remove(request.id()));
        });
    }

    private Throwable mapTransportFailure(Throwable error) {
        if (error instanceof OpsMcpCallFailure typed && typed.kind() == OpsMcpCallFailure.Kind.AUTHORITY_DENIED
                || error instanceof SecurityException) return error;
        Throwable mapped = error instanceof McpTransportSessionNotFoundException
                ? failure("SESSION_NOT_FOUND", true, error) : error;
        markBroken(mapped);
        return mapped;
    }

    private void markBroken(Throwable error) {
        if (closed.get()) return;
        broken = error;
        pending.values().forEach(sink -> sink.tryEmitError(error));
        // The callback fences the handle immediately; close is scheduled off reactor/stdio reader threads.
        invalidator.run();
    }

    private void watchStdioExit() {
        if (!(delegate instanceof StdioClientTransport stdio)) return;
        Thread watcher = new Thread(() -> {
            stdio.awaitForExit();
            if (!closed.get()) markBroken(failure("STDIO_PROCESS_EXITED", true, null));
        }, "ops-mcp-stdio-exit");
        watcher.setDaemon(true);
        watcher.start();
    }

    @Override public void setExceptionHandler(Consumer<Throwable> handler) {
        delegate.setExceptionHandler(error -> handler.accept(mapTransportFailure(error)));
    }
    @Override public <T> T unmarshalFrom(Object value, TypeRef<T> type) { return delegate.unmarshalFrom(value, type); }
    @Override public List<String> protocolVersions() { return delegate.protocolVersions(); }
    @Override public Mono<Void> closeGracefully() {
        closed.set(true);
        pending.values().forEach(sink -> sink.tryEmitError(failure("CONNECTION_CLOSED", true, null)));
        return delegate.closeGracefully();
    }
    @Override public void close() { closed.set(true); delegate.close(); }

    private OpsMcpCallFailure failure(String reason, boolean dispatched, Throwable cause) {
        return new OpsMcpCallFailure(OpsMcpCallFailure.Kind.TRANSPORT_ERROR, reason, dispatched, cause);
    }
}
