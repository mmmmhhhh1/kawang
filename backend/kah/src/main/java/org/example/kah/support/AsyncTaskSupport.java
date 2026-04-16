package org.example.kah.support;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AsyncTaskSupport {

    private final Executor ioTaskExecutor;

    public AsyncTaskSupport(@Qualifier("ioTaskExecutor") Executor ioTaskExecutor) {
        this.ioTaskExecutor = ioTaskExecutor;
    }

    public CompletableFuture<Void> runAsync(Runnable action) {
        return CompletableFuture.runAsync(action, ioTaskExecutor);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, ioTaskExecutor);
    }

    public <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            throw unwrap(exception);
        }
    }

    private RuntimeException unwrap(CompletionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Async task failed", cause);
    }
}