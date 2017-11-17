#!/usr/bin/env groovy

/*
 * Main build script for Maven-based FOLIO projects
 *
 * Configurable parameters: 
 *
 * doDocker:  Build, test, and publish Docker image via 'buildDocker' (Default: 'no')
 * runLint: Run ESLint via 'yarn lint' (Default: 'no')
 * runTest: Run unit tests via 'yarn test' (Default: 'no')
 * npmDeploy: Publish NPM artifacts to NPM repository (Default: 'yes')
 * publishModDescriptor:  POST generated module descriptor to FOLIO registry (Default: 'no')
 * modDescriptor: path to standalone Module Descriptor file (Optional)
 * publishApi: Publish API/RAML documentation.  (Default: 'no')
 * buildNode: label of jenkin's slave build node to use
*/


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def foliociLib = new org.folio.foliociCommands()
  
  // default is to deploy to npm repo when branch is master
  def npmDeploy = config.npmDeploy ?: 'yes'

  // use the smaller nodejs build node since most 
  // Nodejs builds are Stripes.
  def buildNode = config.buildNode ?: 'jenkins-slave-nodejs'

  // right now, all builds are snapshots
  env.snapshot = true
  
  node(buildNode) {

    try {
      stage('Checkout') {
        deleteDir()
        currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
        // sendNotifications 'STARTED'

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

      stage('Prep') {

        if (env.snapshot == true) {
          foliociLib.npmSnapshotVersion()
        }

        def Map simpleNameVerMap = foliociLib.npmSimpleNameVersion('package.json')          
        simpleNameVerMap.each { key, value ->
          env.simpleName = key
          env.version = value
        }
        echo "Package Simplfied Name: $env.simpleName"
        echo "Package Version: $env.version"

        // project name is the GitHub repo name and is typically
        // different from mod name specified in package.json
        env.project_name = foliociLib.getProjName()
        echo "Project Name: $env.project_name"
      }
 
      withCredentials([string(credentialsId: 'jenkins-npm-folioci',variable: 'NPM_TOKEN')]) {
        withNPM(npmrcConfig: 'jenkins-npm-folioci') {
          stage('NPM Build') {
            sh 'yarn install' 
          }

          if (config.runLint ==~ /(?i)(Y|YES|T|TRUE)/) {
            stage('ESLint') {
              echo "Running ESLint..."
              def lintStatus = sh(returnStatus:true, script: 'yarn lint 2>/dev/null 1> lint.output')
              echo "Lint Status: $lintStatus"
              if (lintStatus != 0) {
                def lintReport =  readFile('lint.output')
                if (env.CHANGE_ID) {
                  // Requires https://github.com/jenkinsci/pipeline-github-plugin
                  // comment is response to API request in case we ever need it.
                  def comment = pullRequest.comment(lintReport)
                  echo "$comment"
                }
                else {
                  echo "$lintReport"
                }
              }
              else {
                echo "No lint errors found"
              }
            }
          }

          if (config.runTest ==~ /(?i)(Y|YES|T|TRUE)/) {
            stage('Unit Tests') {
              echo "Running unit tests..."
              sh 'yarn test'
            }
          }

          if ( env.BRANCH_NAME == 'master' ) {
            if (npmDeploy ==~ /(?i)(Y|YES|T|TRUE)/) {
              stage('NPM Deploy') {
                // npm is more flexible than yarn for this stage. 
                echo "Deploying NPM packages to Nexus repository"
                sh 'npm publish'
              }
            }
          }

        }  // end withNPM
      }  // end WithCred    

      if (config.doDocker) {
        stage('Docker Build') {
          // use env.project_name as name of docker artifact
          env.name = env.project_name
          echo "Building Docker image for $env.name:$env.version" 
          config.doDocker.delegate = this
          config.doDocker.resolveStrategy = Closure.DELEGATE_FIRST
          config.doDocker.call()
        }
      } 

      if ( env.BRANCH_NAME == 'master' ) {
        if (config.publishModDescriptor ==~ /(?i)(Y|YES|T|TRUE)/) {
          // We assume that MDs are included in package.json
          stage('Publish Module Descriptor') {
            if (config.ModDescriptor) { 
              def modDescriptor = config.ModDescriptor
              if (env.snapshot) {
                env.name = env.simpleName
                // update the version to the snapshot version
                echo "Update Module Descriptor version to snapshot version"
                foliociLib.updateModDesccriptorId(modDescriptor)
              }
            }
            else {
              echo "Generating Stripes Module Descriptor from package.json"
              sh 'git clone https://github.com/folio-org/stripes-core'
              sh 'stripes-core/util/package2md.js --strict package.json > ModuleDescriptor.json'
              def modDescriptor = 'ModuleDescriptor.json'
            }
            echo "Publishing Module Descriptor to FOLIO registry"
            postModuleDescriptor(modDescriptor) 
          }
        }
        if (config.publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) {
          stage('Publish API Docs') {
            echo "Publishing API docs"
            sh "python3 /usr/local/bin/generate_api_docs.py -r $env.project_name -v -o folio-api-docs"
            sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
          }
        }
      } 

    }  // end try
    catch (Exception err) {
      currentBuild.result = 'FAILED'
      println(err.getMessage());
      echo "Build Result: $currentBuild.result"
      throw err
    }
    finally {
      // sendNotifications currentBuild.result
    }
  } // end node
    
} 

