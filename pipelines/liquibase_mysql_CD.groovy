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
        string(
            name: 'REPOSITORY_NAME',
            defaultValue: 'liquibase_actions',
            description: 'Repository name for the Liquibase project'
        ),
        choice(
            name: 'ENVIRONMENT',
            choices: ['dev', 'qa', 'prod'],
            description: 'Select the environment to deploy to'
        ),
        choice(
            name: 'ARTIFACT_GROUP',
            choices: ['dev', 'qa', 'prod'],
            description: 'Select the artifact group (redundant maybe with GROUP)'
        ),
        string(
            name: 'CI_BUILD_NUMBER',
            defaultValue: '12',
            description: 'CI build number (artifact suffix)'
        ),
        reactiveChoice(
            choiceType: 'PT_SINGLE_SELECT',
            description: 'Select the Component_URL',
            filterLength: 1,
            filterable: true,
            name: 'Component_URL',
            referencedParameters: 'PROJECT_KEY,GROUP,CI_BUILD_NUMBER',
            script: groovyScript(
                fallbackScript:
                [
                    classpath: [],
                    oldScript: '',
                    sandbox: false,
                    script:
                    'return [\'ERROR\']'
                    ],
                script:
                [
                    classpath: [],
                    oldScript: '',
                    sandbox: false,
                    script:
                    '''
                            import groovy.json.JsonSlurper

                            def group = ARTIFACT_GROUP
                            def projectKey = PROJECT_KEY
                            def ciBuildNumber = CI_BUILD_NUMBER

                            def nexusHost = "http://nexus:8081"
                            def repository = "Liquibase-CICD"
                            def nexusUrl = "${nexusHost}/service/rest/v1/search?repository=${repository}"

                            def user = "admin"
                            def password = "admin123"
                            def authString = "${user}:${password}".bytes.encodeBase64().toString()

                            def artifactList = [] as List<String>

                            try {
                                def url = new URL(nexusUrl)
                                def connection = url.openConnection()
                                connection.setRequestProperty("Authorization", "Basic ${authString}")
                                connection.setRequestProperty("Accept", "application/json")

                                def response = connection.inputStream.text
                                def json = new JsonSlurper().parseText(response)

                                json.items.each { item ->
                                    item.assets.each { asset ->
                                        def path = asset.path
                                        if (
                                            path.contains("/${projectKey}/") &&
                                            path.contains("/${group}/") &&
                                            path.endsWith("-${ciBuildNumber}.zip")
                                        ) {
                                            def cleanPath = path.startsWith("/") ? path.substring(1) : path
                                            def fullUrl = "${nexusHost}/repository/${repository}/${cleanPath}"
                                            artifactList << fullUrl.toString()
                                        }
                                    }
                                }

                                if (artifactList.isEmpty()) {
                                    artifactList << "No artifact found for build ${ciBuildNumber}".toString()
                                }
                            } catch (Exception e) {
                                artifactList << "ERROR: ${e.message}".toString()
                            }

                            return artifactList
                        '''
                ]
            )
        )
    ])
])

pipeline {
    environment {
        LIQUIBASE_LICENSE_KEY = credentials('liquibaselicensekey')
        liquibasePropFile = 'Config' + '/liquibase.properties'
        liquibaseupdate = 'liquibase-cd.flowfile.yaml'
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
        stage('Clean workspace') {
            steps {
                script {
                    cleanWs()
                }
            }
        }
        stage('Download Artifact') {
            steps {
                script {
                    downloadArtifact()
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
        stage('Upload to Nexus') {
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

                if ("${REQUEST_NUMBER}".startsWith('CHG') ||
                    "${REQUEST_NUMBER}".startsWith('RITM') ||
                    "${REQUEST_NUMBER}".startsWith('REQ')) {
                    ServiceNowUpdate()
                } else {
                    jiraCommentUpdate()
                    }

                sh 'set +xv; cat liquibase.log'
                printf "************************************\n\\n Liquibase ${DBType} Output \\n\\n***********************\\n\\n"
                sh 'set +xv; cat output.txt'
            }
        }
        failure {
            echo 'Executing rollback due to failure'
            script {
                sh 'pipeline failed'
            }
        }
    }
}
