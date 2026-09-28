# Java Web Classwork

Maven 主导的 Java Web 项目，Vue 3 + TypeScript + Vite 前端位于 `frontend/`。
首页使用 COBE 交互地球和本地 Silkscreen 像素字体。

## 目录

- `pom.xml`：统一构建入口，安装、构建前端并打包 WAR。
- `frontend/`：前端源码及 npm 锁文件。
- `src/main/java/`：预留 Java 后端源码目录。
- `src/main/resources/`：预留 Java 配置目录。
- `src/main/webapp/`：预留 Web 资源目录。
- `target/`：前端产物和 WAR，禁止提交。

目前没有业务接口或 Java 框架依赖。后续根据课程要求添加 Servlet 或其他后端实现。

## 环境

JDK 17+、Maven 3.9+；Node.js 满足 `frontend/package.json` 中的 engines 要求，且 npm 在 PATH 中。
在 IDEA 中打开根目录，并将 `pom.xml` 导入为 Maven 项目。

## 开发首页

```sh
cd frontend
npm ci
npm run dev
```

## 统一打包

```sh
mvn clean package
```

Maven 依次运行 `npm ci`、`npm run build`（含 TypeScript 检查）和 WAR 打包。
产物为 `target/java-web-classwork.war`，可部署到兼容的 Tomcat 容器。
默认访问 `/java-web-classwork/`；前端资源使用相对路径，可适配 WAR 上下文路径。
Maven 不负责自动安装 Node.js 或启动 Tomcat。

## 字体

Silkscreen © The Silkscreen Project Authors，使用 SIL Open Font License 1.1。
字体与许可证位于 `frontend/public/fonts/`，运行时无需请求外部字体服务。
