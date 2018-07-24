#!/usr/bin/groovy

/*
 * Main build script for Maven-based FOLIO projects
 *
 * Configurable parameters: 
 *
 * sqBranch:  List of additional branches to perform SonarQube Analysis (Default: none)
 * doDocker:  Build, test, and publish Docker image via 'buildJavaDocker' (Default: 'no')
 * mvnDeploy: Deploy built artifacts to Maven repository (Default: 'no')
 * publishModDescriptor:  POST generated module descriptor to FOLIO registry (Default: 'no')
 * publishApi: Publish API RAML documentation.  (Default: 'no')
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

      stage('Prep') {
        def mvn_artifact = readMavenPom().getArtifactId()
        def mvn_version =  readMavenPom().getVersion()
        env.name = mvn_artifact

        if (mvn_version ==~ /.*-SNAPSHOT$/) {
          echo "This is a snapshot build"
          env.version = "${mvn_version}.${env.BUILD_NUMBER}"
          env.snapshot = true
          env.dockerRepo = 'folioci'
        }
        else {
          echo "This is a release build"
          env.version = mvn_version
          env.dockerRepo = 'folioorg'
        }

        // project name is the GitHub repo name and is typically
        // different from mod name specified in package.json
        env.projectName = foliociLib.getProjName()
        echo "Project Name: $env.projectName"
      }

      stage('Maven Build') {
        echo "Building Maven artifact: ${env.name} Version: ${env.version}"
        timeout(30) {
          withMaven(jdk: 'openjdk-8-jenkins-slave-all',  
                    maven: 'maven3-jenkins-slave-all',  
                    mavenSettingsConfig: 'folioci-maven-settings') {
            sh 'mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install'
          }
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

      // Run Sonarqube stage
      if (config.sqBranch) {
        for (branch in config.sqBranch) {
          if (branch == env.BRANCH_NAME) {
            sonarqubeMvn(branch) 
          }
        }
      }
      else {
        sonarqubeMvn() 
      }

      if (config.runLintRamlCop ==~ /(?i)(Y|YES|T|TRUE)/) {
        runLintRamlCop()
      } 

      // master branch or tagged releases
      if (( env.BRANCH_NAME == 'master' ) ||     
         ( env.BRANCH_NAME ==~ /^v\d+\.\d+\.\d+$/ )) {

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
  } //end node
    
} 

