@Library(['liquibase_shared_library@main', 'liquibase_devops_controls@main']) _
import java.text.SimpleDateFormat
loadEnvVars()
devopsControls()

properties([
    parameters([
        string(
            name: 'REQUEST_NUMBER',
            description: 'Please Enter Request/Jira Number Example: REQ0010001',
            trim: true
        ),
        string(
            name: 'PROJECT_KEY',
            defaultValue: 'avidere',
            description: 'Project key for the Liquibase project'
        ),string(
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
        environment {
            LIQUIBASE_LICENSE_KEY = credentials('liquibaselicensekey')
            liquibaseupdate = 'liquibase-ci.flowfile.yaml'
            VAULT_TOKEN = vaultOperations.generateToken('VaultNS')
            Tag = "${PROJECT_KEY}_${BUILD_NUMBER}"
        }
    agent any
    stages {
        stage('Input Validation') {
            steps {
                script {
                    inputValidation()
                }
            }
        }
        stage('clean workspace') {
            steps {
                script {
                    cleanWs()
                    codeCheckout()
                    sh '''
                    set +xv
                    envsubst < config/liquibase.properties > config/liquibase_temp.properties
                    mv config/liquibase_temp.properties config/liquibase.properties
                    '''
                }
            }
        }
        stage('Liquibase Execution') {
            steps {
                script{
                   liquibaseFlow.appci(liquibaseupdate)
                }
            }
        }
        stage("Create Artifact") {
            steps {
                script {
                    createArtifact()
                }
            }
        }
        stage("Tag Creation") {
            steps {
                script {
                    gitTagCreation()
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
                sh 'pipeline failed'
            }
        }
    }
}
