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
          }
          echo "buildDocker is: $config.buildDocker"
          script {
            def boolean doDocker = config.buildDocker
          }
        
        }
      }

      stage('test') {
         when {
           expression { return doDocker ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
         }
        steps {
           echo "It's true!"
        }
      }
    }
    
  } // end pipeline
 
} 

