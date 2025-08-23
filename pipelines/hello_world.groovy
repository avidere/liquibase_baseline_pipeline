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
            defaultValue: 'liquibase_actions',
            description: 'Repository name for the Liquibase project'
        ),
        string(
            name: 'BRANCH_NAME', 
            defaultValue: 'master', 
            description: 'Branch name to checkout'
        ),
    ])
])
pipeline {
    environment {
        flowfiledeployment = 'liquibase-ci.flowfile.yaml'
    }
    agent any
    stages {
        stage('clean workspace') {
            steps {
                script {
                    cleanWs()
                    codeCheckout()
                }
            }
        }
        stage('Liquibase Execution') {
            steps {
                script {
                    liquibaseFlow.appci(flowfiledeployment)
                }
            }
        }
    }
    post {
            always {
                echo 'Pipeline completed.'
            }
        success {
            echo 'Pipeline succeeded.'
        }
        failure {
            echo 'executing rollback due to failure'

            script {
                sh 'liquibase rollback tag=rollback_tagversion_1.3'
            }
        }
    }
}
