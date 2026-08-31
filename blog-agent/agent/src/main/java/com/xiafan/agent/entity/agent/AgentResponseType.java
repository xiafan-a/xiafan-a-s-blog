package com.xiafan.agent.entity.agent;

/**
 * Mirrors entity/Tool.py RESPONSE_TYPE_* constants.
 */
public final class AgentResponseType {
    public static final String THOUGHT = "thought";
    public static final String ACTION = "action";
    public static final String OBSERVATION = "observation";
    public static final String STEP_DONE = "step_done";
    public static final String SUMMARY = "summary";

    private AgentResponseType() {
    }
}