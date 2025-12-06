// Prevent re-declaration errors when re-injecting script
if (!window.hasAutoResumeInjected) {
  window.hasAutoResumeInjected = true;

  console.log("[AutoResume] Content Script Loaded/Reloaded");

  chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    // 1. PING (Health Check)
    if (request.action === "ping") {
        sendResponse({ success: true, status: "alive" });
        return true;
    }

    // 2. REGULAR FILL
    if (request.action === "fill_resume") {
      try {
          const result = fillResume(request.data);
          sendResponse(result);
      } catch (e) {
          console.error(e);
          sendResponse({ success: false, error: e.message });
      }
      return true;
    }

    // 3. EXTRACT CONTEXT (AI)
    if (request.action === "extract_context") {
      try {
          console.log("[AutoResume] Extracting context for AI...");
          const context = extractFormContext();
          sendResponse({ success: true, context: context });
      } catch (e) {
          console.error("[AutoResume] Extract Context Error:", e);
          sendResponse({ success: false, error: e.message });
      }
      return true;
    }

    // 4. FILL BY AI
    if (request.action === "fill_by_ai") {
        try {
            console.log("[AutoResume] AI Fill Request Received");
            const result = fillByAI(request.mapping);
            console.log("[AutoResume] AI Fill Result:", result);
            sendResponse(result);
        } catch (e) {
            console.error("[AutoResume] AI Fill Error:", e);
            sendResponse({ success: false, error: e.message });
        }
        return true;
    }
  });

  // --- Constants ---
  const FIELD_MATCHERS = {
    name: { keywords: ['姓名', '名字', 'Name'], exclude: ['公司', '奖'] },
    email: { keywords: ['邮箱', 'Email', '邮件'] },
    phone: { keywords: ['手机', '电话', 'Mobile', 'Phone', '联系方式'] },
    wechat: { keywords: ['微信', 'WeChat'] },
    
    school: { keywords: ['学校', 'School', 'University', '院校'] },
    major: { keywords: ['专业', 'Major'] },
    degree: { keywords: ['学历', '学位', 'Degree', 'Education'] },
    gpa: { keywords: ['GPA', '绩点', '成绩'] },
    edu_start_date: { keywords: ['入学', 'Start Date', '开始'] },
    edu_end_date: { keywords: ['毕业', 'End Date', '结束'] },

    company: { keywords: ['公司', 'Company', '单位'] },
    job_title: { keywords: ['职位', 'Title', 'Position', '岗位'] },
    exp_start_date: { keywords: ['入职', 'Start Date', '开始'] },
    exp_end_date: { keywords: ['离职', 'End Date', '结束'] },
    job_desc: { keywords: ['描述', '职责', 'Description', '内容', '经历', 'Work Content'], isTextarea: true }
  };

  // --- Main Logic Functions ---

  function fillResume(data) {
    const flatData = flattenData(data);
    const matchedFields = [];
    const unmatchedFields = [];

    const candidates = Array.from(document.querySelectorAll('label, span, div, p, h3, h4, h5'));
    
    const labelElements = candidates.filter(el => {
        const len = el.innerText.trim().length;
        return len > 0 && len < 40; 
    });

    for (const [key, config] of Object.entries(FIELD_MATCHERS)) {
      const value = flatData[key];
      if (!value) continue;

      let foundInput = null;

      foundInput = findInputByIdOrName(key, config.isTextarea);

      if (!foundInput) {
          foundInput = findInputByLabelText(labelElements, config, key);
      }

      if (!foundInput) {
          foundInput = findInputByPlaceholder(config.keywords, config.isTextarea);
      }

      if (!foundInput && config.isTextarea) {
          foundInput = document.querySelector('textarea:not([disabled])');
          const allTextareas = document.querySelectorAll('textarea:not([disabled])');
          if (allTextareas.length > 1) foundInput = null; 
      }

      if (foundInput) {
        fillInput(foundInput, value);
        matchedFields.push(config.keywords[0]);
      } else {
        unmatchedFields.push(config.keywords[0]);
      }
    }

    return { success: true, matched: matchedFields, unmatched: unmatchedFields };
  }

  function findInputByIdOrName(key, isTextarea) {
      const tag = isTextarea ? 'textarea' : 'input';
      const selectors = [
          `${tag}[name*="${key}" i]`,
          `${tag}[id*="${key}" i]`,
          `input[name*="${key}" i]` 
      ];
      return document.querySelector(selectors.join(','));
  }

  function findInputByLabelText(elements, config, key) {
      for (const element of elements) {
          const text = element.innerText.trim();
          const isMatch = config.keywords.some(k => text.includes(k));
          if (!isMatch) continue;
          if (config.exclude && config.exclude.some(ex => text.includes(ex))) continue;

          if (config.isTextarea) {
              let input = element.parentElement ? element.parentElement.querySelector('textarea') : null;
              if (isValidInput(input)) return input;
              
              let richText = element.parentElement ? element.parentElement.querySelector('div[contenteditable="true"]') : null;
              if (richText) return richText;

              if (element.parentElement && element.parentElement.parentElement) {
                  const grandpa = element.parentElement.parentElement;
                  const uncleTextarea = grandpa.querySelector('textarea');
                  if (isValidInput(uncleTextarea)) return uncleTextarea;
                  
                  const uncleRich = grandpa.querySelector('div[contenteditable="true"]');
                  if (uncleRich) return uncleRich;
              }
          } 
          
          let input = element.querySelector('input, textarea, select');
          if (isValidInput(input)) return input;
          
          if (element.parentElement) {
              input = element.parentElement.querySelector('input:not([type="hidden"]), textarea, select');
              if (isValidInput(input) && input !== element) return input;
          }
      }
      return null;
  }

  function findInputByPlaceholder(keywords, isTextarea) {
      for (const keyword of keywords) {
          const selector = isTextarea 
              ? `textarea[placeholder*="${keyword}"], div[contenteditable][placeholder*="${keyword}"]`
              : `input[placeholder*="${keyword}"]`;
          const input = document.querySelector(selector);
          if (isValidInput(input)) return input;
      }
      return null;
  }

  function isValidInput(input) {
      if (!input) return false;
      return !input.disabled && input.style.display !== 'none';
  }

  function flattenData(data) {
      const result = {};
      if (data.basic_info) Object.assign(result, data.basic_info);
      if (data.education && data.education.length) Object.assign(result, data.education[0]);
      if (data.experience && data.experience.length) Object.assign(result, data.experience[0]);
      return result;
  }

  function fillInput(input, value) {
    if (!input) return;
    // We do NOT call input.focus() to avoid scrolling/popups
    
    // 1. ContentEditable (Rich Text)
    if (input.tagName === 'DIV' && input.isContentEditable) {
        input.innerText = value;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('blur', { bubbles: true }));
        return;
    }

    // 2. Select Elements (Smart Option Matching)
    if (input.tagName === 'SELECT') {
        let matched = false;
        // Try exact value match first
        try {
            input.value = value;
            if (input.value === value) matched = true;
        } catch(e) {}

        // If failed, try matching option text
        if (!matched) {
            const options = Array.from(input.options);
            const lowerValue = String(value).toLowerCase();
            // Exact text match
            let bestOption = options.find(opt => opt.text.trim() === value);
            // Partial text match (fuzzy)
            if (!bestOption) {
                bestOption = options.find(opt => opt.text.toLowerCase().includes(lowerValue));
            }
            
            if (bestOption) {
                input.value = bestOption.value;
                matched = true;
            }
        }
        
        // Dispatch events for Select
        input.dispatchEvent(new Event('change', { bubbles: true }));
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('blur', { bubbles: true }));
        return;
    }

    // 3. Input / Textarea (React-aware setter)
    let nativeSetter;
    try {
        // Safer way to get prototype
        const proto = Object.getPrototypeOf(input);
        nativeSetter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
    } catch(e) {
        console.warn("[AutoResume] Prototype lookup failed", e);
    }

    if (nativeSetter) {
        nativeSetter.call(input, value);
    } else {
        input.value = value;
    }
    
    // Dispatch critical events
    // NOTE: 'input' and 'change' are crucial for React/Vue
    const eventOptions = { bubbles: true, composed: true };
    
    input.dispatchEvent(new Event('input', eventOptions));
    input.dispatchEvent(new Event('change', eventOptions));
    input.dispatchEvent(new Event('blur', eventOptions));
  }

  function extractFormContext() {
      const inputs = Array.from(document.querySelectorAll('input:not([type="hidden"]), textarea, select'));
      
      const visibleInputs = inputs.filter(input => {
          const rect = input.getBoundingClientRect();
          // Relaxed visibility check: just needs to be in DOM and not hidden style
          return input.style.display !== 'none' && input.style.visibility !== 'hidden';
      }).slice(0, 60); // Increased limit slightly

      return visibleInputs.map((input, index) => {
          const tempId = input.id || `ai_temp_${index}_${Math.random().toString(36).substr(2, 5)}`;
          if (!input.id) input.setAttribute('data-ai-id', tempId);
          
          return {
              elementId: input.id || input.getAttribute('data-ai-id'),
              type: input.type || input.tagName.toLowerCase(),
              name: input.name || '',
              placeholder: input.placeholder || '',
              label: getNearbyLabel(input),
              isTextarea: input.tagName === 'TEXTAREA'
          };
      });
  }

  function getNearbyLabel(input) {
      let label = "";
      if (input.id) {
          const l = document.querySelector(`label[for="${input.id}"]`);
          if (l) label = l.innerText;
      }
      if (!label && input.parentElement) {
          // Clone to avoid modifying DOM
          const parentClone = input.parentElement.cloneNode(true);
          // Remove inputs from clone text
          Array.from(parentClone.querySelectorAll('input, select, textarea')).forEach(el => el.remove());
          label = parentClone.innerText.slice(0, 100); 
      }
      return label ? label.trim().replace(/\s+/g, ' ') : "";
  }

  function fillByAI(mapping) {
      const matched = [];
      const unmatched = [];
      
      console.log("[AutoResume] Applying AI Mapping:", mapping);

      for (const [elementId, value] of Object.entries(mapping)) {
          // First try standard ID
          let input = document.getElementById(elementId);
          
          // Second try data-ai-id
          if (!input) {
              try {
                input = document.querySelector(`[data-ai-id="${elementId}"]`);
              } catch(e) {
                console.warn("Invalid selector for data-ai-id", elementId);
              }
          }
          
          // Fallback: Name
          if (!input) {
              input = document.querySelector(`[name="${elementId}"]`);
          }

          if (input) {
              console.log(`[AutoResume] AI Filling ${elementId} with value: "${String(value).substring(0, 20)}..."`);
              
              // Only scroll if really needed (e.g. far off screen) to avoid jumping
              // input.scrollIntoView({ behavior: 'smooth', block: 'center' });
              
              // Fill
              fillInput(input, String(value));
              
              matched.push(elementId);
          } else {
              console.warn(`[AutoResume] AI failed to find element ${elementId}`);
              unmatched.push(elementId);
          }
      }
      
      return { success: true, matched, unmatched };
  }
}
