import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'

async function bootstrap() {
  // 开发模式下启用 Mock 层（无需真实后端即可运行 Demo）
  if (import.meta.env.DEV) {
    const { setupMocks } = await import('./mocks/index')
    setupMocks()
  }

  createApp(App).use(createPinia()).mount('#app')
}

bootstrap()
