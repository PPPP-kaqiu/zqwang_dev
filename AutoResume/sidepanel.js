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

// Input IDs mapping
const FIELDS = [
  'name', 'email', 'phone', 'wechat',
  'school', 'major', 'degree', 'edu_start_date', 'edu_end_date', 'gpa',
  'company', 'job_title', 'exp_start_date', 'exp_end_date', 'job_desc'
];

document.addEventListener('DOMContentLoaded', () => {
  const saveBtn = document.getElementById('saveBtn');
  const fillBtn = document.getElementById('fillBtn');
  const resetBtn = document.getElementById('resetBtn');
  const statusMsg = document.getElementById('statusMsg');
  
  const alertBox = document.getElementById('resultAlert');
  const alertContent = document.getElementById('alertContent');
  const alertTitle = document.getElementById('alertTitle');
  const closeAlert = document.getElementById('closeAlert');

  function showStatus(msg) {
    statusMsg.textContent = msg;
    statusMsg.classList.add('show');
    setTimeout(() => {
      statusMsg.classList.remove('show');
    }, 2000);
  }

  function showAlert(result) {
    alertBox.classList.remove('hidden', 'success', 'warning');
    alertContent.innerHTML = '';
    
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

  closeAlert.addEventListener('click', () => {
    alertBox.classList.add('hidden');
  });

  // Load Data
  chrome.storage.local.get(['resumeDataFlat'], (result) => {
    const data = result.resumeDataFlat || DEFAULT_DATA;
    FIELDS.forEach(id => {
      const el = document.getElementById(id);
      if (el && data[id]) {
        el.value = data[id];
      }
    });
  });

  // Save Data
  saveBtn.addEventListener('click', () => {
    const data = {};
    FIELDS.forEach(id => {
      const el = document.getElementById(id);
      if (el) data[id] = el.value;
    });

    chrome.storage.local.set({ resumeDataFlat: data });

    const structuredData = {
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

    chrome.storage.local.set({ resumeData: structuredData }, () => {
      showStatus('已保存');
    });
  });

  // Reset Data
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

  // Fill Data
  fillBtn.addEventListener('click', async () => {
    saveBtn.click();
    alertBox.classList.add('hidden'); // Hide previous alert
    
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab) return;
    
    const data = {};
    FIELDS.forEach(id => {
      const el = document.getElementById(id);
      if (el) data[id] = el.value;
    });

    const structuredData = {
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

    try {
      showStatus('正在填写...');
      const response = await chrome.tabs.sendMessage(tab.id, { 
        action: "fill_resume", 
        data: structuredData 
      });
      
      if (response) {
        showAlert(response);
      }
    } catch (err) {
      console.error(err);
      showStatus('连接超时或失败，请刷新页面');
    }
  });
});