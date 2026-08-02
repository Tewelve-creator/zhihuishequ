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
                sh 'mvn clean package -Dmaven.test.skip=true'
            }
        }
        stage('启动服务') {
            steps {
                sh 'docker compose down && docker compose up -d --build'
            }
        }
    }
}