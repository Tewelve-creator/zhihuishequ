pipeline {
    agent any
    stages {
        stage('代码拉取') {
            steps {
                echo "Git自动拉取代码"
            }
        }
        stage('编译打包所有微服务') {
            steps {
                sh 'mvn clean package -Dmaven.test.skip=true'
            }
        }
        stage('构建镜像+启动所有微服务') {
            steps {
                sh '''
                # 重启全部容器
                docker-compose down
                docker-compose up -d --build
                '''
            }
        }
    }
    post {
        success {
            echo """
部署完成！
网关地址：http://82.156.66.83:8080
认证服务：8081
SaaS租户服务：8082
报修服务：8083
            """
        }
    }
}