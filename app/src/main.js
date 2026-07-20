import { createSSRApp } from 'vue'
import uviewPlus from 'uview-plus'
import App from './App.vue'
import './styles/h5-phone-frame.js'

export function createApp() {
  const app = createSSRApp(App)
  app.use(uviewPlus)
  return { app }
}
