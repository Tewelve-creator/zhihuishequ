# Jenkins 流水线说明（本仓库）

仓库根目录已提供 [`Jenkinsfile`](../Jenkinsfile)。  
你贴的那段模板**不能直接用**，原因如下。

## 原流水线为什么会挂

| 原写法 | 问题 |
|--------|------|
| `echo "代码拉取完成"` | 没有真正 git clone / checkout |
| `mvn clean package` 在仓库根目录 | 父 POM 在 **`backend/`**，根目录没有 `pom.xml` |
| `docker build -t zhihuishequ:latest .` | 仓库**没有 Dockerfile**，构建必失败 |
| 一个容器 `-p 8081:8080` | 本项目是**多微服务**（网关 8080、auth 8081、repair 8083、ai 8084…），不是单体 |

## 推荐用法

1. Jenkins 新建 Pipeline Job  
2. 定义方式选 **Pipeline script from SCM**  
   - Git：`https://gitee.com/zhihuo_1/zhihuishequ.git`  
   - 分支：`*/master`  
   - Script Path：`Jenkinsfile`  
3. 构建节点需：JDK 17、Maven 3.8+、能访问 `82.156.66.83`（MySQL/Redis/MinIO）  
4. AI Key：Jenkins 凭据里加 `AI_API_KEY`，在 `Jenkinsfile` 的 `environment` 里取消注释绑定  
5. 部署目录默认 `/opt/zhihuishequ`（Linux 节点）；Windows 节点需改脚本为 PowerShell

## 端口

| 服务 | 端口 |
|------|------|
| community-gateway | 8080（对外入口） |
| community-auth | 8081 |
| community-repair | 8083 |
| community-ai | 8084 |

前端 `admin-web` / `app` 一般本机 `npm run dev`，或另做 Nginx 静态部署，不在该流水线里。

## 若坚持 Docker

需先为各服务写 Dockerfile（或一个 docker-compose 起多个 jar），再改 stage；当前仓库未带镜像定义，故默认 **jar + nohup**。
