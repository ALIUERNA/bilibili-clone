# 哔哩哔哩仿站（Vue 3 + Spring Boot 3）

一个模仿哔哩哔哩（bilibili）页面结构和交互方式的完整前后端分离项目。
**下载下来就能跑，不需要装数据库**，也不需要你写任何代码。

> ⚠️ 说明：这是学习用的仿站项目，页面结构参考哔哩哔哩，但和哔哩哔哩官方没有任何关系。
> 项目里的 UP 主、视频、弹幕、评论全部是程序生成的假数据，不含任何真实视频内容。

---

## 一、怎么运行（最快的方式）

1. 双击 **`启动网站.bat`**
2. 等 8 秒左右，浏览器会自动打开 <http://localhost:8080>
3. 想关掉网站，直接关掉那个黑色命令行窗口就行

如果提示找不到 Java 17，看下面的「常见问题」。

### 其他两个脚本

| 脚本 | 作用 |
| --- | --- |
| `启动网站.bat` | 启动网站（日常就看这个），会自动打开浏览器 |
| `开发模式-前端.bat` | 前端开发模式，改代码自动刷新，端口 5173（要先启动后端） |
| `重新构建.bat` | 修改代码后重新编译前端 + 后端，生成新的 jar（会自动先停掉正在运行的服务） |

> 小知识：这三个 .bat 是用 GBK 编码保存的。因为 Windows 的 cmd 默认代码页是 936（中文），
> 如果存成 UTF-8，里面的中文会变成乱码，甚至导致脚本直接跑不起来。
> 它们的「源文件」放在 `tools/bat-src/*.bat.txt`（UTF-8 编码），改完执行
> `python tools/bat-src/build_bats.py` 就会重新生成根目录下的 .bat。

---

## 二、技术栈

**前端**

- Vue 3（组合式 API，`<script setup>`）
- Vite 6（打包 / 开发服务器）
- Vue Router 4（页面路由，hash 模式）
- Pinia（登录状态管理）
- Axios（接口请求封装）
- 原生 CSS（没有用 UI 框架，样式全部手写，方便你改）

**后端**

- Java 17 + Spring Boot 3.3.5
- Spring MVC（REST 接口）
- 纯内存模拟数据（`DataStore` 里用代码生成 80 条稿件、1.6 万条弹幕、上千条评论）
- Maven 打包成一个可执行 jar

---

## 三、目录结构

```
bilibili-clone/
├── 启动网站.bat              ← 双击这个就能跑
├── 重新构建.bat
├── 开发模式-前端.bat
├── backend/                  ← Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/bili/demo/
│       ├── BiliApplication.java     启动类
│       ├── config/WebConfig.java    跨域配置
│       ├── model/                   实体类（视频、弹幕、评论、UP主、番剧…）
│       ├── data/DataStore.java      ★ 所有模拟数据都在这里
│       └── controller/              接口层
│           ├── HomeController.java      首页 / 列表 / 排行榜
│           ├── VideoController.java     视频详情 / 弹幕 / 评论 / 三连
│           ├── BangumiController.java   番剧 / 追番
│           ├── SpaceController.java     个人空间 / 动态 / 搜索
│           └── UserController.java      登录 / 退出
└── frontend/                 ← Vue 3 前端
    ├── vite.config.js
    └── src/
        ├── api/index.js             接口封装
        ├── router/index.js          路由表
        ├── stores/user.js           登录状态
        ├── utils/format.js          播放量、时长的格式化
        ├── styles/global.css        全局样式 + B站配色变量
        ├── components/
        │   ├── TopBar.vue           ★ 顶部导航栏（搜索、头像、投稿）
        │   ├── PlayerView.vue       ★ 播放器（进度条、倍速、全屏、弹幕设置）
        │   ├── DanmakuLayer.vue     ★ 弹幕引擎
        │   ├── VideoCard.vue        视频卡片（网格 / 横向 / 排行榜三种样式）
        │   ├── CommentItem.vue      评论（含楼中楼）
        │   ├── LoginModal.vue       登录弹窗（扫码样式）
        │   └── AppFooter.vue        页脚
        └── views/
            ├── HomeView.vue         首页（轮播 + 分区 + 推荐流）
            ├── VideoView.vue        播放页（播放器 + 三连 + 评论 + 相关推荐）
            ├── RankingView.vue      排行榜
            ├── BangumiView.vue      番剧（追番）
            ├── SpaceView.vue        个人空间（主页 / 动态 / 投稿）
            ├── DynamicView.vue      动态流
            └── SearchView.vue       搜索结果页
```

---

## 四、做了哪些功能

**顶部导航**

- B 站风格粉色 logo、分区导航、搜索框（带输入联想下拉）、大会员、消息/历史/创作中心、头像下拉、粉色「投稿」按钮
- 点击「消息 / 历史」等未实现的功能会有一个友好的提示，不会白屏

**首页**

- 分区导航条：推荐 / 热门 / 动画 / 番剧 / 国创 / 音乐 / 舞蹈 / 游戏 / 知识 / 科技……共 18 个分区，切换会重新拉数据
- 轮播图（4.5 秒自动切换，可点圆点）、右侧「大家都在搜」热搜榜
- 视频卡片网格：16:9 封面、时长角标、鼠标悬停出现「xx播放 xx弹幕」蒙层、两行标题省略、UP 主
- 「加载更多」分页

**播放页（重点）**

