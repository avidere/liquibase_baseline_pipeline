@Library(['liquibase_shared_library@main']) _
import java.text.SimpleDateFormat

loadEnvVars()

properties([
    parameters([
        string(
            description: 'Please Enter Request/Jira Number Example: REQ0010001',
            name: 'REQUEST_NUMBER',
            trim: true
        ),
        choice(
            choices: ['dev', 'qa', 'prod'],
            description: 'Select Environment Value to proceed.',
            name: 'ENVIRONMENT'
        ),
        activeChoice(
            choiceType: 'PT_RADIO',
            description: 'Select Request type to proceed',
            filterLength: 1,
            filterable: false,
            name: 'REQUEST_TYPE',
            randomName: 'choice-parameter-20276184842354',
            script:
            groovyScript(
                fallbackScript: [
                    classpath: [],
                    oldScript: '',
                    sandbox: false,
                    script:
                    'return[\'ERROR\']'
                ],
                script: [
                    classpath: [],
                    oldScript: '',
                    sandbox: false,
                    script:
                    'return[\'DEPLOYMENT\',\'VALIDATION\']'
                ]
            )
        ),
        stashedFile(
            description: 'Upload Script file with .sql extension for execution',
            name: 'FILE'
        ),
        string(
            description: 'Enter a valid Database Schema Name to proceed',
            name: 'DB_NAME',
            trim: true
        ),
        reactiveChoice(
            choiceType: 'PT_CHECKBOX',
            description: 'Select ACCESS_REQUEST filed if change is related to Access Request.',
            filterLength: 1,
            filterable: false,
            name: 'ACCESS_REQUEST',
            randomName: 'choice-parameter-20276385234469',
            referencedParameters: 'REQUEST_TYPE',
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
                    if (REQUEST_TYPE == \'DEPLOYMENT\') {
                        return [\'true\']
                    } else {
                        return [\'false:disabled\']
                    }
                    '''
                ]
            )
        ),
        activeChoiceHtml(
            choiceType: 'ET_FORMATTED_HTML',
            description: 'Provide the USER ID for which Access change is requested',
            name: 'USER_ID',
            omitValueField: true,
            randomName: 'choice-parameter-17220894630277',
            referencedParameters: 'ACCESS_REQUEST',
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
                            if (ACCESS_REQUEST) {
                                return """<input type="text" class="setting-input" name="value">"""
                            } else {
                                return """<input type="hidden" class="setting-input" name="value" vale="USER ID is not required">"""
                            }
                    '''
                    ]
                )
            ),
        reactiveChoice(
            choiceType: 'PT_SINGLE_SELECT',
            description: 'Select the type of Access Request',
            filterLength: 1,
            filterable: false,
            name: 'ACCESS_TYPE',
            randomName: 'choice-parameter-20276570046991',
            referencedParameters: 'ACCESS_REQUEST',
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
                    if (ACCESS_REQUEST) {
                        return [\'GRANT\', \'REVOKE\', \'LOCK\']
                    } else {
                        return [ \'NotRequired:disabled\']
                    }
                    '''
                ]
            )
        ),
        string(
            description: 'Please Select Changelogfile',
            name: 'CHANGE_LOG',
            defaultValue: 'root-changelog.xml',
            trim: true
        )
    ])
])
pipeline {
        environment {
            vault_ns = 'admin'
            VAULT_TOKEN = vaultOperations.generateToken('admin')
            LIQUIBASE_LICENSE_KEY = credentials('liquibaselicensekey')
            liquibaseupdate = 'liquibase-dba.flowfile.yaml'
            liquibasevalidate = 'liquibase-dba-validate.flowfile.yaml'
            DBType = 'Oracle'
            Tag = "${PROJECT_KEY}_${BUILD_NUMBER}"
        }
    agent any
    stages {
        stage('Input Validation') {
            steps {
                script {
                    inputValidation.dba()
                }
            }
        }
        stage('clean workspace') {
            steps {
                script {
                    cleanWs()
                }
            }
        }
        stage('setup Jenkins workspace') {
            steps {
                script {
                    workspaceSetup()
                }
            }
        }
        stage('Quality-Checks Validation') {
            steps {
                script {
                    if (params.REQUEST_TYPE == 'DEPLOYMENT') {
                        echo 'proceeding with checks output validation'
                        qualityChecksValidation.dba()
                    }
                }
            }
        }
        stage('Liquibase Execution') {
            steps {
                script {
                    if (params.REQUEST_TYPE == 'DEPLOYMENT') {
                        echo 'proceeding with deployment request'
                        liquibaseFlow.dba(liquibaseupdate)
                    } else {
                        echo 'proceeding with validation request'
                        liquibaseFlow.dba(liquibasevalidate)
                    }
                }
            }
        }
        stage('upload Artifact') {
            steps {
                script {
                    createArtifact.dba()
                    uploadArtifact.dba()
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
