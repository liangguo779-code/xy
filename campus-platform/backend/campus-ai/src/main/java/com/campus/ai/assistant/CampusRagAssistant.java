package com.campus.ai.assistant;

import com.campus.ai.memory.MemoryStore;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 声明式 RAG 助手接口，基于 LangChain4j AiServices。
 *
 * <p>用标准的 LangChain4j 模式替代手写的循环逻辑：
 * 系统提示词 + 对话记忆 + 检索增强 + 工具调用 —— 全部由框架自动装配。
 *
 * <p>{@code @MemoryId} 参数将每次会话绑定到独立的
 * {@link dev.langchain4j.memory.ChatMemory}（由 {@link MemoryStore} 支持，持久化到 MySQL）。
 * {@code @UserMessage} 携带当前问题和检索到的上下文。
 *
 * <p>工具（如 {@link com.campus.ai.tool.CalendarTool}）在构建时通过
 * {@code AiServices.builder(...).tools(...)} 注册。
 *
 * <p>Bean 注册方式：不在接口上加 {@code @AiService}，而是在 {@link com.campus.ai.config.LangChain4jConfig}
 * 中通过 {@code @Bean} 方法手动构建，以便配置 ChatMemory（MySQL 持久化）和 Tools。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>LLM 无法接收系统提示词和检索上下文，生成的回答将没有格式和引用规范</li>
 *   <li>对话记忆（多轮上下文）将丢失，LLM 每次都像新对话一样回答</li>
 *   <li>工具调用（如校历查询）将不可用，LLM 无法主动调用外部能力</li>
 * </ul>
 */
public interface CampusRagAssistant {

    /**
     * 带检索上下文的对话。调度器从检索结果构建上下文字符串并传入此处。
     * ChatMemory（由 {@link MemoryStore} 支持）自动管理多轮历史。
     * 工具（如 CalendarTool）在对话过程中由 LLM 主动调用。
     */
    @SystemMessage("""
            你是校园事务咨询助手。请根据下方"参考信息"回答学生问题，回答中用 [来源X] 标注引用。
            规则：
            1. 优先依据参考信息回答；参考信息不足时礼貌说明并给出建议联系部门。
            2. 不要编造参考信息中没有的制度、流程、数字。
            3. 引用编号必须与参考信息中 [来源X] 一一对应。
            4. 回答前先用一句简短寒暄或确认，再正式回答；保持简洁。
            5. 如果用户的问题涉及校历、考试安排、课表等，优先使用提供的工具查询。""")
    @UserMessage("""
            参考信息：
            {{context}}

            请按要求回答学生问题。

            学生问题: {{question}}""")
    String chatWithContext(@MemoryId Long memoryId, @V("context") String context, @V("question") String question);
}
