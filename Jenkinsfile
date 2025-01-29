// allow build to publish build scans to develocity-staging.eclipse.org
def secrets = [
  [path: 'cbi/tools.buildship/develocity.eclipse.org', secretValues: [
    [envVar: 'DEVELOCITY_ACCESS_KEY', vaultKey: 'api-token']
    ]
  ]
]


pipeline {
    agent any

    tools {
        jdk 'temurin-jdk11-latest'
    }

    environment {
        CI = "true"
    }

     triggers {
        githubPush()
    }
    
     stages {
        stage('Sanity check') {
            steps {
                withVault([vaultSecrets: secrets]) {
                    sh './gradlew assemble checkstyleMain -Pbuild.invoker=CI --info --stacktrace'
                }
            }
        }

        stage('Basic test coverage') {
            parallel {
                stage('Linux - Eclipse 4.8 - Java 8') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean eclipseTest -Peclipse.version=48 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                    }
                }
                stage('Linux - Eclipse 4.34 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean eclipseTest -Peclipse.version=434 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
                // stage('Windows - Eclipse 4.8 - Java 8') {
                //     agent { label 'windows' }
                //     steps {
                //         sh './gradlew clean eclipseTest -Peclipse.version=48 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                //     }
                // }
                // stage('Windows - Eclipse 4.34 - Java 17') {
                //     agent { label 'windows' }
                //     steps {
                //         sh './gradlew clean eclipseTest -Peclipse.version=434 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                //     }
                // }
            }
        }

        stage('Full test coverage') {
            parallel {
                stage('Linux - Eclipse 4.8 - Java 8') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=48 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                    }
                }
                stage('Linux - Eclipse 4.9 - Java 8') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=49 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                    }
                }
                stage('Linux - Eclipse 4.10 - Java 8') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=410 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                    }
                }
                stage('Linux - Eclipse 4.11 - Java 8') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=411 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                    }
                }
                stage('Linux - Eclipse 4.12 - Java 8') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=412 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                    }
                }
                stage('Linux - Eclipse 4.13 - Java 8') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=413 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                    }
                }
                stage('Linux - Eclipse 4.14 - Java 8') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=414 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                    }
                }
                stage('Linux - Eclipse 4.15 - Java 8') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=415 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                    }
                }
                stage('Linux - Eclipse 4.16 - Java 8') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=416 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                    }
                }
                stage('Linux - Eclipse 4.17 - Java 11') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=417 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=11'
                    }
                }
                stage('Linux - Eclipse 4.18 - Java 11') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=418 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=11'
                    }
                }
                stage('Linux - Eclipse 4.179 - Java 11') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=419 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=11'
                    }
                }
                stage('Linux - Eclipse 4.20 - Java 11') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=420 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=11'
                    }
                }
                stage('Linux - Eclipse 4.21 - Java 11') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=421 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=11'
                    }
                }
                stage('Linux - Eclipse 4.22 - Java 11') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=422 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=11'
                    }
                }
                stage('Linux - Eclipse 4.23 - Java 11') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=423 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=11'
                    }
                }
                stage('Linux - Eclipse 4.24 - Java 11') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=424 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=11'
                    }
                }
                stage('Linux - Eclipse 4.25 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=425 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
                stage('Linux - Eclipse 4.26 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=426 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
                stage('Linux - Eclipse 4.27 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=427 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
                stage('Linux - Eclipse 4.28 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=428 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
                stage('Linux - Eclipse 4.29 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=429 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
                stage('Linux - Eclipse 4.30 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=430 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
                stage('Linux - Eclipse 4.31 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=431 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
                stage('Linux - Eclipse 4.32 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=432 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
                stage('Linux - Eclipse 4.33 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=433 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
                stage('Linux - Eclipse 4.34 - Java 17') {
                    //agent { label 'linux' }
                    steps {
                        sh './gradlew clean build -Peclipse.version=434 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                    }
                }
               
                // stage('Windows - Eclipse 4.8 - Java 8') {
                //     agent { label 'windows' }
                //     steps {
                //         sh './gradlew clean build -Peclipse.version=48 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=8'
                //     }
                // }
                // stage('Windows - Eclipse 4.34 - Java 17') {
                //     agent { label 'windows' }
                //     steps {
                //         sh './gradlew clean build -Peclipse.version=434 -Pbuild.invoker=CI --info --stacktrace -Peclipse.test.java.version=17'
                //     }
                // }
            }
        }
    }
}