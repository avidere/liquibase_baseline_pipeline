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
            description: 'Enter Bitbucket project key'
        ),string(
            name: 'REPOSITORY_NAME',
            defaultValue: 'liquibase_actions',
            description: 'Repository name for the Liquibase project'
        ),
        reactiveChoice(
            choiceType: 'PT_SINGLE_SELECT',
            description: 'select branch for for deployment',
            filterLength: 1, filterable: false,
            name: 'BRANCH_NAME',
            randomName: 'choice-parameter-6254404173953',
            referencedParameters: 'PROJECT_KEY,REPOSITORY_NAME',
            script:
            groovyScript(
                fallbackScript:
                [
                    classpath: [],
                    oldScript: '',
                    sandbox: true,
                    script:
                    'return[\'ERROR\']'
                ],
                script:
                [
                    classpath: [],
                    oldScript: '',
                    sandbox: true,
                    script:
                    '''
                    def PROJECT_KEY= "avidere"        // Replace with env/parameter if needed
                    def REPOSITORY_NAME = "liquibase_actions"  // Replace with env/parameter if needed

                    def command = "git ls-remote --heads git@github.com:${PROJECT_KEY}/${REPOSITORY_NAME}.git"
                    def proc = command.execute()
                    proc.waitFor()

                    def branches = []
                    if (proc.exitValue() == 0) {
                        branches = proc.in.text.readLines()
                            .collect { it.split()[1].replaceAll(\'refs/heads/\', \'\') }
                            .findAll { it.startsWith("develop") || it.startsWith("feature") || it.startsWith("bugfix") || it.startsWith("release") || it.startsWith("main") || it.startsWith("master") }
                    } else {
                        branches = ["Error: cannot fetch branches"]
                    }

                    return branches
                    '''
                ]
            )
        ),

        string(
            name: 'BRANCH_NAME',
            defaultValue: 'main',
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
        ),
        choice(
            name: 'ARTIFACT_GROUP',
            choices: ['dev', 'qa', 'prod'],
            description: 'Select the Artifact group'
        )
    ])
])
pipeline {
        environment {
            LIQUIBASE_LICENSE_KEY = credentials('liquibaselicensekey')
            liquibasePropFile = 'Config' + '/liquibase.properties'
            liquibaseupdate = 'liquibase-ci.flowfile.yaml'
            VAULT_TOKEN = vaultOperations.generateToken('VaultNS')
            PipelineType = 'CI'
            DBType = 'MySQL'
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
                    envsubst < config/liquibase.properties > liquibase_updated.properties
                    mv config/liquibase.properties liquibase.properties_from_source
                    mv liquibase_updated.properties config/liquibase.properties
                    '''
                }
            }
        }
        stage('Liquibase Execution') {
            steps {
                script {
                    liquibaseFlow.appci(liquibaseupdate)
                }
            }
        }
        stage('Create Artifact') {
            steps {
                script {
                    createArtifact()
                }
            }
        }
        stage('upload to nexus') {
            steps {
                script {
                    uploadArtifact.artifactupload()
                }
            }
        }
    }
    post {
            always {
                script {
                    StartComment = "Pipeline Deployment Summary \\n\\n Pipeline Name: $env.JOB_BASE_NAME\\n\\n$BUILD_TRIGGER_BY\\n\\n Pipeline URL : $env.BUILD_URL"
                    CDSummaryFileToSN(StartComment)
                }
            }
        success {
                script {
                    comment = sh(returnStdout: true, script: "echo \$(cat ${WORKSPACE}/${successFile})")
                    CDSummaryFileToSN(comment.trim())

                    if ("${REQUEST_NUMBER}".startsWith('CHG') || "${REQUEST_NUMBER}".startsWith('RITM') || "${REQUEST_NUMBER}".startsWith('REQ')) {
                        ServiceNowUpdate()
                    } else (
                        jiraCommentUpdate()
                    )

                    sh' set +xv;cat liquibase.log'
                    printf "************************************\n\n Liquibase ${DBType} Output \n\n***********************\n\n"
                    sh'set +xv;cat output.txt'
                }
        }
        failure {
            echo 'executing rollback due to failure'

            script {
                sh 'pipeline failed'
            }
        }
    }
}