- 播放器：播放/暂停、可拖动进度条（悬停显示时间预览）、音量、倍速（0.5~2x）、清晰度切换、全屏
- **弹幕引擎**：弹幕按时间轴滚动，支持顶部/底部固定弹幕，支持暂停、拖动进度后重新对齐
- 弹幕开关、弹幕设置面板（不透明度 / 字号 / 速度 / 轨道数 / 显示区域）
- 发弹幕：输入内容 + 选颜色，发送后立刻出现在画面上（自己发的弹幕有粉色描边）
- 一键三连（点赞 / 投币 / 收藏）、分享
- UP 主卡片 + 关注、视频简介、标签（点击可跳搜索）
- 评论区：排序、发表评论、点赞、楼中楼展开
- 右侧相关推荐（同分区优先）
- 键盘快捷键：`空格` 播放暂停、`←/→` 快退快进 5 秒、`F` 全屏

**其他页面**

- 排行榜：综合 / 最多点赞 / 投币 / 收藏 / 弹幕 五个榜 + 分区筛选，前三名金银铜配色
- 番剧：地区 / 状态筛选、追番按钮、评分
- 个人空间：渐变横幅、头像、粉丝/播放统计、主页 / 动态 / 投稿三个标签页
- 动态流：点赞、瀑布式卡片
- 搜索：相关用户 + 视频结果 + 热搜词

**登录**

- 点「点赞 / 评论 / 发弹幕 / 关注」如果没登录，会自动弹出登录框
- 登录框模仿扫码登录界面（二维码是画出来的），点「一键登录」即可登录
- 登录状态存 localStorage，刷新不掉线

---

## 五、接口一览

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| GET | `/api/home` | 首页聚合：轮播 + 分区 + 热搜 |
| GET | `/api/videos?category=游戏&page=1&size=24` | 视频列表（分页） |
| GET | `/api/videos/{id}` | 视频详情（含弹幕） |
| GET | `/api/videos/{id}/related` | 相关推荐 |
| POST | `/api/videos/{id}/view` | 播放量 +1 |
| POST | `/api/videos/{id}/action?type=like` | 点赞 / coin / fav / share / follow |
| GET | `/api/videos/{id}/danmaku` | 拉弹幕 |
| POST | `/api/videos/{id}/danmaku` | 发弹幕 |
| GET | `/api/videos/{id}/comments` | 评论列表 |
| POST | `/api/videos/{id}/comments` | 发评论 |
| GET | `/api/rankings?type=all&limit=100` | 排行榜 |
| GET | `/api/bangumi?area=国产` | 番剧列表 |
| POST | `/api/bangumi/{id}/follow` | 追番 |
| GET | `/api/spaces/{upId}` | 个人空间 |
| GET | `/api/dynamics` | 动态流 |
| GET | `/api/search?keyword=美食` | 搜索 |
| GET | `/api/search/suggest?keyword=美` | 搜索联想 |
| POST | `/api/user/login` | 一键登录（演示） |
| GET | `/api/user/me` | 当前用户 |

---

## 六、想改成你自己的数据？

打开 `backend/src/main/java/com/bili/demo/data/DataStore.java`，最上面几个数组就是全部数据源：

- `CATEGORY_DEFS`：分区 + 对应的 UP 主（昵称、头像 emoji、签名、粉丝数）
- `VIDEO_TITLES`：每个分区的视频标题（想加视频就往数组里加字符串）
- `DANMAKU_POOL` / `COMMENT_TEXTS`：弹幕池、评论内容
- `PALETTE`：封面渐变色

改完双击 `重新构建.bat` 就行了。

---

## 七、常见问题

**1. 提示「没有找到 Java 17 或更高版本」**

这个项目用的是 Spring Boot 3，需要 Java 17+（Java 8 跑不了）。
你电脑上其实装了 JDK 17，可以先在这个 bat 文件的开头加一行：

```bat
set JAVA_HOME=D:\ProgramData\jdk\jdk-17
```

**2. 提示端口 8080 被占用**

说明已经有一个服务在跑了（或者别的软件占用了）。
可以先看看是不是已经开过浏览器窗口；要换端口的话，改
`backend/src/main/resources/application.yml` 里的 `server.port`，然后重新构建。

**3. 双击「重新构建.bat」报错 `'vite' is not recognized`**

说明 npm 把开发依赖跳过了（你的系统环境变量里可能设置了 `NODE_ENV=production`）。
脚本里已经加了 `--include=dev` 处理这个情况；如果还不行，在 frontend 目录下手动执行：

```bat
set NODE_ENV=
npm install --include=dev
npm run build
```

**4. 页面上的视频点开为什么没有画面？**

因为演示项目里没有真实的视频文件，播放器是模拟的（画面是渐变动画 + 弹幕）。
想接真实视频的话，把 `frontend/src/components/PlayerView.vue` 里 `.scene` 那一段
换成 `<video :src="xxx">`，再把 `time` 换成 `video.currentTime` 就可以了。

**5. 重启后我发的弹幕 / 评论怎么没了？**

数据都存在内存里（没有数据库），重启就恢复初始状态。想持久化的话可以接 MySQL + MyBatis / JPA。

---

## 八、开发者：自动化测试与工具

项目里带了几个用 Chrome DevTools 协议写的脚本（不需要装 puppeteer，用系统自带的 Chrome）：

```bat
node tools\verify.mjs   :: 逐页检查元素是否渲染、有没有 JS 报错、有没有布局溢出，并截图到 shots\
node tools\e2e.mjs      :: 模拟真人：点卡片 → 登录 → 发弹幕 → 发评论 → 三连 → 搜索
python tools\check_shots.py    :: 对截图做像素级检查（颜色丰富度、品牌色占比等）
```

运行前请先启动网站。

其他工具：

- `tools/stop-server.ps1`：停掉正在运行的网站（重新构建脚本会自动调用）
- `tools/bat-src/`：三个 .bat 的 UTF-8 源文件 + 生成脚本（见上文「小知识」）

当前验收状态：**8 项页面检查全部通过、11 项交互测试全部通过、零 JS 报错**。
