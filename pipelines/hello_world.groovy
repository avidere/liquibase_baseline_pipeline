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
        environment {
            VAULT_TOKEN="hvs.CAESIH1PWFhPNVnvW9q-Z7a72qKC1KBSFlkDe9QxtNF0VQKaGigKImh2cy5qSTYzSlNvU0ZLVmFOcFpqcUxFTng3UkQueDNqREEQmLsG"
        }
    agent any
    stages {
        stage('clean workspace') {
            steps {
                script {
                    cleanWs()
                    codeCheckout()
                    
                    def props = readFile('liquibase.properties')
                    props = props.replace('${dbUrl}', env.dbUrl)
                                 .replace('${username}', env.username)
                                 .replace('${password}', env.password)
                                 .replace('${vaultAddress}', env.vaultAddress)
                                 .replace('${vaultPath}', env.vaultPath)
                                 .replace('${vaultNS}', env.vaultNS)
                                 .replace('${driver}', env.driver)
                    writeFile(file: 'liquibase.properties', text: props)

                   
                }
            }
        }
        stage('Liquibase Execution') {
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
