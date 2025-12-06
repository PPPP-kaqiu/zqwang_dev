// AI Service for PaperMind
const AIService = {
    async callAIStream(messages, onChunk) {
        const settings = await Storage.getSettings();
        
        if (!settings.openai_key) {
            throw new Error("请先在设置中配置 API Key。");
        }

        const modelName = settings.model || "deepseek-chat";
        const isDeepSeek = modelName.toLowerCase().includes("deepseek");
        const baseUrl = isDeepSeek ? 'https://api.deepseek.com' : 'https://api.openai.com/v1';
        const endpoint = `${baseUrl}/chat/completions`;

        try {
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${settings.openai_key}`
                },
                body: JSON.stringify({
                    model: modelName,
                    messages: messages,
                    stream: true
                })
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error?.message || "API 请求失败");
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder("utf-8");
            let fullText = "";
            let buffer = "";

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                
                const chunk = decoder.decode(value, { stream: true });
                buffer += chunk;
                
                const lines = buffer.split('\n');
                buffer = lines.pop(); // Keep the last partial line

                for (const line of lines) {
                    const trimmed = line.trim();
                    if (trimmed.startsWith('data: ')) {
                        const dataStr = trimmed.slice(6);
                        if (dataStr === '[DONE]') continue;
                        
                        try {
                            const data = JSON.parse(dataStr);
                            const content = data.choices[0]?.delta?.content || "";
                            if (content) {
                                fullText += content;
                                if (onChunk) onChunk(content, fullText);
                            }
                        } catch (e) {
                            console.error("Error parsing stream data", e);
                        }
                    }
                }
            }
            return fullText;

        } catch (e) {
            console.error("AI Service Error:", e);
            throw e; 
        }
    },

    async getExplanation(context, selection, onChunk) {
        const trimmedContext = this.truncateContext(context);
        const messages = [
            {
                role: "system",
                content: `Role: 你是专业的学术科研助手，也是用户的私人阅读笔记员。
Context: 提供了论文的前文内容。
Selection: 用户高亮的文本。

Task: 针对用户的高亮文本，分别生成【AI 深度解析】和【用户标记动机推测】。

Output Structure (Markdown):

### 🤖 AI 深度解析 (Analysis)
(客观视角。基于 Context 分析这段话在论文中的地位。例如：这是一个核心假设、实验结论、还是方法创新？它回答了前文的什么问题？)

### 👤 我的标记理解 (Why I Marked This)
(第一人称视角 "我"。尝试站在用户角度，推测用户为什么觉得这段话重要。例如："我标记这段是因为它解释了模型收敛的核心原因。" 或 "这是一个非常巧妙的实验设置，值得我后续参考。")

要求:
- 使用 Markdown 三级标题 "###" 严格分隔这两个部分。
- 语言简洁、专业、有洞察力。`
            },
            {
                role: "user",
                content: `Context: """${trimmedContext}"""\n\nSelection: """${selection}"""`
            }
        ];
        return this.callAIStream(messages, onChunk);
    },

    async generateReport(context, notes, onChunk) {
        const trimmedContext = this.truncateContext(context, 30000); 
        
        // 分离高亮笔记和AI解释，构建更有结构的数据
        const notesText = notes.map((n, i) => {
            let noteStr = `[Note ${i+1}]: "${n.text}" (Page ${n.page})`;
            if (n.explanation) {
                noteStr += `\n   -> [AI Insight]: ${n.explanation}`;
            }
            return noteStr;
        }).join("\n\n");

        const messages = [
            {
                role: "system",
                content: `Role: 你是专业的学术科研助手。你正在协助用户整理一份基于这篇论文的"深度阅读记录"。

Task: 请结合全文 Context 和用户的 User Notes (用户的关注点)，生成一份阅读报告。
重点在于：**保持用户笔记的原始顺序**，并在此基础上进行深度解析。

Output Structure (Markdown):

# [论文标题]

## 🎯 核心贡献 (Executive Summary)
(基于全文，用简练的语言总结论文解决的问题、方法和核心贡献。约 100-150 字)

## 🧠 阅读解析 (Reading Notes & Analysis)
(请**严格按照用户笔记的顺序**，逐条进行罗列和解析。不要重新排序或归类。)

1.  **Page [页码] - 笔记点**
    *   **原文**: "[引用用户笔记原文片段]"
    *   **深度解读**: [结合 AI Insight 和 Context，深度说明这个点在论文中的作用、背景或创新之处。]

2.  **Page [页码] - 笔记点**
    *   ... (继续下一条)

## 💡 启发与总结 (Key Takeaways)
(基于上述阅读路径，总结这篇论文对用户的潜在启发。约 3 点。)
`
            },
            {
                role: "user",
                content: `Context: """${trimmedContext}"""\n\nUser Notes:\n${notesText}`
            }
        ];
        return this.callAIStream(messages, onChunk);
    },

    truncateContext(text, maxLength = 15000) {
        if (text.length <= maxLength) return text;
        return text.slice(text.length - maxLength);
    }
};
