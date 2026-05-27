def call(Map configMap) {
    pipeline {
        agent {
            node {
                label 'roboshop'
            }
        }
        environment {
            // declare the variables to use in this pipeline 
            // at the sametime denote the variables to use it from Main pipeline execution with configMap.<variable_name>
            awsAccId = "381491827632"
            awsRegion = "us-east-1"
            appVersion = ""
            appProject = "${configMap.appProject}"
            appName = "${configMap.appName}"
        }
        parameters {
            booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Toogle this Value to Deploy')
        }
        options {
            timeout(time:15, unit:"MINUTES")
        }
        stages {
            stage('readApp version') {
                steps {
                    script {
                        // load and parse the json

                        def data = readJSON file: 'package.json'
                        
                        appVersion = data.version

                        echo "ApplicationName: ${configMap.appName} Version: ${appVersion}"

                        echo "Building application version ${appVersion}"
                    }
                }
            }
            stage('dependencies install') {
                steps {
                    script {
                        sh """
                            npm install
                        """
                    }
                }
            }
            // stage('unit test') {
            //     steps {
            //         script {
            //             // these tests will run using Jest testing framework
            //             sh """
            //                 npm test
            //             """
            //         }
            //     }
            // }
            // stage('sonarQube analysis') {
            //     steps {
            //         script {
            //             def scannerHome = tool name: 'sonar-8' // agent configuration
            //             withSonarQubeEnv('sonarqube-instance') { // analysing and uploading to server
            //                 sh "${scannerHome}/bin/sonar-scanner"
            //             }
            //         }
            //     }
            // }
            // stage('qualityGate analysis') {
            //     steps {
            //         script {
            //             timeout(time:5, unit:"MINUTES"){
            //                 waitForQualityGate abortPipeline: true
            //             }
            //         }
            //     }
            // }
            stage('dependabotAlerts scan') {
                steps {
                    script {
                        echo "Fetching Dependabot Scan Alerts"

                        withCredentials([string(credentialsId: 'github-dependabot-scan-token', variable: 'GITHUB_TOKEN')]) {
                            def repoUrl = sh(
                                script: "git remote get-url origin",
                                returnStdout: true
                            ).trim()
                            def repoPath = repoUrl.replaceAll(/.*github\.com[\/:]/, '').replaceAll(/\.git$/, '') 

                            def response = sh (
                                script: """
                                    curl -L \
                                        -H "Accept: application/vnd.github+json" \
                                        -H "Authorization: Bearer ${GITHUB_TOKEN}" \
                                        -H "X-GitHub-Api-Version: 2022-11-28" \
                                        "https://api.github.com/repos/${repoPath}/dependabot/alerts?state=open&per_page=100"
                                """,
                                returnStdout: true
                            ).trim()
                            def alerts = readJSON file: response
                            echo "Found ${alerts.szie()} dependabot alerts"
                        }
                    }
                }
            }
            stage('build image') {
                steps {
                    script {
                        sh """
                            docker build -t ${env.awsAccId}.dkr.ecr.${env.awsRegion}.amazonaws.com/${appProject}/${appName}:${appVersion} .
                        """
                    }
                }
            }
            stage('trivy imageScan') {
                steps {
                    script {
                        def scanResult = sh (
                            script: """
                                tryivy image \
                                    --scanners vuln \
                                    --pkg-type os \
                                    --severity HIGH, MEDIUM \
                                    --format table \
                                    --exit-code 1 \
                                    --quiet
                                    ${env.awsAccId}.dkr.ecr.${env.awsRegion}.amazonaws.com/${appProject}/${appName}:${appVersion}
                            """,
                            returnStatus: true
                        ).trim()
                    }
                }
            }
            stage('trivy dockerFileScan') {
                steps {
                    script {
                        def dockerScanResult = sh (
                            script: """
                                trivy config \
                                --severity HIGH, MEDIUM \
                                --exit-code 1 \
                                --format table \
                                Dockerfile
                            """,
                            returnStatus: true
                        ).trim()
                    }
                }
            }
            stage('pushImage ecrRepo') {
                steps {
                    withAWS(credentials: 'aws-creds', region:"${env.awsRegion}") {
                        script {
                            sh """
                                aws ecr get-login-password --region ${env.awsRegion}| docker login --username AWS --password-stdin ${env.awsAccId}.dkr.ecr.${env.awsRegion}.amazonaws.com
                                docker push ${env.awsAccId}.dkr.ecr.${env.awsRegion}.amazonaws.com/${appProject}/${appName}:${appVersion}
                            """
                        }                        
                    }
                }
            }
        }
        post {
            always {
                echo "perform final post checks"
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