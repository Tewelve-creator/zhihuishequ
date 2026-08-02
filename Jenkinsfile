pipeline {
    agent any
    tools {
        maven 'Maven3.9'
    }
    stages {
        stage('拉取代码') {
            steps {
                git url: 'https://gitee.com/zhihuo_1/zhihuishequ.git', branch: 'master'
            }
        }
        stage('Maven打包') {
            steps {
                sh '''
                    cd backend
                    mvn clean package -Dmaven.test.skip=true
                '''
            }
        }
        stage('启动服务') {
            steps {
                sh '''
                    cd backend
                    # =====================
                    # 后续在这里补充docker构建镜像、停止旧容器、启动新容器命令
                    # =====================
                    echo "等待补充部署脚本"
                '''
            }
        }
    }
    post {
        always {
            echo '构建结束'
        }
    }
}