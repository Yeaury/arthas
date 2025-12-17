/*
 * Copyright 2024-2024 the original author or authors.
 */

package com.taobao.arthas.mcp.server.task;

import com.taobao.arthas.mcp.server.protocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

import com.taobao.arthas.mcp.server.protocol.server.McpNettyServerExchange;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for managing MCP tasks.
 *
 * @author Yeaury
 */
public interface TaskManager {

    /**
     * Set the session exchange for sending notifications.
     * 
     * @param exchange The session exchange
     */
    void setSessionExchange(McpNettyServerExchange exchange);

    /**
     * Create a new task.
     *
     * @param meta Metadata for the task
     * @return The created task
     */
    McpSchema.Task createTask(Map<String, Object> meta);

    /**
     * Get a task by ID.
     *
     * @param taskId Task ID
     * @return The task, or null if not found
     */
    McpSchema.Task getTask(String taskId);

    /**
     * List all tasks, with pagination support.
     *
     * @param cursor Optional cursor for pagination
     * @return List of tasks and next cursor
     */
    McpSchema.ListTasksResult listTasks(String cursor);

    /**
     * Cancel a task.
     *
     * @param taskId Task ID
     * @param reason Reason for cancellation
     * @return The cancelled task, or null if not found
     */
    McpSchema.Task cancelTask(String taskId, String reason);

    /**
     * Update task status and output.
     *
     * @param taskId        Task ID
     * @param status        New status
     * @param statusMessage Status message
     * @param output        Task output (partial or full)
     * @return The updated task
     */
    McpSchema.Task updateTask(String taskId, McpSchema.TaskStatus status, String statusMessage, Map<String, Object> output);

    /**
     * Wait for task result (blocking/async).
     *
     * @param taskId Task ID
     * @return Future that completes when task finishes
     */
    CompletableFuture<McpSchema.GetTaskResult> waitForTask(String taskId);

}
