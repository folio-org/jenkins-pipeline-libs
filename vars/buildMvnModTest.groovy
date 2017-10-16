#!/usr/bin/groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node('folio-jenkins-slave-docker') {

    try {
      stage('Checkout') {
        deleteDir()
        currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
        sendNotifications 'STARTED'

         checkout([
                 $class: 'GitSCM',
                 branches: scm.branches,
                 extensions: scm.extensions + [[$class: 'SubmoduleOption',
                                                       disableSubmodules: false,
                                                       parentCredentials: false,
                                                       recursiveSubmodules: true,
                                                       reference: '',
                                                       trackingSubmodules: false]],
                 userRemoteConfigs: scm.userRemoteConfigs
         ])

         echo "Checked out $env.BRANCH_NAME"

        // project name is different from mod name specified in package.json
        def proj_name = sh(returnStdout: true, script: 'git config remote.origin.url | awk -F \'/\' \'{print $5}\' | sed -e \'s/\\.git//\'').trim()
        env.project_name = proj_name
        echo "$env.project_name"

      }

      stage('Maven Build') {
        def mvn_artifact = readMavenPom().getArtifactId() 
        def mvn_version =  readMavenPom().getVersion()
        env.name = "$mvn_artifact"

        if (mvn_version ==~ /.*-SNAPSHOT$/) {
          echo "This is a snapshot"
          env.version = "${mvn_version}.${env.BUILD_NUMBER}"
        }
        else {
          env.version = "$mvn_version"
        }

        echo "Building Maven artifact: ${env.name} Version: ${env.version}"
            
        withMaven(jdk: 'OpenJDK 8 on Ubuntu Docker Slave Node',
                    maven: 'Maven on Ubuntu Docker Slave Node',
                    options: [junitPublisher(disabled: false,
                    ignoreAttachments: false),
                    artifactsPublisher(disabled: false)]) {

          sh 'mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent -DskipTests install'

        }
      }

      if ( config.doDocker ==~ /(?i)(Y|YES|T|TRUE)/ ) {
        stage('Docker Build') {
          if (config.dockerDir != null) {
            dockerDir = config.dockerDir
          } 
          else 
            // default top-level directory
            dockerDir = env.WORKSPACE
          }
          echo "Building Docker image $env.name:$env.version" 
          buildJavaModDocker(env.name,env.version,dockerDir) 
      }
          
    } // end try
    catch (Exception err) {
      currentBuild.result = 'FAILED'
      println(err.getMessage());
      echo "Build Result: $currentBuild.result"
      throw err
    }
    finally {
      echo "Clean up any temporary docker artifacts"
      sh "docker rmi ${env.name}:${env.version} || exit 0"
      sh "docker rmi ${env.name}:latest || exit 0"
      sh "docker rmi ${env.repository}/${env.name}:${env.version} || exit 0"
      sh "docker rmi ${env.repository}/${env.name}:latest || exit 0"

    }

  } //end node
    
} 

