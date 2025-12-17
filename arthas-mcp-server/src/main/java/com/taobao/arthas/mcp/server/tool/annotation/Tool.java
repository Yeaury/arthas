package com.taobao.arthas.mcp.server.tool.annotation;

import java.lang.annotation.*;

@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Tool {

    String name() default "";

    String description() default "";

    boolean streamable() default false;
    
    /**
     * Task support negotiation.
     * "required": Must be called as a task.
     * "optional": Can be called as a task.
     * "forbidden": Cannot be called as a task (default).
     */
    String taskSupport() default "forbidden";

}
