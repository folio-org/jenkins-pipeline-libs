#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  pipeline {
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
          echo "Building Docker Image: ${config.dockerImage}"
        }
      }

    } // end stages

  } // end pipeline
 
} 

  
