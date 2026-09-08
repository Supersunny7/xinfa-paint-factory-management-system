import { defineAsyncComponent, defineComponent, h, type Component } from 'vue'
import { useI18n } from 'vue-i18n'

type PageLoader = () => Promise<{ default: Component }>

export function bilingualPage(englishLoader: PageLoader, chineseLoader: PageLoader) {
  const EnglishPage = defineAsyncComponent(englishLoader)
  const ChinesePage = defineAsyncComponent(chineseLoader)

  return defineComponent({
    name: 'BilingualPage',
    setup() {
      const { locale } = useI18n()
      return () => h(locale.value === 'zh-CN' ? ChinesePage : EnglishPage)
    },
  })
}
