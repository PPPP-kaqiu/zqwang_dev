chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === "fill_resume") {
    console.log("[AutoResume] Received data, starting smart fill...");
    const result = fillResume(request.data);
    // Send back the result (matched/unmatched)
    sendResponse(result);
  }
  return true; // Keep the message channel open for sendResponse
});

/**
 * Smart Resume Filler using Score-based Matching
 */

const FIELD_CONFIGS = {
  // --- Basic Info ---
  name: {
    label_cn: "姓名",
    id: /name|xm|xingming|truename/i,
    label: /^(姓名|名字|Name|Full Name)$/i,
    placeholder: /姓名|Name/i,
    exclude: /公司|奖|Company|Project|School/i
  },
  email: {
    label_cn: "邮箱",
    id: /email|mail|youxiang/i,
    label: /邮箱|Email|E-mail/i,
    placeholder: /邮箱|Email/i
  },
  phone: {
    label_cn: "电话",
    id: /phone|mobile|tel|shouji/i,
    label: /手机|电话|Mobile|Phone|Cell/i,
    placeholder: /手机|电话|Mobile/i
  },
  wechat: {
    label_cn: "微信",
    id: /wechat|weixin|wx/i,
    label: /微信|WeChat/i,
    placeholder: /微信|WeChat/i
  },

  // --- Education ---
  school: {
    label_cn: "学校",
    id: /school|university|college|daxue/i,
    label: /学校|毕业院校|School|University/i,
    placeholder: /学校|School/i
  },
  major: {
    label_cn: "专业",
    id: /major|zhuanye|subject/i,
    label: /专业|Major|Subject/i,
    placeholder: /专业|Major/i
  },
  degree: {
    label_cn: "学历",
    id: /degree|xueli|education/i,
    label: /学历|学位|Degree|Education Level/i,
    placeholder: /学历|Degree/i
  },
  gpa: {
    label_cn: "GPA",
    id: /gpa|score|grade|cj|chengji/i,
    label: /GPA|绩点|成绩|Grade/i,
    placeholder: /GPA|绩点/i
  },
  edu_start_date: {
    label_cn: "入学时间",
    id: /start.*date|ruxue/i,
    label: /入学|Start Date/i,
    placeholder: /入学|Start/i
  },
  edu_end_date: {
    label_cn: "毕业时间",
    id: /end.*date|biye/i,
    label: /毕业|End Date|Graduation/i,
    placeholder: /毕业|End/i
  },

  // --- Experience ---
  company: {
    label_cn: "公司",
    id: /company|firm|qiye/i,
    label: /公司|Company|Employer/i,
    placeholder: /公司|Company/i
  },
  job_title: {
    label_cn: "职位",
    id: /title|position|zhiwei|job/i,
    label: /职位|头衔|Job Title|Position/i,
    placeholder: /职位|Position/i
  },
  exp_start_date: {
    label_cn: "工作开始时间",
    id: /start|begin/i,
    label: /开始|Start/i,
    placeholder: /开始|Start/i
  },
  exp_end_date: {
    label_cn: "工作结束时间",
    id: /end|finish/i,
    label: /结束|End/i,
    placeholder: /结束|End/i
  },
  job_desc: {
    label_cn: "工作描述",
    id: /desc|duty|zhize|neirong/i,
    label: /描述|职责|Description|Responsibility/i,
    placeholder: /描述|Description/i
  }
};

function fillResume(data) {
  const flatData = flattenData(data);
  const inputs = Array.from(document.querySelectorAll('input, textarea, select'));
  
  const inputLabels = new Map();
  inputs.forEach(input => {
    inputLabels.set(input, getLabelText(input));
  });

  const filledInputs = new Set();
  
  // Track results
  const matchedFields = [];
  const unmatchedFields = [];

  for (const [fieldKey, config] of Object.entries(FIELD_CONFIGS)) {
    const value = flatData[fieldKey];
    
    // Only consider fields that the user actually provided data for
    if (!value) continue;

    let bestInput = null;
    let maxScore = 0;

    for (const input of inputs) {
      if (filledInputs.has(input)) continue;
      if (input.type === 'hidden' || input.disabled) continue;

      const score = calculateScore(input, config, inputLabels.get(input));
      
      if (score > maxScore && score > 10) {
        maxScore = score;
        bestInput = input;
      }
    }

    if (bestInput) {
      fillInput(bestInput, value);
      filledInputs.add(bestInput);
      matchedFields.push(config.label_cn || fieldKey);
    } else {
      unmatchedFields.push(config.label_cn || fieldKey);
    }
  }
  
  return {
    success: true,
    matched: matchedFields,
    unmatched: unmatchedFields
  };
}

function calculateScore(input, config, labelText) {
  let score = 0;
  const nameId = (input.id + " " + input.name).toLowerCase();
  const placeholder = (input.placeholder || "").toLowerCase();
  const label = labelText.toLowerCase();

  // 1. Negative Check
  if (config.exclude && (config.exclude.test(nameId) || config.exclude.test(label))) {
    return 0;
  }

  // 2. ID/Name Match
  if (config.id && config.id.test(nameId)) {
    score += 50;
  }

  // 3. Label Match
  if (config.label && config.label.test(label)) {
    score += 40;
  }

  // 4. Placeholder Match
  if (config.placeholder && config.placeholder.test(placeholder)) {
    score += 20;
  }

  return score;
}

function getLabelText(input) {
  const texts = [];
  if (input.id) {
    const label = document.querySelector(`label[for="${input.id}"]`);
    if (label) texts.push(label.innerText);
  }

  let parent = input.parentElement;
  while (parent) {
    if (parent.tagName === 'LABEL') {
      texts.push(parent.innerText);
      break;
    }
    if (parent.tagName === 'FORM' || parent === document.body) break;
    parent = parent.parentElement;
  }

  let container = input.parentElement;
  if (container) {
    const siblingText = container.innerText;
    texts.push(siblingText);
    if (container.parentElement) {
       const parentText = container.parentElement.innerText;
       if (parentText.length < 200) {
         texts.push(parentText);
       }
    }
  }

  return texts.join(" ").trim();
}

function flattenData(data) {
  const result = {};
  if (data.basic_info) Object.assign(result, data.basic_info);
  if (data.education && data.education.length) {
    const edu = data.education[0];
    result.school = edu.school;
    result.major = edu.major;
    result.degree = edu.degree;
    result.gpa = edu.gpa;
    result.edu_start_date = edu.start_date;
    result.edu_end_date = edu.end_date;
  }
  if (data.experience && data.experience.length) {
    const exp = data.experience[0];
    result.company = exp.company;
    result.job_title = exp.job_title;
    result.job_desc = exp.job_desc;
    result.exp_start_date = exp.start_date;
    result.exp_end_date = exp.end_date;
  }
  return result;
}

function fillInput(input, value) {
  if (!input) return;
  input.focus();
  input.value = value;
  ['input', 'change', 'blur'].forEach(eventType => {
    input.dispatchEvent(new Event(eventType, { bubbles: true }));
  });
}