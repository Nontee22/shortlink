# ShortLink — 短链接服务

一个基于 Spring Boot + Vue 3 的短链接生成与统计系统，支持自定义短码、访问统计、设备分析、二维码分享等功能。

## 功能特性

- **短链接生成** — 长链接转短链接，支持自动生成和自定义短码
- **访问统计** — PV/UV 统计、今日访问量、7 天趋势图
- **设备分析** — 自动解析 User-Agent，统计设备类型、浏览器、操作系统分布
- **二维码分享** — 一键生成短链接二维码
- **缓存加速** — Redis 缓存热点链接，布隆过滤器防止缓存穿透
- **限流保护** — 基于 Redis 滑动窗口的接口限流
- **异步处理** — RabbitMQ 异步记录访问日志，解耦跳转与统计
- **管理后台** — 短链接列表、搜索筛选、批量删除、状态启停

## 技术栈

### 后端
| 技术 | 说明 |
|------|------|
| Spring Boot 3.2 | 核心框架 |
| MyBatis-Plus 3.5 | ORM 框架 |
| MySQL 8.0 | 关系型数据库 |
| Redis 7 | 缓存 / 布隆过滤器 / 限流 |
| Redisson 3.25 | 分布式锁 |
| RabbitMQ 3 | 消息队列 |
| Knife4j 4.3 | API 文档 |

### 前端
| 技术 | 说明 |
|------|------|
| Vue 3 | 前端框架 |
| Element Plus | UI 组件库 |
| ECharts 5 | 数据可视化 |
| Pinia | 状态管理 |
| Vite 5 | 构建工具 |
| QRCode | 二维码生成 |

## 项目结构

```
ShortLink/
├── shortlink-backend/          # 后端服务
│   └── src/main/
│       ├── java/com/shortlink/
│       │   ├── common/         # 公共模块（配置、异常、拦截器、响应封装）
│       │   ├── controller/     # 控制器
│       │   ├── dto/            # 数据传输对象
│       │   ├── entity/         # 数据库实体
│       │   ├── mapper/         # MyBatis Mapper
│       │   ├── mq/             # 消息队列（生产者/消费者）
│       │   ├── service/        # 业务逻辑层
│       │   ├── task/           # 定时任务
│       │   ├── util/           # 工具类
│       │   └── vo/             # 视图对象
│       └── resources/
│           ├── db/init.sql     # 数据库初始化脚本
│           └── application.yml # 应用配置
├── shortlink-frontend/         # 前端服务
│   └── src/
│       ├── api/                # API 接口封装
│       ├── router/             # 路由配置
│       ├── utils/              # 工具函数
│       └── views/              # 页面组件
├── docker-compose.yml          # Docker 编排
├── .env.example                # 环境变量模板
└── README.md
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- Docker & Docker Compose（用于启动中间件）

### 1. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env` 文件，填入你的配置：

```env
SHORTLINK_DOMAIN=http://your-domain-or-ip
MYSQL_PASSWORD=your-mysql-password
RABBITMQ_USER=your-rabbitmq-user
RABBITMQ_PASSWORD=your-rabbitmq-password
```

### 2. 启动中间件

```bash
docker-compose up -d
```

这将启动 MySQL、Redis、RabbitMQ，并自动执行数据库初始化脚本。

### 3. 启动后端

```bash
cd shortlink-backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`，API 文档地址为 `/swagger-ui.html`。

### 4. 启动前端

```bash
cd shortlink-frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，已配置代理转发至后端。

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/short-link/create` | 创建短链接 |
| GET | `/api/short-link/list` | 分页查询列表 |
| GET | `/api/short-link/{shortCode}` | 获取短链接详情 |
| PUT | `/api/short-link/{shortCode}/status` | 更新启用/禁用状态 |
| DELETE | `/api/short-link/{shortCode}` | 删除短链接 |
| DELETE | `/api/short-link/batch` | 批量删除 |
| GET | `/sl/{shortCode}` | 短链接跳转（302） |
| GET | `/api/stats/{shortCode}` | 获取统计数据 |

## 页面截图

### 创建短链
输入原始链接，支持可选描述、自定义短码和有效期设置，生成后自动展示二维码。

### 短链列表
支持关键词搜索、状态筛选、日期范围筛选，可批量操作。

### 统计详情
展示 PV/UV 总量、今日访问、7 天趋势折线图、设备分布饼图、最近访问记录。

## License

MIT
