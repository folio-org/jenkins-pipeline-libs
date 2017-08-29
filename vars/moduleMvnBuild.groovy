#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  pipeline {

    environment {
      mvn_artifact = readMavenPom().getArtifactId()
      mvn_version  = readMavenPom().getVersion()
      snapshot_version = "${env.mvn_version}.{env.BUILD_NUMBER}"
    }

    agent {
      node {
        label 'folio-jenkins-slave-docker'
      }
    }

    stages {
      stage('Prep') {
        steps {
          script {
            currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
          }
        sendNotifications 'STARTED'
        }
      }

      stage('Mvn Build') {
        steps {
          withMaven(jdk: 'OpenJDK 8 on Ubuntu Docker Slave Node',
                    maven: 'Maven on Ubuntu Docker Slave Node',
                    options: [junitPublisher(disabled: false,
                    ignoreAttachments: false),
                    artifactsPublisher(disabled: false)]) {

            sh 'mvn -DskipTests integration-test'
          }
        }
      }

      stage('Build Docker Image') {
        steps {
          echo "Building Docker Image: ${config.dockerImage}:${env.snapshot_version}"
          sh """
            cat > .dockerignore <<EOF
*
!Dockerfile
!docker
!target/*.jar
EOF
          """
          sh 'cat .dockerignore'
   
        }
      }

    } // end stages

  } // end pipeline
 
} 

  
