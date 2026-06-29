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
        // https://github.com/eclipse-cbi/jiro/wiki/Tools-(JDK,-Maven,-Ant)#jdk
        jdk 'temurin-jdk21-latest'
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
                    sh './gradlew assemble checkstyleMain -Peclipse.version=436 -Pbuild.invoker=CI --info --stacktrace'
                }
                
            }
        }
    }
}