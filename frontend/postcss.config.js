import autoprefixer from 'autoprefixer'

/**
 * PostCSS 配置：Autoprefixer 会读取 package.json 里的 browserslist，
 * 自动给 CSS 加上 -webkit- / -moz- / -ms- 前缀，保证不同版本浏览器显示一致。
 */
export default {
  plugins: [
    autoprefixer({
      // 与 package.json 的 browserslist 保持一致
      overrideBrowserslist: [
        'Chrome >= 70',
        'Edge >= 79',
        'Firefox >= 68',
        'Safari >= 12',
        'iOS >= 12',
        'Android >= 8',
        'last 3 versions',
        'not dead'
      ],
      grid: 'autoplace'
    })
  ]
}
