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
        ),string(
            name: 'REPOSITORY_NAME',
            defaultValue: 'liquibase_actions',
            description: 'Repository name for the Liquibase project'
        ),
        [
            $class : 'CascadeChoiceParameter',
            choiceType: 'PT_SINGLE_SELECT',
            description: '<b><span style="color:orange;"> Select Branch for CI Build</span></b>'
            filterLength: 1, filterable: true,
            name: 'BRANCH',
            referencedParameters: 'REPOSITORY_NAME',
            script: [
                $class: 'GroovyScript',
                fallbackScript: [
                    classpath: [],
                    sandbox: false,
                    script: 'return ["ERROR"]'
                ],
                script: [
                    classpath: [],
                    sandbox: false,
                    script: '''
                        def gettags = ("git ls-remote -h ssh:git@github.com:${PROJECT_KEY}/${REPOSITORY_NAME}.git").execute()
                        x = gettags.text.readLines().collect { it.split()[1].replaceAll(\'refs/heads/\', \'\').replaceAll(\'refs/tags/\', \'\').replaceAll("\\\\^\\\\{\\\\}", \'\')}
                        def mylist = []
                        int number = 0
                        y = x.findAll { it.startsWith("main") || it.startsWith("master") || it.startsWith("release/") || it.startsWith("hotfix/") || it.startsWith("feature/") }
                        return y
                    '''
                ]
            ]
        ],

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
        stage('clean workspace') {
            steps {
                script {
                    cleanWs()
                    codeCheckout()
                    sh '''
                    set +xv
                    envsubst < liquibase.properties > liquibase_temp.properties
                    mv liquibase_temp.properties liquibase.properties
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
                sh 'liquibase rollback tag=${Tag} --defaultsFile=liquibase.properties'
            }
        }
    }
}
