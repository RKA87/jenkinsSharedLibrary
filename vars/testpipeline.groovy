def call (Map configMap) {
    pipeline {
        agent {
            node {label 'roboshop'}
        }
        // environment {
        //     proj = 'roboshop'
        //     env = 'dev'
        //     deployment = 'jenkins-shared-library'
        // }
        options {
            // disableConcurrentBuilds()
            timeout(time:10, unit:'MINUTES')
        }
        // parameters {
        //     // using for Buildwith Parameters in Jenkins
        // }
        stages {
            stage ('testing') {
                steps {
                    script {
                        sh """
                            echo "Testing using Shared Library"
                            echo "project is from ${configMap.project} of ${configMap.component} microservice"
                        """
                    }
                }
            }
        }
        post {
            always {
                echo "checking pipeline after stages"
            }
            success {
                echo "pipeline success"
            }
            failure {
                echo "pipeline failure"
            }
        }
    }
}