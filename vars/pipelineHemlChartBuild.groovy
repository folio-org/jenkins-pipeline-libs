def call() {
  pipeline {
    agent { label 'jenkins-agent-java11' }

    options {
      disableConcurrentBuilds()
    }

    stages {
      stage('SCM') {
        steps {
          prepareScm()
        }
      }

//      stage('Prepare ENV') {
//        steps {
//          withCredentials([
//            usernamePassword(credentialsId: 'nexus_pull', usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')
//          ]) {
//            prepareSemanticGradleEnvironment()
//          }
//        }
//      }

      stage('Build') {
        steps {
          script {
            sh "gradle build"
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
