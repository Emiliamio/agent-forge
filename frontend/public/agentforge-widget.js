/**
 * AgentForge 商业级嵌入式 Web Component 智能体挂件
 * 支持 Shadow DOM 样式绝对隔离、自适应抽屉展开与实时对话
 */
(function () {
  const scriptTag = document.currentScript;
  const apiUrl = (scriptTag && scriptTag.getAttribute('data-api-url')) || 'http://localhost:8080/api';
  const appId = (scriptTag && scriptTag.getAttribute('data-app-id')) || '1';
  const botTitle = (scriptTag && scriptTag.getAttribute('data-title')) || 'AgentForge 智能顾问';
  const primaryColor = (scriptTag && scriptTag.getAttribute('data-primary-color')) || '#4f46e5';

  const host = document.createElement('div');
  host.id = 'agentforge-widget-container';
  document.body.appendChild(host);

  const shadowRoot = host.attachShadow({ mode: 'open' });
  shadowRoot.innerHTML = `
    <style>
      :host {
        --primary: ${primaryColor};
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      }
      .af-floating-btn {
        position: fixed; bottom: 24px; right: 24px; width: 58px; height: 58px;
        border-radius: 50%; background: var(--primary); color: #fff;
        box-shadow: 0 8px 24px rgba(79, 70, 229, 0.35); display: flex; align-items: center;
        justify-content: center; cursor: pointer; z-index: 2147483647; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        font-size: 26px; border: none; outline: none;
      }
      .af-floating-btn:hover { transform: scale(1.08); box-shadow: 0 12px 28px rgba(79, 70, 229, 0.45); }
      .af-chat-window {
        position: fixed; bottom: 96px; right: 24px; width: 390px; height: 580px; max-height: calc(100vh - 120px);
        background: #181825; border: 1px solid #313244; border-radius: 20px;
        box-shadow: 0 16px 40px rgba(0, 0, 0, 0.45); display: none; flex-direction: column;
        overflow: hidden; z-index: 2147483647; animation: slideUp 0.3s ease-out;
      }
      @keyframes slideUp { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
      .af-header { background: #1e1e2e; padding: 16px 20px; color: #cdd6f4; font-weight: 600; border-bottom: 1px solid #313244; display: flex; justify-content: space-between; align-items: center; }
      .af-header-title { display: flex; align-items: center; gap: 8px; font-size: 15px; }
      .af-header-dot { width: 8px; height: 8px; border-radius: 50%; background: #10b981; }
      .af-close-btn { cursor: pointer; background: transparent; border: none; color: #a6adc8; font-size: 18px; line-height: 1; padding: 4px; border-radius: 6px; }
      .af-close-btn:hover { background: #313244; color: #fff; }
      .af-messages { flex: 1; padding: 18px; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; background: #11111b; }
      .af-msg { max-width: 82%; padding: 11px 15px; border-radius: 14px; font-size: 14px; line-height: 1.55; word-break: break-word; }
      .af-msg-user { align-self: flex-end; background: var(--primary); color: #ffffff; border-bottom-right-radius: 4px; }
      .af-msg-bot { align-self: flex-start; background: #1e1e2e; color: #cdd6f4; border: 1px solid #313244; border-bottom-left-radius: 4px; }
      .af-input-area { padding: 14px; background: #181825; border-top: 1px solid #313244; display: flex; gap: 10px; align-items: center; }
      .af-input { flex: 1; background: #1e1e2e; border: 1px solid #313244; border-radius: 10px; padding: 10px 14px; color: #cdd6f4; outline: none; font-size: 14px; }
      .af-input:focus { border-color: var(--primary); }
      .af-send-btn { background: var(--primary); border: none; color: #fff; border-radius: 10px; padding: 10px 18px; font-size: 14px; font-weight: 500; cursor: pointer; transition: opacity 0.2s; }
      .af-send-btn:hover { opacity: 0.9; }
      @media (max-width: 640px) {
        .af-chat-window { bottom: 0; right: 0; width: 100vw; height: 100vh; max-height: 100vh; border-radius: 0; }
        .af-floating-btn { bottom: 16px; right: 16px; }
      }
    </style>

    <button class="af-floating-btn" id="af-btn" title="点击展开智能助理">🤖</button>
    <div class="af-chat-window" id="af-window">
      <div class="af-header">
        <div class="af-header-title">
          <div class="af-header-dot"></div>
          <span>${botTitle}</span>
        </div>
        <button class="af-close-btn" id="af-close">✕</button>
      </div>
      <div class="af-messages" id="af-msg-box">
        <div class="af-msg af-msg-bot">您好！我是您的专属企业级 AI 助理。请问有什么可以帮助您？</div>
      </div>
      <div class="af-input-area">
        <input type="text" class="af-input" id="af-input-field" placeholder="请输入您的问题..." />
        <button class="af-send-btn" id="af-send-action">发送</button>
      </div>
    </div>
  `;

  const btn = shadowRoot.getElementById('af-btn');
  const win = shadowRoot.getElementById('af-window');
  const close = shadowRoot.getElementById('af-close');
  const msgBox = shadowRoot.getElementById('af-msg-box');
  const inputField = shadowRoot.getElementById('af-input-field');
  const sendBtn = shadowRoot.getElementById('af-send-action');

  btn.onclick = () => { win.style.display = win.style.display === 'flex' ? 'none' : 'flex'; };
  close.onclick = () => { win.style.display = 'none'; };

  function appendMessage(role, text) {
    const div = document.createElement('div');
    div.className = `af-msg ${role === 'user' ? 'af-msg-user' : 'af-msg-bot'}`;
    div.textContent = text;
    msgBox.appendChild(div);
    msgBox.scrollTop = msgBox.scrollHeight;
  }

  async function handleSend() {
    const text = inputField.value.trim();
    if (!text) return;
    inputField.value = '';
    appendMessage('user', text);

    try {
      const response = await fetch(`${apiUrl}/agents/${appId}/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text })
      });
      const resData = await response.json();
      if (resData.code === 200 && resData.data) {
        appendMessage('bot', resData.data.finalAnswer || '回答已生成');
      } else {
        appendMessage('bot', '服务响应异常: ' + (resData.message || '未知错误'));
      }
    } catch (e) {
      appendMessage('bot', '网络连接出现异常，请稍后重试。');
    }
  }

  sendBtn.onclick = handleSend;
  inputField.onkeypress = (e) => { if (e.key === 'Enter') handleSend(); };
})();
