from agent.agents_mode.agents.simple_agents import react_agent

if __name__ == '__main__':
    ans = ""
    for chunk in react_agent._stream_impl("帮我写一篇关于学习Dify的博客"):
        print(chunk, flush=True)
        if chunk and chunk['content'] is not None:
            ans += str(chunk['content'])
    # print(ans)
