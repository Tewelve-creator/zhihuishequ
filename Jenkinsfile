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
                    # 这里替换成真实项目目录
                    cd zhihuishequ-admin
                    mvn clean package -Dmaven.test.skip=true
                '''
            }
        }
        stage('启动服务') {
            steps {
                sh '''
                    cd zhihuishequ-admin
                    # 后续写docker构建、重启容器命令
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