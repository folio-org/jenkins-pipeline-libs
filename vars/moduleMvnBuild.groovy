#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  pipeline {

    environment {
      MVN_ARTIFACT = readMavenPom().getArtifactId()
      MVN_VERSION  = readMavenPom().getVersion()
      SNAPHOT_VERSION = "${env.MVN_VERSION}}.${env.BUILD_NUMBER}"
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
            echo "Building Maven artifact: ${env.MVN_ARTIFACT} Version: ${env.SNAPSHOT_VERSION}"
            sh 'mvn -DskipTests integration-test'
          }
        }
      }

      stage('Build Docker Image') {
        steps {
          echo "Building Docker Image: ${config.dockerImage}:${env.SNAPSHOT_VERSION}"
          sh """
            cat > .dockerignore << EOF
*
!Dockerfile
!docker
!target/*.jar
EOF
          """
          //sh "docker build -t ${config.dockerImage}:${snapshot_version} ."
          echo "${config.dockerImage}:${env.SNAPSHOT_VERSION}"
          //sh "docker tag ${config.dockerImage}:${snapshot_version} ${config.dockerImage}:latest"
        }
      }

    } // end stages

    post {
      always {
        sh "docker rmi $config.dockerImage:${snapshot_version} || exit 0"
        sh "docker rmi $config.dockerImage:latest || exit 0"
      }
    }
    
  } // end pipeline
 
} 

  
