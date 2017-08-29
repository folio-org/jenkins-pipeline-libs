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

      stage('Maven Build') {
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

      stage('Docker Build') {
        when {
          expression { $config.docker ==~ /(?i)(Y|YES|T|TRUE|ON|RUN)/ }
        }
        steps {
          echo "Building Docker Image: ${env.mvn_artifact}:${env.snapshot_version}"
          sh """
            cat > .dockerignore << EOF
*
!Dockerfile
!docker
!target/*.jar
EOF
          """
          sh "docker build -t ${env.mvn_artifact}:${env.snapshot_version} ."
          sh "docker tag ${env.mvn_artifact}:${env.snapshot_version} ${env.mvn_artifact}:latest"
        }
      }
  
      // stage('Docker Publish') {
      //  when { 
      // echo "Publishing Docker image ${env.docker_image}:${env.POM_VERSION} to Docker Hub..."

    } // end stages

    post {
      always {
        sh "docker rmi ${env.mvn_artifact}:${env.snapshot_version} || exit 0"
        sh "docker rmi ${env.mvn_artifact}:latest || exit 0"
      }
    }
    
  } // end pipeline
 
} 

