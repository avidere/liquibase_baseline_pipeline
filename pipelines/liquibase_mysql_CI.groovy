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
        ),
        [
        $class: 'CascadeChoiceParameter',
        choiceType: 'PT_SINGLE_SELECT',
        description: 'Select repository',
        name: 'REPOSITORY_NAME',
        referencedParameters: 'PROJECT_KEY', 
        script: [
             $class: 'GroovyScript',
             script: [
                 sandbox: true,
                 script: """
                        import groovy.json.JsonSlurper
                        import jenkins.model.*
                        import com.cloudbees.plugins.credentials.CredentialsProvider
                        import com.cloudbees.plugins.credentials.common.StandardCredentials
    
                        def githubUsername = "avidere"
                        def credentialId = "git-token"
                        def githubToken = CredentialsProvider.lookupCredentials(
                            StandardCredentials.class,
                            Jenkins.instance,
                            null,
                            null
                        ).find { c ->
                            c.id == credentialId && c.metaClass.respondsTo(c, 'getSecret')
                        }?.getSecret()?.getPlainText()
                        def apiUrl = "https://api.github.com/users/${githubUsername}/repos"
                        def response = "curl -s -H 'Authorization: token ${githubToken}' ${apiUrl}".execute().text
                        def json = new JsonSlurper().parseText(response)
                        json.collect { it.name }

                    """
                ]
            ]
        ],

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
