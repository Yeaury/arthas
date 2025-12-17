/*
 * Copyright 2024-2024 the original author or authors.
 */

package com.taobao.arthas.mcp.server.task.context;

import com.taobao.arthas.mcp.server.protocol.spec.McpSchema;
import com.taobao.arthas.mcp.server.task.TaskManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of TaskContext that updates the TaskManager.
 *
 * @author Yeaury
 */
public class ArthasTaskContext implements TaskContext {

    private final String taskId;
    private final TaskManager taskManager;
    private final List<String> logs = new ArrayList<>();
    
    private Runnable cancellationHandler;

    public ArthasTaskContext(String taskId, TaskManager taskManager) {
        this.taskId = taskId;
        this.taskManager = taskManager;
    }

    @Override
    public String getTaskId() {
        return taskId;
    }

    @Override
    public void appendOutput(String text) {
        synchronized (logs) {
            logs.add(text);
        }
        
        // Update task output
        // In a real implementation, we might want to buffer this or update less frequently
        // For now, we update on every log to ensure responsiveness
        Map<String, Object> output = new HashMap<>();
        output.put("logs", new ArrayList<>(logs));
        
        taskManager.updateTask(taskId, McpSchema.TaskStatus.WORKING, null, output);
    }

    @Override
    public void updateStatusMessage(String message) {
        taskManager.updateTask(taskId, McpSchema.TaskStatus.WORKING, message, null);
    }

    @Override
    public void reportProgress(long progress, Long total) {
        // Currently Task object doesn't have a standard progress field in the schema we defined
        // We can put it in meta or wait for spec clarification
        // For now, just update status message
        String msg = "Progress: " + progress;
        if (total != null) {
            msg += "/" + total;
        }
        updateStatusMessage(msg);
    }

    @Override
    public void onCancelled(Runnable handler) {
        if (handler == null) {
            return;
        }
        Runnable existing = this.cancellationHandler;
        if (existing == null) {
            this.cancellationHandler = handler;
        } else {
            this.cancellationHandler = () -> {
                existing.run();
                handler.run();
            };
        }
    }

    @Override
    public void cancel() {
        if (cancellationHandler != null) {
            cancellationHandler.run();
        }
    }
}
