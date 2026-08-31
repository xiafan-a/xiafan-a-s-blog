package com.xiafan.agent.service.agent;

import java.util.Map;

/**
 * Executor for a tool definition, mirroring the Python executor callables in
 * toolRegistryService / builtInTools / api/agent.py api_executor.
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * Executes the tool with the (already validated and default-filled) parameters.
     *
     * @return the raw result value (usually a Map, but may be any JSON-serializable value)
     */
    Object execute(Map<String, Object> params) throws Exception;
}