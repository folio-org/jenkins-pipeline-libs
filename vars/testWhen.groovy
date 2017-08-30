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
          echo "Branch is: $env.BRANCH"
          echo "DOCKER is: $env.DOCKER"
        
        }
      }

      stage('test') {
         when {
           // environment name: 'DOCKER', value: 'yes'  
           branch 'master'
         }
        steps {
           echo "DOCKER again is: $env.DOCKER"
           echo "It's true!"
           echo "Branch is: $env.BRANCH_NAME"
        }
      }
    }
    
  } // end pipeline
 
} 

