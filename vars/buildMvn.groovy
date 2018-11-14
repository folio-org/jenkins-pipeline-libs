#!/usr/bin/groovy

/*
 * Main build script for Maven-based FOLIO projects
 *
 * Configurable parameters: 
 *
 * doDocker:  Build, test, and publish Docker image via 'buildJavaDocker' (Default: 'no')
 * mvnDeploy: Deploy built artifacts to Maven repository (Default: 'no')
 * publishModDescriptor:  POST generated module descriptor to FOLIO registry (Default: 'no')
 * publishAPI: Publish API RAML documentation.  (Default: 'no')
 * runLintRamlCop: Run 'raml-cop' on back-end modules that have declared RAML in api.yml (Default: 'no')
*/
 


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def foliociLib = new org.folio.foliociCommands()

  def buildNode = config.buildNode ?: 'jenkins-slave-all'

  node(buildNode) {
    timeout(60) {

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

          echo "Checked out branch: $env.BRANCH_NAME"
        }

        stage('Setup') {
          def mvn_artifact = readMavenPom().getArtifactId()
          def mvn_version =  readMavenPom().getVersion()
          env.name = mvn_artifact

          // if release
          if ( foliociLib.isRelease() )  {
            // make sure git tag and maven version match
            if ( foliociLib.tagMatch(mvn_version) ) {
              env.version = mvn_version
              env.isRelease = true
              env.dockerRepo = 'folioorg'
            }
            else { 
              error('Git release tag and Maven version mismatch')
            }
          } 
          // else snapshot
          else {
            env.version = "${mvn_version}.${env.BUILD_NUMBER}"
            env.snapshot = true
            env.dockerRepo = 'folioci'
          }
            
          env.projectName = foliociLib.getProjName()
          echo "Project Name: $env.projectName"
        }

        stage('Maven Build') {
          echo "Building Maven artifact: ${env.name} Version: ${env.version}"
          withMaven(jdk: 'openjdk-8-jenkins-slave-all',  
                    maven: 'maven3-jenkins-slave-all',  
                    mavenSettingsConfig: 'folioci-maven-settings') {
            sh 'mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install'
          }
        }

      
        // Docker stuff
        if (config.doDocker) {
          stage('Docker Build') {
            echo "Building Docker image for $env.name:$env.version" 
            config.doDocker.delegate = this
            config.doDocker.resolveStrategy = Closure.DELEGATE_FIRST
	    config.doDocker.call()
          }
        } 

        // Run Sonarqube
        stage('SonarQube Analysis') {
          sonarqubeMvn() 
        }

        // master branch or tagged releases
        if (( env.BRANCH_NAME == 'master' ) || ( env.isRelease )) {

          if ( config.mvnDeploy ==~ /(?i)(Y|YES|T|TRUE)/ ) {
            stage('Maven Deploy') {
              echo "Deploying artifacts to Maven repository"
              withMaven(jdk: 'openjdk-8-jenkins-slave-all', 
                      maven: 'maven3-jenkins-slave-all', 
                      mavenSettingsConfig: 'folioci-maven-settings') {
                sh 'mvn -DskipTests deploy'
              }
            }
          }
          if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
            stage('Publish Module Descriptor') {
              echo "Publishing Module Descriptor to FOLIO registry"
              def modDescriptor = 'target/ModuleDescriptor.json'
              foliociLib.updateModDescriptor(modDescriptor)
              postModuleDescriptor(modDescriptor) 
            }
          }
          if (config.publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) {
            stage('Publish API Docs') {
              echo "Publishing API docs"
              sh "python3 /usr/local/bin/generate_api_docs.py -r $env.projectName -l info -o folio-api-docs"
              withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', 
                   accessKeyVariable: 'AWS_ACCESS_KEY_ID', 
                   credentialsId: 'jenkins-aws', 
                   secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
              }
            }
          }
        }

        if (config.runLintRamlCop ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Lint raml-cop') {
            runLintRamlCop()
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
        sendNotifications currentBuild.result
      }
    } //end timeout
  } // end node
} 

