/**
 * v-reveal 指令：元素滚动到视口里时淡入上浮，让页面「活」起来。
 *
 * 用法：
 *   <div v-reveal>...</div>
 *   <div v-reveal="{ delay: 120 }">...</div>   // 延迟 120ms，做交错动画
 */
let observer = null

function getObserver() {
  if (observer) return observer
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('revealed')
          observer.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.06, rootMargin: '0px 0px -40px 0px' }
  )
  return observer
}

export const reveal = {
  mounted(el, binding) {
    // 用户如果开了「减少动态效果」，就不做动画，直接显示
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (reduce) {
      el.classList.add('revealed')
      return
    }
    el.classList.add('reveal')
    const delay = binding.value?.delay || 0
    if (delay) el.style.transitionDelay = `${delay}ms`
    getObserver().observe(el)
  },
  unmounted(el) {
    if (observer) observer.unobserve(el)
  }
}
