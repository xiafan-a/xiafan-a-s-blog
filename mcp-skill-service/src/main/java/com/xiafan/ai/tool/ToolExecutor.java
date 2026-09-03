package com.xiafan.ai.tool;

import java.util.Map;

@FunctionalInterface
public interface ToolExecutor {

    Object execute(Map<String, Object> params) throws Exception;
}
