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
         //when {
           // environment name: 'DOCKER', value: 'yes'  
         //}
        steps {
           script {
              if (config.buildDocker ==~ /(yes|true)/) {
                 echo "buildDocker is: $config.buildDocker"
                 echo "It's 
                 echo "Branch is: $env.BRANCH_NAME"
              }
              else {
                echo "False: buildDocker is: $config.buildDocker" 
              }
           } 
        }
      }
    }
    
  } // end pipeline
 
} 

