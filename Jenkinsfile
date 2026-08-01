/**
 * 智慧社区后端 Jenkins 流水线（适配本仓库结构）
 *
 * 前提：
 * - Jenkins 已装 JDK17、Maven、Git；若用 Docker 部署再装 Docker
 * - Job 的「源码管理」指向 https://gitee.com/zhihuo_1/zhihuishequ.git 分支 master
 *   （或下面 stage 里自己 git）
 * - 中间件已用远程：MySQL/Redis/MinIO（见各服务 application.yml）
 * - AI Key 建议在 Jenkins 凭据里配环境变量 AI_API_KEY，不要写进仓库
 *
 * 说明：根目录没有 Dockerfile，默认用 jar 部署；不要用「单容器 8080」那种模板。
 */
pipeline {
    agent any

    tools {
        // 若 Jenkins 全局工具名不同，改成你机器上的名字，或删掉 tools 段改用系统 PATH
        // jdk 'jdk17'
        // maven 'maven3'
    }

    environment {
        BACKEND = 'backend'
        DEPLOY_DIR = '/opt/zhihuishequ'
        // 远程中间件（与 application.yml 默认一致，可在 Job 里覆盖）
        MYSQL_HOST = '82.156.66.83'
        REDIS_HOST = '82.156.66.83'
        // AI：在 Jenkins → Credentials 配 Secret text，ID=AI_API_KEY，再取消下一行注释
        // AI_API_KEY = credentials('AI_API_KEY')
        AI_PROVIDER = 'http'
        AI_MODEL = 'qwen-vl-plus'
    }

    stages {
        stage('拉取代码') {
            steps {
                // 若 Job 已配置 SCM，可改成：checkout scm
                git branch: 'master',
                    url: 'https://gitee.com/zhihuo_1/zhihuishequ.git'
                echo "代码拉取完成: ${env.GIT_COMMIT}"
            }
        }

        stage('Maven打包') {
            steps {
                dir("${BACKEND}") {
                    sh 'mvn -B clean package -Dmaven.test.skip=true'
                }
            }
        }

        stage('发布 jar') {
            steps {
                sh '''
set -e
mkdir -p "${DEPLOY_DIR}"
cp -f backend/community-gateway/target/community-gateway-*.jar "${DEPLOY_DIR}/community-gateway.jar" 2>/dev/null \
  || cp -f backend/community-gateway/target/*.jar "${DEPLOY_DIR}/community-gateway.jar"
cp -f backend/community-auth/target/community-auth-*.jar "${DEPLOY_DIR}/community-auth.jar" 2>/dev/null \
  || cp -f backend/community-auth/target/*.jar "${DEPLOY_DIR}/community-auth.jar"
cp -f backend/community-repair/target/community-repair-*.jar "${DEPLOY_DIR}/community-repair.jar" 2>/dev/null \
  || cp -f backend/community-repair/target/*.jar "${DEPLOY_DIR}/community-repair.jar"
cp -f backend/community-ai/target/community-ai.jar "${DEPLOY_DIR}/community-ai.jar" 2>/dev/null \
  || cp -f backend/community-ai/target/*.jar "${DEPLOY_DIR}/community-ai.jar"
# 可选模块
cp -f backend/community-saas/target/*.jar "${DEPLOY_DIR}/community-saas.jar" 2>/dev/null || true
cp -f backend/community-order/target/*.jar "${DEPLOY_DIR}/community-order.jar" 2>/dev/null || true
cp -f backend/community-stats/target/*.jar "${DEPLOY_DIR}/community-stats.jar" 2>/dev/null || true
ls -la "${DEPLOY_DIR}"
                '''
            }
        }

        stage('重启服务') {
            steps {
                sh '''
set -e
mkdir -p "${DEPLOY_DIR}/logs"

start_one() {
  NAME="$1"
  JAR="$2"
  PORT="$3"
  shift 3
  # 停旧进程
  if [ -f "${DEPLOY_DIR}/${NAME}.pid" ]; then
    kill "$(cat "${DEPLOY_DIR}/${NAME}.pid")" 2>/dev/null || true
    rm -f "${DEPLOY_DIR}/${NAME}.pid"
  fi
  pkill -f "${JAR}" 2>/dev/null || true
  sleep 1
  nohup java -jar "${DEPLOY_DIR}/${JAR}" \
    --server.port="${PORT}" \
    "$@" \
    > "${DEPLOY_DIR}/logs/${NAME}.log" 2>&1 &
  echo $! > "${DEPLOY_DIR}/${NAME}.pid"
  echo "started ${NAME} pid=$(cat ${DEPLOY_DIR}/${NAME}.pid) port=${PORT}"
}

# 顺序：业务 → 网关
start_one auth   community-auth.jar   8081
start_one repair community-repair.jar 8083
start_one ai     community-ai.jar     8084
# start_one saas community-saas.jar 8082
# start_one order community-order.jar 8085
# start_one stats community-stats.jar 8086
start_one gateway community-gateway.jar 8080

sleep 8
ss -lntp | grep -E '8080|8081|8083|8084' || netstat -lntp | grep -E '8080|8081|8083|8084' || true
                '''
            }
        }
    }

    post {
        success {
            echo '部署成功：网关 http://服务器IP:8080'
        }
        failure {
            echo '部署失败：请看本 Job 控制台日志，以及 /opt/zhihuishequ/logs/*.log'
        }
    }
}
