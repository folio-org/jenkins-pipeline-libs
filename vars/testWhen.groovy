#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  pipeline {

    agent any 

    stages {
      stage('test') {
        when {
          branch 'master'
        }
        steps {
          echo "Building branch 'master'..."  
          echo "Branch is: $env.BRANCH_NAME"
        }
      }
    }
    
  } // end pipeline
 
} 

