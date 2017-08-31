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
        //sendNotifications 'STARTED'

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
      }

      stage('Maven Build') {
        def mvn_artifact = readMavenPom().getArtifactId() 
        def mvn_version =  readMavenPom().getVersion()
        env.name = "$mvn_artifact"

        if (mvn_version ==~ /-SNAPSHOT/) {
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

          //sh 'mvn integration-test'
          sh 'mvn -DskipTests integration-test'

        }
      }

      //if ( env.BRANCH_NAME == 'master' ) {    
      if ( env.BRANCH_NAME == 'malc-test' ) {    

        if ( config.doDocker ==~ /(?i)(Y|YES|T|TRUE)/ ) {
          stage('Docker') {
            echo "Building Docker image $env.name:$env.version" 
            buildModDockerImage("$env.name","$env.version") 
          }
          stage('Docker Publish') {
            echo "Publishing Docker"
          }
        } 
        if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish Module Descriptor') {
            echo "Publishing Module Descriptor"
          }
        }
        if (config.publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish API Docs') {
          echo "Publishing API docs"
          }
        }
      } 
    } // end try
    catch (Exception err) {
      currentBuild.result = 'FAILED'
      println(err.getMessage());
      echo "Build Result: $currentBuild.result"
      throw err
    
    }
    finally {
      echo "Send some notifications"
      echo "Do some cleanup"
      
    }
  } //end node
    
} 

