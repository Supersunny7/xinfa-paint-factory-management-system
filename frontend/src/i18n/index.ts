import { createI18n } from 'vue-i18n'
import enUS from './locales/en-US'
import zhCN from './locales/zh-CN'

export type AppLocale = 'en-US' | 'zh-CN'
export const LANGUAGE_STORAGE_KEY = 'appLanguage'

function initialLocale(): AppLocale {
  const stored = localStorage.getItem(LANGUAGE_STORAGE_KEY)
  if (stored === 'en-US' || stored === 'zh-CN') return stored
  return navigator.language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
}

const locale = initialLocale()
document.documentElement.lang = locale
document.title = locale === 'zh-CN' ? '新发油漆业务管理系统' : 'Xinfa Paint Factory Management System'

const i18n = createI18n({
  legacy: false,
  locale,
  fallbackLocale: 'en-US',
  messages: { 'en-US': enUS, 'zh-CN': zhCN },
})

export default i18n
