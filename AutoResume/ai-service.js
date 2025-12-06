// AI Service for AutoResume
const AIService = {
    async callAIStream(messages, settings, onChunk) {
        if (!settings.api_key) {
            throw new Error("请先在设置中配置 API Key。");
        }

        const modelName = settings.model || "deepseek-chat";
        let baseUrl = settings.base_url;
        const isDeepSeek = modelName.toLowerCase().includes("deepseek");
        if (!baseUrl) {
             baseUrl = isDeepSeek ? 'https://api.deepseek.com' : 'https://api.openai.com/v1';
        }
        const endpoint = `${baseUrl}/chat/completions`;

        try {
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${settings.api_key}`
                },
                body: JSON.stringify({
                    model: modelName,
                    messages: messages,
                    stream: true,
                    // Note: response_format json_object is not always supported in stream mode for all providers
                    // We will rely on raw text parsing or simple JSON extraction
                })
            });

            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error?.message || "API 请求失败");
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder("utf-8");
            let buffer = "";
            let fullText = "";

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                
                const chunk = decoder.decode(value, { stream: true });
                buffer += chunk;
                
                const lines = buffer.split('\n');
                buffer = lines.pop(); 

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
                            // ignore partial json errors
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

    async analyzeAndMapStream(formContext, resumeData, settings, onPartialResult) {
        const messages = [
            {
                role: "system",
                content: `Role: Intelligent Form Filler.
Task: Map resume data to form fields.
Output: Streaming JSON. 
Format: Return a SINGLE JSON object. 
Keys = elementId, Values = text to fill.

Resume: ${JSON.stringify(resumeData)}

Fields: ${JSON.stringify(formContext)}

Constraint:
- Output valid JSON only.
- Do not output markdown code blocks. Just the raw JSON object string.
`
            },
            { role: "user", content: "Start mapping." }
        ];

        // Buffer to accumulate the full JSON string
        let fullAccumulatedText = "";
        
        await this.callAIStream(messages, settings, (chunk, fullText) => {
            fullAccumulatedText = fullText;
            
            // OPTIONAL: Basic heuristic to detect new key-value pairs if you want real-time field filling
            // For stability, we might just wait for full JSON or try to parse partial JSON if possible.
            // Since JSON streaming is tricky to parse partially, we will just stream the text to the UI log first,
            // and parse the final result. 
            // OR: We can use a partial JSON parser library, but adding deps is hard here.
            
            // Let's pass the raw text to UI for visualization
            onPartialResult(chunk, fullText);
        });

        // Try to parse the final result
        try {
            // Remove markdown code blocks if AI added them
            let cleanJson = fullAccumulatedText.replace(/```json/g, '').replace(/```/g, '').trim();
            return JSON.parse(cleanJson);
        } catch (e) {
            console.error("Failed to parse final JSON", e);
            throw new Error("AI 返回格式错误，无法解析");
        }
    }
};
