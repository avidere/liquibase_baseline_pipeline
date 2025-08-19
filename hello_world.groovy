@Library('liquibase_shared_library')
import java.text.SimpleDateFormat
loadEnvVars()

properties([
    parameters([
        string(
            name: 'PROJECT_KEY', 
            defaultValue: 'avidere', 
            description: 'Project key for the Liquibase project'
        ),
        string(
            name: 'REPOSITORY_NAME',
            defaultValue: 'Ansible-Deployment',
            description: 'Repository name for the Liquibase project'
        ),
        string(
            name: 'BRANCH_NAME', 
            defaultValue: 'main', 
            description: 'Branch name to checkout'
        ),
    ])
])
pipeline {
    agent any

    stages {
        stage('clean workspace') {
            steps {
                script {
                    cleanWs()
                }
            }
        }
        stage('code checkout') {
            steps {
                script {
                    codeCheckout()
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
