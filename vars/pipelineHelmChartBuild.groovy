def call() {
  pipeline {
    agent { label 'jenkins-agent-java11' }

    options {
      disableConcurrentBuilds()
    }

    stages {
//      stage('Prepare ENV') {
//        steps {
//          withCredentials([
//            usernamePassword(credentialsId: 'nexus_pull', usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')
//          ]) {
//            prepareSemanticGradleEnvironment()
//          }
//        }
//      }

      stage("Configure environment") {
        steps {
          script {
            sh "curl -LSs https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 | bash -s -- --version v3.8.0"
          }
        }
      }

      stage('Build') {
        steps {
          script {
            sh "./gradlew build -PhelmRegistryUrl=url -PhelmRegistryUsername=user -PhelmRegistryPassword=pass"
          }
        }
        post {
          always {
            archiveArtifacts 'build/helm/charts/*.tgz'
          }
        }
      }

//      stage("Publish all artifacts") {
//        steps {
//          script {
//            additionalParams = "-Phelm.executable=/usr/local/bin/helm3"
//            if (BRANCH_NAME == 'master') {
//              runGradleCmd("release publish syncBack ${additionalParams} " +
//                "-PprojectVersion=${semanticVersion.timestampVersion}")
//            } else {
//              runGradleCmd("release publish ${additionalParams} " +
//                "-PprojectVersion=${semanticVersion.timestampVersion}")
//            }
//            currentBuild.description = "--- helm charts ---\n"
//            findFiles(glob: '**/helm/charts/*.tgz').each { file ->
//              currentBuild.description += "${file.name}\n"
//            }
//          }
//        }
//      }
    }
  }
}
