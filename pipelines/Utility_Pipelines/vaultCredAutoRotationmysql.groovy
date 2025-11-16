@Library(['liquibase_shared_library@main']) _
import java.text.SimpleDateFormat

properties([
    parameters([
        string(
            name: 'REQUEST_NUMBER',
            description: 'Please Enter Request/Jira Number Example: REQ0010001',
            trim: true
        ),
        string(
            name: 'APP_CIID',
            defaultValue: '503027034',
            description: 'Please Eneter APP CIID'
        ),string(
            name: 'AWS_ACCOUNT',
            defaultValue: '654654373515',
            description: 'Please Eneter AWS Account Number'
        ),
        
        string(
            name: 'DB_IDENTIFIER',
            defaultValue: '',
            description: 'Please Enter DB Identifier'
        ),
    
        string(
            name: 'GLOBAL_ENDPOINT',
            defaultValue: '',
            description: 'Please Enter Database Global Endpoint'
        ),
        string(
            name: 'PORT',
            defaultValue: '3306',
            description: 'Please Enter Database Global Endpoint'
        ),
        string(
            name: 'DB_NAME',
            defaultValue: '',
            description: 'Please Enter Database Name'
        ),
        string(
            name: 'DB_TYPE',
            defaultValue: 'aurora',
            description: 'Please Enter Database Type'
        )
    ])
])
pipeline {
    environment{
           VAULT_ADDR="https://demo-cluster-public-vault-c65b6d5f.1630fe0b.z1.hashicorp.cloud:8200"
           namespace="admin"
           VaultPath="secret/data/rds/mysqldev"
           
    }
    agent any
    stages {
        stage('clean workspace') {
            steps {
                script {
                    cleanWs()
                }
            }
        }
        stage('Fetch Master user creds') {
            steps {
                script {
                    env.VAULT_TOKEN=vaultOperations.generateToken("${namespace}")
                    def(master_user, master_pass)=vaultReadCreds.usernamePassword()
                    env.master_user="${master_user}"
                    env.master_pass="${master_pass}"
                }
            }
        }
        stage('DB Configuration') {
            steps {
                script{
                   vaultDbConfig()
                   
                }
            }
        }
        stage('Create Static Role') {
            steps {
                script{
                   vaultStaticRole.deploy()
                   vaultStaticRole.dare()
                   vaultStaticRole.dba()
                }
            }
        }
    }
}
