pipeline {
    agent any
    environment {
        SERVICE_NAME = "zhihuishequ"
    }
    stages {
        stage('拉取代码') {
            steps {
                git url: 'https://gitee.com/zhihuo_1/zhihuishequ.git',
                    branch: 'master',
                    credentialsId: '你的gitee凭证ID'
            }
        }

        stage('Maven打包后端微服务') {
            steps {
                sh '''
                    cd backend
                    mvn clean package -Dmaven.test.skip=true
                '''
            }
        }

        stage('Docker Compose 构建+启动全部服务') {
            steps {
                sh '''
                    # 工作目录是仓库根目录，直接读取docker-compose.yml
                    docker-compose down
                    docker-compose up -d --build
                '''
            }
        }
    }
    post {
        success {
            echo "✅ 所有微服务部署成功！"
        }
        failure {
            echo "❌ 流水线执行失败，请查看控制台日志"
        }
    }
}