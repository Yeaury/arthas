package com.taobao.arthas.mcp.server.tool.definition;

import com.taobao.arthas.mcp.server.protocol.spec.McpSchema;

public class ToolDefinition {
    private String name;

    private String description;

    private McpSchema.JsonSchema inputSchema;

    private boolean streamable;
    
    private String taskSupport;

    public ToolDefinition(String name, String description,
                          McpSchema.JsonSchema inputSchema, boolean streamable, String taskSupport) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.streamable = streamable;
        this.taskSupport = taskSupport;
    }
    
    public ToolDefinition(String name, String description,
                          McpSchema.JsonSchema inputSchema, boolean streamable) {
        this(name, description, inputSchema, streamable, "forbidden");
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public McpSchema.JsonSchema getInputSchema() {
        return inputSchema;
    }

    public boolean isStreamable() {
        return streamable;
    }
    
    public String taskSupport() {
        return taskSupport;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;

        private String description;

        private McpSchema.JsonSchema inputSchema;

        private boolean streamable;
        
        private String taskSupport = "forbidden";

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(McpSchema.JsonSchema inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder streamable(boolean streamable) {
            this.streamable = streamable;
            return this;
        }
        
        public Builder taskSupport(String taskSupport) {
            this.taskSupport = taskSupport;
            return this;
        }

        public ToolDefinition build() {
            return new ToolDefinition(name, description, inputSchema, streamable, taskSupport);
        }
    }
}
