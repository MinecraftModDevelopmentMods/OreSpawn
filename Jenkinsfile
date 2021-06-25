pipeline {
    agent any
    environment {
        GRADLE_OPTS = '-Dorg.gradle.caching=true -Dorg.gradle.configureondemand=true -Dorg.gradle.warning.mode=all'
//      JAVA_OPTS = ''
    }
    options {
        ansiColor('xterm')
    }
    tools {
//      git 'Git'
        gradle 'Gradle 4.9'
        jdk 'oraclejdk8'
    }
    stages {
        stage('prebuild') {
            steps {
                sh 'rm -rf build/libs'
                sh 'chmod +x gradlew'
                sh 'java -version'
                sh 'gradle -version'
                sh './gradlew -version'
                sh 'export'
            }
        }
        stage('CIWorkspace') {
            steps {
                withGradle {
                    sh './gradlew clean setupCiWorkspace -S'
                }
            }
        }
        stage('build') {
            steps {
                withGradle {
                    sh './gradlew build -S'
                }
            }
        }
        stage('test') {
            steps {
                withGradle {
                    sh './gradlew test -S'
                }
            }
        }
        stage('publish') {
            steps {
                withCredentials([file(credentialsId: 'secret.json', variable: 'SECRET_FILE')]) {
                    withGradle {
                        sh './gradlew publish -S'
                    }
                }
            }
        }
        stage('CurseForge') {
            steps {
                withCredentials([file(credentialsId: 'secret.json', variable: 'SECRET_FILE')]) {
                    withGradle {
                        sh './gradlew -x publish curseforge -S'
                    }
                }
            }
        }
        stage('SonarQube') {
            tools {
                jdk "oraclejdk11"
            }
            environment {
                scannerHome = tool 'SonarQube'
            }
            steps {
//              withCredentials([file(credentialsId: 'secret.json', variable: 'SECRET_FILE')]) {
//                  withGradle {
//                      sh './gradlew sonarqube -S'
//                  }
//              }
                withSonarQubeEnv(installationName: 'SonarCloud', , envOnly: false) {
                    sh "${scannerHome}/bin/sonar-scanner -Dsonar.java.jdkHome=${JAVA_HOME}"
                }
            }
        }
        stage('postbuild') {
            steps {
                archiveArtifacts artifacts: 'build/libs/*.jar', followSymlinks: false
                javadoc javadocDir: 'build/docs/javadoc', keepAll: false
                fingerprint 'build/libs/*.zip'
                junit allowEmptyResults: true, testResults: '**/build/test-results/junit-platform/*.xml'
                jacoco classPattern: '**/build/classes/java', execPattern: '**/build/jacoco/**.exec', sourceInclusionPattern: '**/*.java', sourcePattern: '**/src/main/java'
                findBuildScans()
                recordIssues(tools: [java()])
                recordIssues(tools: [javaDoc()])
//              if (fileExists('')) {
//                  recordIssues(tools: [errorProne(pattern: 'ReportFilePattern', reportEncoding: 'UTF-8')])
//              } else {
//                  echo 'No ErrorProne report available'
//              }
                if (fileExists('**/build/reports/checkstyle/*.xml')) {
                    recordIssues(tools: [checkStyle(pattern: '**/build/reports/checkstyle/*.xml')])
                } else {
                    echo 'No CheckStyle report available'
                }
                if (fileExists('**/build/reports/pmd/*.xml')) {
                    recordIssues(tools: [pmdParser(pattern: '**/build/reports/pmd/*.xml')])
                } else {
                    echo 'No PMD report available'
                }
                if (fileExists('*/build/reports/findbugs/*.xml')) {
                    recordIssues(tools: [findBugs(pattern: '*/build/reports/findbugs/*.xml', useRankAsPriority: true)])
                } else {
                    echo 'No FindBugs report available'
                }
            }
            when { expression { fileExists('**/build/reports/spotbugs/*.xml') } }
            steps {
                recordIssues(tools: [spotBugs(pattern: '**/build/reports/spotbugs/*.xml', useRankAsPriority: true)])
            }
            when { expression { fileExists('**/build/test-results/junit-platform/*.xml') } }
            steps {
                recordIssues(tools: [junitParser(pattern: '**/build/test-results/junit-platform/*.xml')])
            }
            when { expression { fileExists('**/sonar-report.json') } }
            steps {
                recordIssues(tools: [sonarQube(pattern: '**/sonar-report.json')])
            }
        }
    }
}
