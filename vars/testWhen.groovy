#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  pipeline {

    agent any 

    parameters { booleanParam(name: 'buildDocker', defaultValue: false, description: '') }

    stages {
      stage('Prep') {
        steps {
          script {
            currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}
            if ( config.buildDocker ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/) {
               def Boolean doDocker = true
               echo "doDocker equals $doDocker"
            }
          }
          echo "buildDocker is: $config.buildDocker"
        
        }
      }

      stage('test') {
         when {
           expression { return doDocker || false }
         }
        steps {
           echo "It's true!"
        }
      }
    }
    
  } // end pipeline
 
} 

