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
        reactiveChoice(
            choiceType: 'PT_SINGLE_SELECT',
            description: 'Select Artifact group ',
            filterLength: 1,
            filterable: false,
            name: 'ARTIFACT_GROUP',
            randomName: 'choice-parameter-21971747249596',
            referencedParameters: 'ENVIRONMENT',
            script:
            groovyScript(
                fallbackScript: [
                    classpath: [],
                    oldScript: '',
                    sandbox: false,
                    script:
                    'return [\'ERROR\']'
                ],
                script: [
                    classpath: [],
                    oldScript: '',
                    sandbox: false,
                    script:
                    '''
                    if (ENVIRONMENT.equals(\'dev\')){
                        return [\'dev\',\'common\']
                    } else if (ENVIRONMENT.equals(\'qa\')){
                        return [\'qa\',\'common\']
                    }  else if (ENVIRONMENT.equals(\'prod\')){
                        return [\'prod\',\'common\']
                    }
                    '''
                ]
            )
        )
    ])
])
pipeline {
        environment {
            SCHEMA_NAME = "${schemaName}"
            LIQUIBASE_LICENSE_KEY = credentials('liquibaselicensekey')
            liquibasePropFile = 'config' + '/liquibase.properties'
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
        stage('Quality-Checks Validation') {
            steps {
                script {
                    def liquibaseconf = libraryResource "config/checks_conf/liquibase.${DBType}-checks-settings.conf"
                    writeFile file: "liquibase-checks-settings.conf", text: liquibaseconf
                    echo "Proceeding with Quality checks output validation"
                    qualityChecksValidation.app()
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
            echo 'Pipeline failed'

            script {
                    comment = sh(returnStdout: true, script: "echo \$(cat ${WORKSPACE}/${faileFile})")
                    CDSummaryFileToSN(comment.trim())

                    if ("${REQUEST_NUMBER}".startsWith('CHG') || "${REQUEST_NUMBER}".startsWith('RITM') || "${REQUEST_NUMBER}".startsWith('REQ')) {
                        ServiceNowUpdate()
                    } else (
                        jiraCommentUpdate()
                    )
            }
        }
    }
}
