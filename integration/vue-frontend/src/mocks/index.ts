import { setupAxiosMocks } from './axiosMocks'
import { installMockWS } from './wsSimulator'

/**
 * 初始化 Demo Mock 层。
 * 在 main.ts 中仅开发模式下调用。
 */
export function setupMocks() {
  // 写入假 JWT token（后端 Mock 不校验，前端 axios 拦截器会读取）
  if (!localStorage.getItem('token')) {
    localStorage.setItem('token', 'mock-jwt-token-for-demo')
  }

  // 替换 WebSocket
  installMockWS()

  // 拦截所有 axios 请求
  setupAxiosMocks()
}
