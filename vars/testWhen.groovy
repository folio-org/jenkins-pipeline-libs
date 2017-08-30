#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  pipeline {

    agent any 

    stages {
      stage('Prep') {
        steps {
          script {
            currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
            env.DOCKER = config.buildDocker
          }
          echo "buildDocker is: $config.buildDocker"
          echo "DOCKER is: $env.DOCKER"
        
        }
      }

      stage('test') {
         when {
           environment name: 'DOCKER', value: 'yes'  
         }
        steps {
           echo "It's true!"
        }
      }
    }
    
  } // end pipeline
 
} 

