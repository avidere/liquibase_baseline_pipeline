@Library('liquibase_shared_library') _
pipeline {
    agent any

    stages {
        stage('clean workspace') {
            steps {
                script {
                    echo 'Hello, World!'
                }
            }
        }
        stage('code checkout') {
            steps {
                script {
                    codeCkeckout()
                }
            }
        }
    }
    post {
        always {
            echo 'Pipeline completed.'
        }
    }
}
