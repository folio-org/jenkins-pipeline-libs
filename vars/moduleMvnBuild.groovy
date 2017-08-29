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
          script {
            def mvn_artifact = readMavenPom().getArtifactId() 
            def mvn_version =  readMavenPom().getVersion()
            def snapshot_version = "${mvn_version}.${env.BUILD_NUMBER}" 

            env.mvn_artifact = mvn_artifact
            env.mvn_version = mvn_version
            env.snapshot_version = snapshot_version
          }
          echo "Building Maven artifact: ${env.mvn_artifact} Version: ${env.snapshot_version}"
            
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
          echo "Building Docker Image: ${config.dockerImage}:${snapshot_version}"
          sh """
            cat > .dockerignore << EOF
*
!Dockerfile
!docker
!target/*.jar
EOF
          """
          //sh "docker build -t ${config.dockerImage}:${snapshot_version} ."
          echo "${config.dockerImage}:${snapshot_version}"
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

  
