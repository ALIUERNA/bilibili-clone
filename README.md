# 哔哩哔哩仿站 · bilibili-clone

![Vue](https://img.shields.io/badge/Vue-3.5-42b883?logo=vue.js&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-6-646cff?logo=vite&logoColor=white)
![Element Plus](https://img.shields.io/badge/Element%20Plus-2.x-409eff?logo=element&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6db33f?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg)
<!-- 上传到 GitHub 之后，把下面这行的 ALIUERNA 换成你的 GitHub 用户名，CI 徽章就会亮起来 -->
![CI](https://github.com/ALIUERNA/bilibili-clone/actions/workflows/ci.yml/badge.svg)

一个**照着哔哩哔哩做的完整前后端分离网站**：首页推荐流、弹幕播放器、一键三连、排行榜、追番、个人空间、动态、搜索，
还带上传头像 / 改昵称 / 等级经验 / 每日签到等互动功能。

- 🚀 **下载下来就能跑**，不需要装数据库，Windows 下双击脚本即可
- 🎨 不依赖任何外部图片，封面是程序生成的渐变图，断网也能看
- 🧩 前端用 Element Plus 做组件能力，样式全部自己写，最大限度贴近 B 站观感

> ⚠️ 声明：这是**学习用的仿站项目**，页面结构与交互参考哔哩哔哩，与哔哩哔哩官方无任何关系。
> 站内所有 UP 主、视频、弹幕、评论均为程序生成的假数据，**不包含任何真实视频内容**。

---

## 一、效果预览

| 首页（推荐流 + 分区 + 轮播） | 播放页（弹幕 + 一键三连 + 评论） |
| --- | --- |
| ![首页](docs/images/home.png) | ![播放页](docs/images/video-playing.png) |

| 排行榜 | 番剧（追番） | 个人空间 |
| --- | --- | --- |
| ![排行榜](docs/images/ranking.png) | ![番剧](docs/images/bangumi.png) | ![个人空间](docs/images/space.png) |

---

## 二、快速开始

### 方式 1：Windows 一键脚本（推荐，最省事）

| 脚本 | 作用 |
| --- | --- |
| **`启动网站.bat`** | 启动网站，自动打开浏览器 http://localhost:8080 |
| `重新构建.bat` | 改了代码后重新打包（会自动先停掉旧服务） |
| `开发模式-前端.bat` | 前端开发模式，改代码热更新，端口 5173 |
| `上传到GitHub.bat` | 把项目推送到你自己的 GitHub 仓库 |

只要电脑上有 **JDK 17+**，双击 `启动网站.bat` 就能跑（脚本会自动找到 JDK；改代码才需要 Node.js 和 Maven）。

### 方式 2：Docker（一行命令）

```bash
docker compose up -d
# 打开 http://localhost:8080
```

镜像采用多阶段构建：Node 打前端 → Maven 打后端 → 只留一个 JRE 运行时，用户上传的头像挂在 `bili-uploads` 数据卷里。

### 方式 3：手动跑

```bash
# 前端
cd frontend
npm install --include=dev
npm run build

# 把前端产物塞进后端静态目录
cp -r dist/* ../backend/src/main/resources/static/

# 后端
cd ../backend
mvn package -DskipTests
java -jar target/bili-web.jar
```

---

## 三、功能清单

### 首页
- 18 个分区导航（推荐 / 热门 / 动画 / 番剧 / 国创 / 音乐 / 舞蹈 / 游戏 / 知识 / 科技 …），切换即时换内容
- Element Plus 轮播图 + 右侧「大家都在搜」热搜榜
- 视频卡片网格：16:9 封面、时长角标、悬停出现「xx播放 xx弹幕」蒙层与播放按钮
- 卡片会跟着鼠标轻轻倾斜（VueUse `useMouseInElement`），滚动时依次淡入
- 分页「加载更多」

### 播放页（核心）
- **弹幕引擎**：按时间轴滚动，支持顶部/底部固定弹幕；暂停、拖动进度、倍速后都能重新对齐
- 弹幕设置面板：不透明度 / 字号 / 速度 / 轨道数 / 显示区域
- 发弹幕：选颜色、即时上屏，自己发的弹幕带粉色描边
- 自定义播放器：进度条悬停预览、音量、0.5~2x 倍速、清晰度切换、全屏（VueUse `useFullscreen`）
- 键盘快捷键：`空格` 播放暂停、`←/→` 快退快进 5 秒、`F` 全屏
- **一键三连**：成功时炸开一圈小图标；点赞/投币/收藏/分享都会涨经验并飘出「+N 经验」
- UP 主卡片、关注、简介、标签（点击跳搜索）、相关推荐
- 评论区：排序、发表评论、点赞、楼中楼展开、自己发的评论带头像

### 用户系统
- 扫码风格登录弹窗（Element Plus Dialog），未登录做互动会自动弹出来
- **上传头像**：点击 / 拖拽都可以（Element Plus Upload），选完立即上传并马上生效，也能一键换回表情头像
- **修改昵称、个性签名**，带实时预览和字数统计
- **鼠标悬停头像弹出资料卡**：放大头像 + 昵称 + 等级 + 经验进度条 + 勋章 + 关注/粉丝/获赞/硬币 + 签到按钮
- **等级与经验**：互动就会涨经验（点赞 +5、投币 +10、收藏 +5、关注 +5、发弹幕 +3、发评论 +5、分享 +2），经验满了自动升级并放升级特效
- **每日签到**：+10 经验、+5 硬币，一天只能签到一次
- 资料落盘保存在 `uploads/profile.json`，刷新页面、重启服务都不会丢

### 其他页面
- **排行榜**：综合 / 最多点赞 / 投币 / 收藏 / 弹幕 五个榜单 + 分区筛选，前三名金银铜配色
- **番剧**：地区 / 状态筛选、追番、评分
- **个人空间**：渐变横幅、粉丝与播放统计、主页 / 动态 / 投稿三个标签页
- **动态**：动态流、点赞、转发
- **搜索**：顶栏联想词、相关用户、结果网格、热搜词

---

## 四、技术栈

### 前端
| 技术 | 用途 |
| --- | --- |
| **Vue 3**（`<script setup>`） | 组件开发 |
| **Vite 6** | 开发服务器 + 打包 |
| **Vue Router 4** | 路由（hash 模式，刷新不会 404） |
| **Pinia** | 用户状态、等级经验、飘字动画队列 |
| **Axios** | 接口封装与统一错误处理 |
| **Element Plus**（按需引入） | Dialog / Popover / Upload / Progress / Skeleton / Carousel / Empty / Backtop / Message / Tooltip |
| **VueUse** | `useMouseInElement`（卡片倾斜）、`useFullscreen`（全屏）、`useEventListener`（快捷键）、`useDebounceFn`（搜索防抖） |
| 原生 CSS | 全站样式手写，用 CSS 变量维护 B 站配色（`--bili-pink: #fb7299`） |

> Element Plus 只用来补齐组件能力，**没有破坏 B 站观感**：弹窗、上传、轮播都被覆盖成 B 站风格。
> 组件与样式均按需引入（`unplugin-vue-components`），el-table / el-date-picker 这类没用到的组件不会进包。

### 后端
| 技术 | 说明 |
| --- | --- |
| **Java 17 + Spring Boot 3.3** | REST 接口 |
| **内存模拟数据** | `DataStore` 启动时生成 16 个分区 / 80 条稿件 / 1.6 万条弹幕 / 上千条评论 / 12 部番剧 |
| **`uploads/`** | 用户资料与头像图片落盘（无需数据库） |
| **Maven** | 打包成一个可执行 jar |

---

## 五、目录结构

```
bilibili-clone/
├── 启动网站.bat / 重新构建.bat / 开发模式-前端.bat / 上传到GitHub.bat
├── Dockerfile / docker-compose.yml
├── .github/workflows/ci.yml          GitHub Actions 持续集成
├── backend/                          Spring Boot 后端
│   └── src/main/java/com/bili/demo/
│       ├── controller/               5 个控制器，19 个接口
│       ├── data/DataStore.java       ★ 视频/弹幕/评论等模拟数据
│       ├── data/UserStore.java       ★ 用户资料、等级经验、签到、头像落盘
│       └── model/                    实体类
├── frontend/                         Vue 3 前端
│   ├── eslint.config.js / .prettierrc.json
│   └── src/
│       ├── api/ router/ stores/ utils/ directives/ styles/
│       ├── components/               TopBar、PlayerView、DanmakuLayer、VideoCard、
│       │                             UserAvatar、UserHoverCard、ProfileModal、LoginModal…
│       └── views/                    首页/播放页/排行榜/番剧/空间/动态/搜索
├── tools/                            自动化脚本（验收测试、停止服务、bat 源码）
└── docs/images/                      README 截图
```

---

## 六、接口一览

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| GET | `/api/home` | 首页聚合：轮播 + 分区 + 热搜 |
| GET | `/api/videos?category=游戏&page=1&size=24` | 视频列表（分页） |
| GET | `/api/videos/{id}` | 视频详情（含弹幕） |
| GET | `/api/videos/{id}/related` | 相关推荐 |
| POST | `/api/videos/{id}/view` | 播放量 +1 |
| POST | `/api/videos/{id}/action?type=like` | 点赞/投币/收藏/分享/关注（会涨经验） |
| GET / POST | `/api/videos/{id}/danmaku` | 拉弹幕 / 发弹幕 |
| GET / POST | `/api/videos/{id}/comments` | 评论列表 / 发评论 |
| GET | `/api/rankings?type=all&limit=100` | 排行榜 |
| GET | `/api/bangumi?area=国产` | 番剧列表 |
| POST | `/api/bangumi/{id}/follow` | 追番 |
| GET | `/api/spaces/{upId}` | 个人空间 |
| GET | `/api/dynamics` | 动态流 |
| GET | `/api/search?keyword=美食` | 搜索 |
| GET | `/api/search/suggest?keyword=美` | 搜索联想 |
| POST | `/api/user/login` | 一键登录（演示） |
| GET | `/api/user/me` | 当前用户 + 经验进度 |
| POST | `/api/user/profile` | 修改昵称 / 签名 |
| POST | `/api/user/avatar` | 上传头像（multipart，5MB 以内） |
| DELETE | `/api/user/avatar` | 换回表情头像 |
| GET | `/api/files/avatar/{name}` | 读取头像图片 |
| POST | `/api/user/checkin` | 每日签到 |

---

## 七、工程化与测试

```bash
cd frontend
npm run dev        # 开发
npm run build      # 打包
npm run lint       # ESLint 检查
npm run format     # Prettier 格式化
npm run test:run   # Vitest 单元测试（20 个用例）
```

仓库里还带了几个用 **Chrome DevTools 协议**写的自动化脚本（不需要 puppeteer，用系统自带的 Chrome）：

```bash
node tools/verify.mjs      # 逐页检查渲染、JS 报错、布局溢出，并截图
node tools/e2e.mjs         # 模拟真人：点卡片 → 登录 → 发弹幕 → 发评论 → 三连 → 搜索
node tools/e2e-user.mjs    # 头像悬停卡片 / 改昵称 / 上传头像 / 签到 / 经验飘字
python tools/check_shots.py  # 对截图做像素级检查
```

当前验收状态：**页面检查 8 项通过、基础交互 11 项通过、用户互动 20 项通过、单元测试 20 个通过、零 JS 报错**。

`chcp 65001` 之类的坑：三个 `.bat` 是 **GBK 编码**（Windows cmd 默认 936 代码页，存 UTF-8 会乱码），
它们的 UTF-8 源文件放在 `tools/bat-src/*.bat.txt`，改完执行 `python tools/bat-src/build_bats.py` 重新生成。

---

## 八、想改成自己的内容？

| 想改什么 | 改哪里 |
| --- | --- |
| 分区、UP 主、视频标题 | `backend/src/main/java/com/bili/demo/data/DataStore.java` 顶部的 `CATEGORY_DEFS` / `VIDEO_TITLES` |
| 弹幕池、评论文案、封面配色 | 同上文件里的 `DANMAKU_POOL` / `COMMENT_TEXTS` / `PALETTE` |
| 番剧数据 | `DataStore.java` 的 `seedBangumi()` |
| 经验规则、等级经验表 | `UserStore.java` 的 `addExp()` / `nextExpMax()`，以及 `VideoController.expPayload()` 里的加成数值 |
| 首页端口、上传目录 | `backend/src/main/resources/application.yml` |

改完双击 `重新构建.bat` 即可。

---

## 九、怎么上传到 GitHub

**方式 A：用脚本（推荐）**

1. 打开 <https://github.com/new>，仓库名填 `bilibili-clone`，选 **Public**，
   下面的 README / .gitignore / license **都不要勾**，点 Create
2. 双击项目里的 **`上传到GitHub.bat`**，按提示粘贴仓库地址（形如 `https://github.com/你的用户名/bilibili-clone.git`）
3. 第一次推送会**弹出 GitHub 登录窗口**（你的电脑已装 Git Credential Manager），登录授权即可

**方式 B：手动敲命令**

```bash
git init
git add -A
git commit -m "feat: 仿哔哩哔哩网站（Vue3 + Spring Boot3）"
git branch -M main
git remote add origin https://github.com/你的用户名/bilibili-clone.git
git push -u origin main
```

> 💡 GitHub 从 2021 年起就不支持用「账号密码」推送了，只能用 **Personal Access Token**、**SSH key**
> 或者 **Git Credential Manager 登录**（本机已装，最省事）。
> 推送成功后，去仓库的 Actions 页面就能看到 CI 自动跑前端检查、单元测试、打包和冒烟测试。

---

## 十、常见问题

**1. 提示找不到 Java 17**
本项目用 Spring Boot 3，需要 Java 17+（Java 8 不行）。可以在 `启动网站.bat` 开头加一行：
`set JAVA_HOME=你的JDK目录`。

**2. 提示 8080 端口被占用**
脚本会自动尝试停掉旧服务；如果是别的软件占用，改 `application.yml` 里的 `server.port`。

**3. 报 `'vite' is not recognized`**
npm 跳过了开发依赖（环境变量里可能有 `NODE_ENV=production`）。脚本已加 `--include=dev`；
手动执行时先 `set NODE_ENV=` 再 `npm install --include=dev`。

**4. 播放器为什么没有画面？**
演示项目没有真实视频文件，画面是用随进度变化的渐变动画模拟的，弹幕、进度、倍速都是真的。
想接真实视频：把 `PlayerView.vue` 里的 `.scene` 换成 `<video :src="...">`，再把 `time` 换成 `video.currentTime`。

**5. 重启后我发的弹幕 / 评论没了？**
视频与弹幕存在内存里（重启恢复初始状态），但**用户资料、等级经验、头像会保存到 `uploads/`**，不会丢。

---

## 十一、许可

[MIT](LICENSE) —— 随便用、随便改，保留版权声明即可。页面设计版权归哔哩哔哩官方所有，本项目仅用于学习交流。
