/**
 * AgentForge 智能客服 Web Component 嵌入挂件
 * 基于 Shadow DOM 隔离宿主样式，开箱即用无样式污染
 */

function buildWidgetHeaders(options = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (options && options.publicToken) {
    headers['X-Public-Token'] = options.publicToken
    headers['X-AgentForge-Public-Token'] = options.publicToken
  }
  if (options && options.tenantId !== undefined && options.tenantId !== null && options.tenantId !== '') {
    headers['X-Tenant-Id'] = String(options.tenantId)
  }
  return headers
}

(function () {
  if (typeof document === 'undefined' || !document.body) {
    return
  }

  const scriptTag = document.currentScript
  const apiUrl = (scriptTag && scriptTag.getAttribute('data-api-url')) || 'http://localhost:8080/api'
  const appId = (scriptTag && scriptTag.getAttribute('data-app-id')) || '1'
  const publicToken = (scriptTag && (scriptTag.getAttribute('data-token') || scriptTag.getAttribute('data-public-token'))) || ''
  const tenantId = (scriptTag && scriptTag.getAttribute('data-tenant-id')) || ''
  const botTitle = (scriptTag && scriptTag.getAttribute('data-title')) || 'AgentForge 智能助理'
  const primaryColor = (scriptTag && scriptTag.getAttribute('data-primary-color')) || '#4f46e5'

  const host = document.createElement('div')
  host.id = 'agentforge-widget-container'
  document.body.appendChild(host)

  const shadowRoot = host.attachShadow({ mode: 'open' })
  shadowRoot.innerHTML = `
    <style>
      .af-widget-btn {
        position: fixed;
        bottom: 24px;
        right: 24px;
        width: 56px;
        height: 56px;
        border-radius: 50%;
        background-color: ${primaryColor};
        color: white;
        border: none;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 24px;
        z-index: 999999;
        transition: transform 0.2s ease;
      }
      .af-widget-btn:hover {
        transform: scale(1.05);
      }
      .af-chat-window {
        position: fixed;
        bottom: 96px;
        right: 24px;
        width: 380px;
        height: 540px;
        background: #ffffff;
        border-radius: 16px;
        box-shadow: 0 8px 32px rgba(0,0,0,0.15);
        display: none;
        flex-direction: column;
        overflow: hidden;
        z-index: 999999;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      }
      .af-chat-header {
        background: ${primaryColor};
        color: white;
        padding: 16px;
        font-weight: 600;
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      .af-chat-messages {
        flex: 1;
        padding: 16px;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .af-msg {
        max-width: 80%;
        padding: 10px 14px;
        border-radius: 12px;
        font-size: 14px;
        line-height: 1.4;
      }
      .af-msg-user {
        align-self: flex-end;
        background: #f3f4f6;
        color: #1f2937;
      }
      .af-msg-bot {
        align-self: flex-start;
        background: #eff6ff;
        color: #1e40af;
        border: 1px solid #dbeafe;
      }
      .af-chat-input-area {
        padding: 12px;
        border-top: 1px solid #e5e7eb;
        display: flex;
        gap: 8px;
      }
      .af-chat-input {
        flex: 1;
        border: 1px solid #d1d5db;
        border-radius: 8px;
        padding: 8px 12px;
        font-size: 14px;
        outline: none;
      }
      .af-send-btn {
        background: ${primaryColor};
        color: white;
        border: none;
        border-radius: 8px;
        padding: 8px 16px;
        cursor: pointer;
        font-weight: 500;
      }
    </style>
    <button class="af-widget-btn" id="af-toggle-btn">💬</button>
    <div class="af-chat-window" id="af-chat-window">
      <div class="af-chat-header">
        <span>${botTitle}</span>
        <span style="cursor:pointer;" id="af-close-btn">✕</span>
      </div>
      <div class="af-chat-messages" id="af-messages">
        <div class="af-msg af-msg-bot">您好！我是您的智能助理，请问有什么可以帮您？</div>
      </div>
      <div class="af-chat-input-area">
        <input type="text" class="af-chat-input" id="af-input" placeholder="输入您的问题..." />
        <button class="af-send-btn" id="af-send-btn">发送</button>
      </div>
    </div>
  `

  const toggleBtn = shadowRoot.getElementById('af-toggle-btn')
  const closeBtn = shadowRoot.getElementById('af-close-btn')
  const chatWindow = shadowRoot.getElementById('af-chat-window')
  const messagesContainer = shadowRoot.getElementById('af-messages')
  const inputField = shadowRoot.getElementById('af-input')
  const sendBtn = shadowRoot.getElementById('af-send-btn')

  let isOpen = false
  function toggleChat() {
    isOpen = !isOpen
    chatWindow.style.display = isOpen ? 'flex' : 'none'
    if (isOpen) inputField.focus()
  }

  toggleBtn.onclick = toggleChat
  closeBtn.onclick = toggleChat

  function appendMessage(sender, text) {
    const msg = document.createElement('div')
    msg.className = `af-msg af-msg-${sender}`
    msg.innerText = text
    messagesContainer.appendChild(msg)
    messagesContainer.scrollTop = messagesContainer.scrollHeight
  }

  async function handleSend() {
    const text = inputField.value.trim()
    if (!text) return
    inputField.value = ''
    appendMessage('user', text)

    try {
      const reqHeaders = buildWidgetHeaders({ publicToken, tenantId })
      const response = await fetch(`${apiUrl}/public/agents/${appId}/chat`, {
        method: 'POST',
        headers: reqHeaders,
        body: JSON.stringify({ message: text })
      })
      const resData = await response.json()
      if (resData.code === 200 && resData.data) {
        appendMessage('bot', resData.data.finalAnswer || '已收到您的回复')
      } else {
        appendMessage('bot', '服务响应异常: ' + (resData.message || '请稍后再试'))
      }
    } catch {
      appendMessage('bot', '网络连接失败，请检查网络配置')
    }
  }

  sendBtn.onclick = handleSend
  inputField.onkeypress = (e) => { if (e.key === 'Enter') handleSend() }
})()

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { buildWidgetHeaders }
}

export { buildWidgetHeaders };
