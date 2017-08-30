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
            if ( config.buildDocker == 'yes')  {
               def Boolean doDocker = true
               echo "doDocker equals $doDocker"
            }
          }
          echo "buildDocker is: $config.buildDocker"
        
        }
      }

      stage('test') {
         when {
           expression {  
             config.buildDocker ==~ /(yes|true)/  
           }
         }
        steps {
           echo "It's true!"
        }
      }
    }
    
  } // end pipeline
 
} 

