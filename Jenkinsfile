pipeline {
    agent any
    stages {
        stage('拉取代码') {
            steps {
                git url: 'https://gitee.com/zhihuo_1/zhihuishequ.git',
                    branch: 'master'
            }
        }

        stage('Maven打包') {
            steps {
                sh '''
/var/jenkins_home/apache-maven-3.9.9/bin/mvn \
-s /var/jenkins_home/apache-maven-3.9.9/conf/settings.xml \
-B clean package -Dmaven.test.skip=true
                '''
            }
        }

        stage('构建&启动容器') {
            steps {
                sh '''
docker build -t zhihuishequ .
docker stop zhihuishequ || true
docker rm zhihuishequ || true
docker run -d --name zhihuishequ -p 8090:8090 zhihuishequ
                '''
            }
        }
    }
    post {
        success {
            echo '✅ 部署成功'
        }
        failure {
            echo '❌ 构建失败，请查看日志'
        }
    }
}