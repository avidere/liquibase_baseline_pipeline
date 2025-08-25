@Library(['liquibase_shared_library@main', 'liquibase_devops_controls@main']) _
import java.text.SimpleDateFormat
loadEnvVars()
devopsControls()

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
        string(
            name: 'CHANELOG_FILE',
            defaultValue: 'changelog/changelog.xml',
            description: 'Changelog file for Liquibase'
        ),
        choice(
            name: 'ENVIRONMENT',
            choices: ['dev', 'qa', 'prod'],
            description: 'Select the environment to deploy to'
        )
    ])
])
pipeline {

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
        stage('Liquibase Update Cassandra') {
            steps {
                   bat"""
                   
                    liquibase update

                   """
                
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
                bat 'liquibase rollback tag=rollback_tagversion_1.3'
            }
        }
    }
}
