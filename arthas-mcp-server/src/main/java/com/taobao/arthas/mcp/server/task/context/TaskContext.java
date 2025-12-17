/*
 * Copyright 2024-2024 the original author or authors.
 */

package com.taobao.arthas.mcp.server.task.context;

import com.taobao.arthas.mcp.server.protocol.spec.McpSchema;

/**
 * Context for executing a task.
 * Allows tools to report progress and append output.
 *
 * @author Yeaury
 */
public interface TaskContext {

    /**
     * Get the task ID.
     *
     * @return Task ID
     */
    String getTaskId();

    /**
     * Append text to the task output logs.
     *
     * @param text Log text
     */
    void appendOutput(String text);

    /**
     * Update task status message.
     *
     * @param message Status message
     */
    void updateStatusMessage(String message);
    
    /**
     * Report progress.
     * 
     * @param progress Progress value (0-100)
     * @param total Total steps (optional)
     */
    void reportProgress(long progress, Long total);
    
    /**
     * Set the cancellation handler.
     * 
     * @param handler The handler to run when task is cancelled
     */
    void onCancelled(Runnable handler);
    
    /**
     * Cancel the task.
     */
    void cancel();
}
