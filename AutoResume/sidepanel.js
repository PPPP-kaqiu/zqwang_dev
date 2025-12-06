// Default Template
const DEFAULT_DATA = {
  name: "张三",
  email: "zhangsan@example.com",
  phone: "13800138000",
  wechat: "zhangsan_wx",
  
  school: "北京大学",
  major: "计算机科学与技术",
  degree: "本科",
  edu_start_date: "2020-09",
  edu_end_date: "2024-06",
  gpa: "3.8/4.0",
  
  company: "某互联网大厂",
  job_title: "前端开发实习生",
  exp_start_date: "2023-06",
  exp_end_date: "2023-09",
  job_desc: "1. 负责公司内部管理系统的开发与维护。\n2. 优化页面加载速度，提升用户体验。"
};

const FIELDS = [
  'name', 'email', 'phone', 'wechat',
  'school', 'major', 'degree', 'edu_start_date', 'edu_end_date', 'gpa',
  'company', 'job_title', 'exp_start_date', 'exp_end_date', 'job_desc'
];

document.addEventListener('DOMContentLoaded', () => {
  // Elements
  const saveBtn = document.getElementById('saveBtn');
  const fillBtn = document.getElementById('fillBtn');
  const resetBtn = document.getElementById('resetBtn');
  const statusMsg = document.getElementById('statusMsg');
  
  // AI Elements
  const aiFillBtn = document.getElementById('aiFillBtn');
  const toggleSettingsBtn = document.getElementById('toggleSettings');
  const settingsPanel = document.getElementById('settingsPanel');
  const closeSettingsBtn = document.getElementById('closeSettings');
  const saveSettingsBtn = document.getElementById('saveSettingsBtn');
  
  const apiKeyInput = document.getElementById('apiKey');
  const modelNameInput = document.getElementById('modelName');
  const baseUrlInput = document.getElementById('baseUrl');

  const alertBox = document.getElementById('resultAlert');
  const alertContent = document.getElementById('alertContent');
  const alertTitle = document.getElementById('alertTitle');
  const closeAlert = document.getElementById('closeAlert');

  // --- Helpers ---
  function showStatus(msg) {
    if(!statusMsg) return;
    statusMsg.textContent = msg;
    statusMsg.classList.add('show');
    setTimeout(() => {
      statusMsg.classList.remove('show');
    }, 2000);
  }

  function showAlert(result, isAI = false) {
    if(!alertBox) return;
    alertBox.classList.remove('hidden', 'success', 'warning');
    alertContent.innerHTML = '';
    
    if (isAI) {
        alertBox.classList.add('success');
        alertTitle.textContent = `AI 填写完成 (匹配 ${result.matched.length} 个字段)`;
        return;
    }

    if (result.unmatched && result.unmatched.length > 0) {
      alertBox.classList.add('warning');
      alertTitle.textContent = `有 ${result.unmatched.length} 个字段未找到`;
      const ul = document.createElement('ul');
      ul.className = 'unmatched-list';
      result.unmatched.forEach(field => {
        const li = document.createElement('li');
        li.textContent = field;
        ul.appendChild(li);
      });
      alertContent.appendChild(ul);
    } else {
      alertBox.classList.add('success');
      alertTitle.textContent = "所有字段均已匹配并尝试填写";
    }
  }

  if(closeAlert) {
      closeAlert.addEventListener('click', () => {
        alertBox.classList.add('hidden');
      });
  }

  // --- Settings Logic ---
  chrome.storage.local.get(['aiSettings'], (result) => {
      if (result.aiSettings) {
          if(apiKeyInput) apiKeyInput.value = result.aiSettings.api_key || '';
          if(modelNameInput) modelNameInput.value = result.aiSettings.model || 'deepseek-chat';
          if(baseUrlInput) baseUrlInput.value = result.aiSettings.base_url || '';
      }
  });

  if(toggleSettingsBtn) {
      toggleSettingsBtn.addEventListener('click', () => {
          settingsPanel.classList.toggle('hidden');
      });
  }

  if(closeSettingsBtn) {
      closeSettingsBtn.addEventListener('click', () => {
          settingsPanel.classList.add('hidden');
      });
  }

  if(saveSettingsBtn) {
      saveSettingsBtn.addEventListener('click', () => {
          const settings = {
              api_key: apiKeyInput.value.trim(),
              model: modelNameInput.value.trim(),
              base_url: baseUrlInput.value.trim()
          };
          chrome.storage.local.set({ aiSettings: settings }, () => {
              showStatus('设置已保存');
              settingsPanel.classList.add('hidden');
          });
      });
  }

  // --- Data Logic ---
  chrome.storage.local.get(['resumeDataFlat'], (result) => {
    const data = result.resumeDataFlat || DEFAULT_DATA;
    FIELDS.forEach(id => {
      const el = document.getElementById(id);
      if (el && data[id]) {
        el.value = data[id];
      }
    });
  });

  function collectData() {
    const data = {};
    FIELDS.forEach(id => {
      const el = document.getElementById(id);
      if (el) data[id] = el.value;
    });
    return data;
  }

  function structureData(data) {
    return {
      basic_info: {
        name: data.name,
        email: data.email,
        phone: data.phone,
        wechat: data.wechat
      },
      education: [{
        school: data.school,
        major: data.major,
        degree: data.degree,
        start_date: data.edu_start_date,
        end_date: data.edu_end_date,
        gpa: data.gpa
      }],
      experience: [{
        company: data.company,
        job_title: data.job_title,
        start_date: data.exp_start_date,
        end_date: data.exp_end_date,
        job_desc: data.job_desc
      }]
    };
  }

  function performSave(showToast = true) {
    return new Promise((resolve) => {
        const data = collectData();
        const structuredData = structureData(data);
        chrome.storage.local.set({ 
            resumeDataFlat: data,
            resumeData: structuredData
        }, () => {
            if (showToast) showStatus('已保存');
            resolve(structuredData);
        });
    });
  }

  if(saveBtn) {
      saveBtn.addEventListener('click', () => {
        performSave(true);
      });
  }

  if(resetBtn) {
      resetBtn.addEventListener('click', () => {
        if (confirm('确定要重置所有内容吗？')) {
          FIELDS.forEach(id => {
            const el = document.getElementById(id);
            if (el && DEFAULT_DATA[id]) {
              el.value = DEFAULT_DATA[id];
            }
          });
          showStatus('已重置');
        }
      });
  }

  // --- Fill Logic ---
  
  // 1. Regular Fill
  if(fillBtn) {
      fillBtn.addEventListener('click', async () => {
        if(alertBox) alertBox.classList.add('hidden'); 
        const structuredData = await performSave(false);
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        if (!tab) return;

        try {
          showStatus('正在填写...');
          
          // Inject Content Script if needed (using Ping-Pong)
          const pingContentScript = async () => {
              try {
                  const pong = await chrome.tabs.sendMessage(tab.id, { action: "ping" });
                  return pong && pong.success;
              } catch (e) {
                  return false;
              }
          };

          let isAlive = await pingContentScript();
          if (!isAlive) {
               try {
                  await chrome.scripting.executeScript({
                      target: { tabId: tab.id },
                      files: ['content.js']
                  });
                  await new Promise(r => setTimeout(r, 200));
               } catch (e) {
                   console.error(e);
               }
          }

          const response = await chrome.tabs.sendMessage(tab.id, { 
            action: "fill_resume", 
            data: structuredData 
          });
          if (response) showAlert(response);
        } catch (err) {
          console.error(err);
          showStatus('连接失败，请刷新网页重试');
        }
      });
  }

  // 2. AI Fill
  if(aiFillBtn) {
      aiFillBtn.addEventListener('click', async () => {
          if(alertBox) alertBox.classList.add('hidden');
          const aiLogPanel = document.getElementById('aiLogPanel');
          const aiLogContent = document.getElementById('aiLogContent');
          if(aiLogPanel) aiLogPanel.classList.remove('hidden');
          if(aiLogContent) aiLogContent.textContent = "正在连接 AI...\n";
          
          const structuredData = await performSave(false);
          
          // Get AI Settings
          const settingsResult = await chrome.storage.local.get(['aiSettings']);
          const settings = settingsResult.aiSettings;

          if (!settings || !settings.api_key) {
              if(settingsPanel) settingsPanel.classList.remove('hidden');
              showStatus('请先配置 API Key');
              return;
          }

          const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
          if (!tab) return;

          try {
              showStatus('AI 正在分析页面...');
              if(aiLogContent) aiLogContent.textContent += "正在扫描网页表单结构...\n";
              
              // Step 1: Extract Context with PING-PONG Mechanism
              let contextResponse;
              
              // Helper to check connection
              const pingContentScript = async () => {
                  try {
                      const pong = await chrome.tabs.sendMessage(tab.id, { action: "ping" });
                      return pong && pong.success;
                  } catch (e) {
                      return false;
                  }
              };

              // Try to ping first
              let isAlive = await pingContentScript();
              
              if (!isAlive) {
                   console.log("Content script dead, injecting...");
                   try {
                      await chrome.scripting.executeScript({
                          target: { tabId: tab.id },
                          files: ['content.js']
                      });
                      // Short wait for init
                      await new Promise(r => setTimeout(r, 200));
                   } catch (injectErr) {
                       throw new Error("无法注入脚本，请刷新网页: " + injectErr.message);
                   }
              }
              
              // Now try to extract context
              try {
                   contextResponse = await chrome.tabs.sendMessage(tab.id, { action: "extract_context" });
              } catch (e) {
                   throw new Error("通信失败，请刷新网页重试");
              }

              if (!contextResponse || !contextResponse.success) {
                  throw new Error("无法提取页面信息");
              }
              
              if(aiLogContent) aiLogContent.textContent += `找到 ${contextResponse.context.length} 个输入框。\n正在发送给 AI 模型...\n\n`;

              // Step 2: Call AI with Streaming
              showStatus('AI 正在思考...');
              
              const mapping = await AIService.analyzeAndMapStream(
                  contextResponse.context, 
                  structuredData, 
                  settings,
                  (chunk, fullText) => {
                      // Update Log UI in real-time
                      if(aiLogContent) aiLogContent.textContent = `找到 ${contextResponse.context.length} 个输入框。\n正在发送给 AI 模型...\n\n` + fullText;
                      // Auto scroll to bottom
                      if(aiLogPanel) aiLogPanel.scrollTop = aiLogPanel.scrollHeight;
                  }
              );
              
              if(aiLogContent) aiLogContent.textContent += "\n\n[完成] 解析成功，准备填写...";

              // Step 3: Apply Mapping
              showStatus('AI 正在填写...');
              const fillResponse = await chrome.tabs.sendMessage(tab.id, {
                  action: "fill_by_ai",
                  mapping: mapping
              });

              if (fillResponse) showAlert(fillResponse, true);

          } catch (err) {
              console.error("AI Fill Error:", err);
              showStatus('AI 填写失败');
              if(aiLogContent) aiLogContent.textContent += `\n\n[错误] ${err.message}`;
              if(alertBox) alertBox.classList.remove('hidden', 'success', 'warning');
              if(alertBox) alertBox.classList.add('warning');
              if(alertTitle) alertTitle.textContent = "AI 出错";
              if(alertContent) alertContent.textContent = err.message;
          }
      });
  }
});