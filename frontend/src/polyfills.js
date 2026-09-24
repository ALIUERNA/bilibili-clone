/**
 * 老浏览器兼容补丁（Chrome 60+ / Edge 18+ / Firefox 60+ / Safari 11+ 等）。
 *
 * 现代浏览器里这些 API 都已存在，本文件几乎不会执行任何逻辑；
 * 老的浏览器（比如企业内网的 Chrome 63、Safari 11）会在这里补齐缺口，
 * 避免因为某个 API 不存在导致整站白屏。
 *
 * 说明：Vite 打包已经通过 @vitejs/plugin-legacy 生成了 legacy 版本（带 core-js 自动按需 polyfill），
 * 这里再手写一层「最小兜底」，覆盖 legacy 构建没生成时的场景（例如直接用现代版脚本）。
 */

/* eslint-disable no-extend-native */
;(function () {
  // Object.assign
  if (typeof Object.assign !== 'function') {
    Object.defineProperty(Object, 'assign', {
      writable: true,
      configurable: true,
      value: function assign(target) {
        if (target == null) throw new TypeError('Cannot convert undefined or null to object')
        var to = Object(target)
        for (var i = 1; i < arguments.length; i++) {
          var next = arguments[i]
          if (next == null) continue
          for (var key in next) {
            if (Object.prototype.hasOwnProperty.call(next, key)) to[key] = next[key]
          }
        }
        return to
      }
    })
  }

  // Array.prototype.includes / find / findIndex / flat
  if (!Array.prototype.includes) {
    Array.prototype.includes = function (search, fromIndex) {
      return this.indexOf(search, fromIndex || 0) !== -1
    }
  }
  if (!Array.prototype.find) {
    Array.prototype.find = function (fn, thisArg) {
      for (var i = 0; i < this.length; i++) {
        if (fn.call(thisArg, this[i], i, this)) return this[i]
      }
      return undefined
    }
  }
  if (!Array.prototype.findIndex) {
    Array.prototype.findIndex = function (fn, thisArg) {
      for (var i = 0; i < this.length; i++) {
        if (fn.call(thisArg, this[i], i, this)) return i
      }
      return -1
    }
  }
  if (!Array.prototype.flat) {
    Array.prototype.flat = function (depth) {
      var d = depth === undefined ? 1 : depth
      var out = []
      var walk = function (arr, level) {
        for (var i = 0; i < arr.length; i++) {
          if (Array.isArray(arr[i]) && level > 0) walk(arr[i], level - 1)
          else out.push(arr[i])
        }
      }
      walk(this, d)
      return out
    }
  }

  // String.prototype.padStart / trimStart
  if (!String.prototype.padStart) {
    String.prototype.padStart = function (targetLength, padString) {
      var s = String(this)
      var pad = padString === undefined ? ' ' : String(padString)
      if (s.length >= targetLength || !pad) return s
      var fill = ''
      while (fill.length + s.length < targetLength) fill += pad
      return fill.slice(0, targetLength - s.length) + s
    }
  }
  if (!String.prototype.trimStart) {
    String.prototype.trimStart = function () {
      return String(this).replace(/^\s+/, '')
    }
  }

  // Object.fromEntries（设置页等地方可能用到）
  if (typeof Object.fromEntries !== 'function') {
    Object.fromEntries = function (entries) {
      var obj = {}
      var list = Array.from(entries || [])
      for (var i = 0; i < list.length; i++) {
        obj[list[i][0]] = list[i][1]
      }
      return obj
    }
  }

  // Promise.prototype.finally
  if (typeof Promise !== 'undefined' && !Promise.prototype.finally) {
    Promise.prototype.finally = function (callback) {
      var P = this.constructor
      return this.then(
        function (value) {
          return P.resolve(callback()).then(function () {
            return value
          })
        },
        function (reason) {
          return P.resolve(callback()).then(function () {
            throw reason
          })
        }
      )
    }
  }

  // Array.from
  if (!Array.from) {
    Array.from = function (arrayLike, mapFn, thisArg) {
      var out = []
      for (var i = 0; i < (arrayLike.length || 0); i++) {
        out.push(mapFn ? mapFn.call(thisArg, arrayLike[i], i) : arrayLike[i])
      }
      return out
    }
  }

  // URLSearchParams（表单提交用得到）
  if (typeof window !== 'undefined' && !window.URLSearchParams) {
    window.URLSearchParams = function (init) {
      this.params = {}
      var self = this
      if (typeof init === 'string') {
        init
          .replace(/^\?/, '')
          .split('&')
          .filter(Boolean)
          .forEach(function (pair) {
            var kv = pair.split('=')
            self.params[decodeURIComponent(kv[0])] = decodeURIComponent(kv[1] || '')
          })
      }
      this.append = function (k, v) {
        self.params[k] = v
      }
      this.toString = function () {
        return Object.keys(self.params)
          .map(function (k) {
            return encodeURIComponent(k) + '=' + encodeURIComponent(self.params[k])
          })
          .join('&')
      }
    }
  }

  // IntersectionObserver（v-reveal 滚动淡入用得到）：不支持时直接标记为已显示，保证内容可见
  if (typeof window !== 'undefined' && !('IntersectionObserver' in window)) {
    window.IntersectionObserver = function (callback) {
      this.observe = function (el) {
        callback([{ target: el, isIntersecting: true, intersectionRatio: 1 }], this)
      }
      this.unobserve = function () {}
      this.disconnect = function () {}
    }
  }

  // requestAnimationFrame（弹幕 / 播放器时钟用得到）
  if (typeof window !== 'undefined' && !window.requestAnimationFrame) {
    window.requestAnimationFrame = function (cb) {
      return setTimeout(function () {
        cb(Date.now())
      }, 16)
    }
    window.cancelAnimationFrame = function (id) {
      clearTimeout(id)
    }
  }

  // Promise 缺失时（极老的浏览器）给出明确提示，而不是白屏
  if (typeof Promise === 'undefined') {
    var tip = document.createElement('div')
    tip.style.cssText =
      'position:fixed;z-index:99999;left:0;right:0;top:0;padding:16px;background:#fff1f0;color:#a8071a;font:14px/22px sans-serif;text-align:center'
    tip.textContent = '当前浏览器版本过旧，无法运行本站，请升级到 Chrome 80+ / Edge 80+ / Firefox 78+ / Safari 13+ 后重试。'
    document.documentElement.appendChild(tip)
  }
})()
