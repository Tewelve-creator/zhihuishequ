pipeline {
    agent any
    environment {
        SERVICE_NAME = "zhihuishequ"
    }
    stages {
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