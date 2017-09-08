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

          //sh 'mvn integration-test'
          sh 'mvn -DskipTests package'

        }
      }

      //if ( env.BRANCH_NAME == 'master' ) {    
      if ( env.BRANCH_NAME == 'malc-test' ) {    

        if ( config.doDocker ==~ /(?i)(Y|YES|T|TRUE)/ ) {
          stage('Docker Build') {
            echo "Building Docker image $env.name:$env.version" 
            buildJavaModDocker("$env.name","$env.version") 
          }
          stage('Docker Publish') {
            echo "Publishing Docker"
          }
        } 
        if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish Module Descriptor') {
            if (fileExists('target/ModuleDescriptor.json')) {
              def modDescriptor = 'target/ModuleDescriptor.json'
              echo "Publishing Module Descriptor to FOLIO registry"
              postModuleDescriptor("$modDescriptor","$env.name","$env.version") 
            }
            else {
              // 
              echo "ModuleDescriptor.json not found"  
            }
          }
        }
        if (config.publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish API Docs') {
          echo "Publishing API docs"
            sh "python3 /usr/local/bin/generate_api_docs.py -r $env.name -v -o folio-api-docs"
            sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
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
      echo "Clean up any docker artifacts"
      sh "docker rmi ${env.name}:${env.version} || exit 0"
      sh "docker rmi ${env.name}:latest || exit 0"
      
    }
  } //end node
    
} 

