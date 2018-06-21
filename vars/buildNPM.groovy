#!/usr/bin/env groovy

/*
 * Main build script for NPM-based FOLIO projects
 *
 * Configurable parameters: 
 *
 * doDocker:  Build, test, and publish Docker image via 'buildDocker' (Default: 'no')
 * runLint: Run ESLint via 'yarn lint' (Default: 'no')
 * runTest: Run unit tests via 'yarn test' (Default: 'no')
 * runRegression: Run UI regression tests for PRs - 'none','full' or 'partial' (Default: 'none') 
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

  // default is don't run regression tests for PRs
  def runRegression = config.runRegression ?: 'none'

  // default runTestOptions
  def runTestOptions = config.runTestOptions ?: ''

  // use the smaller nodejs build node since most 
  // Nodejs builds are Stripes.
  def buildNode = config.buildNode ?: 'jenkins-slave-all'

  // right now, all builds are snapshots
  env.snapshot = true
  env.dockerRepo = 'folioci'
  
  node(buildNode) {

    try {
      stage('Checkout') {
        deleteDir()
        currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
        sendNotifications 'STARTED'

        checkout([
                 $class: 'GitSCM',
                 branches: scm.branches,
                 extensions: scm.extensions + [[$class: 'RelativeTargetDirectory',
                                                       relativeTargetDir: 'project'],
                                              [$class: 'SubmoduleOption',
                                                       disableSubmodules: false,
                                                       parentCredentials: false,
                                                       recursiveSubmodules: true,
                                                       reference: '',
                                                       trackingSubmodules: false]],
                 userRemoteConfigs: scm.userRemoteConfigs
         ])

         echo "Checked out branch:  $env.BRANCH_NAME"
      }

      dir("${env.WORKSPACE}/project") {
        stage('Prep') {

          if (env.snapshot) {
            foliociLib.npmSnapshotVersion()
          }

          // the actual NPM package name as defined in package.json
          env.npm_name = foliociLib.npmName('package.json')

          // simpleName is similar to npm_name except make name okapi compliant
          def Map simpleNameVerMap = foliociLib.npmSimpleNameVersion('package.json')          
          simpleNameVerMap.each { key, value ->
            env.simpleName = key
            env.version = value
          }
          // "short" name e.g. 'folio_users' -> 'users'
          env.npmShortName = foliociLib.getNpmShortName(env.simpleName)

          // project name is the GitHub repo name and is typically
          // different from mod name specified in package.json
          env.project_name = foliociLib.getProjName()

          echo "Package Simplfied Name: $env.simpleName"
          echo "Package Short Name: $env.npmShortName"
          echo "Package Version: $env.version"
          echo "Project Name: $env.project_name"
        }
 
        withCredentials([string(credentialsId: 'jenkins-npm-folioci',variable: 'NPM_TOKEN')]) {
          withNPM(npmrcConfig: 'jenkins-npm-folioci') {
            stage('NPM Install') {
              sh 'yarn install' 
            }

            if (config.runLint ==~ /(?i)(Y|YES|T|TRUE)/) {
              runLintNPM()
            } 

            if (config.runTest ==~ /(?i)(Y|YES|T|TRUE)/) {
              runTestNPM(runTestOptions)
            }

            if ( env.BRANCH_NAME == 'master' ) {
              if (npmDeploy ==~ /(?i)(Y|YES|T|TRUE)/) {
                stage('NPM Publish') {
                  // npm is more flexible than yarn for this stage. 
                  echo "Deploying NPM packages to Nexus repository"
                  sh 'npm publish -f'
                }
              }
            }

          }  // end withNPM
          // remove .npmrc put in directory by withNPM
          sh 'rm -f .npmrc'
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
              def modDescriptor = ''
              if (config.modDescriptor) { 
                modDescriptor = config.modDescriptor
                env.name = env.project_name
                if (env.snapshot) {
                  // update the version to the snapshot version
                  echo "Update Module Descriptor version to snapshot version"
                  foliociLib.updateModDescriptorId(modDescriptor)
                }
              }
              else {
                echo "Generating Stripes Module Descriptor from package.json"
                env.name = env.simpleName
                sh 'git clone https://github.com/folio-org/stripes-core'
                sh 'stripes-core/util/package2md.js --strict package.json > ModuleDescriptor.json'
                modDescriptor = 'ModuleDescriptor.json'
              }
              echo "Publishing Module Descriptor to FOLIO registry"
              postModuleDescriptor(modDescriptor) 
            }
          }

          if (config.publishAPI ==~ /(?i)(Y|YES|T|TRUE)/) {
            stage('Publish API Docs') {
              echo "Publishing API docs"
              sh "python3 /usr/local/bin/generate_api_docs.py -r $env.project_name -l info -o folio-api-docs"
              withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                   accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                   credentialsId: 'jenkins-aws',
                   secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                sh 'aws s3 sync folio-api-docs s3://foliodocs/api'
              }
            }
          }
        } 
      } // end dir

      if (env.CHANGE_ID) {

        // ensure tenant id is unique
        // def tenant = "${env.BRANCH_NAME}_${env.BUILD_NUMBER}"
        def tenant = "pr_${env.CHANGE_ID}_${env.BUILD_NUMBER}"
        tenant = foliociLib.replaceHyphen(tenant)
        def okapiUrl = 'http://folio-snapshot-stable.aws.indexdata.com:9130'

        dir("${env.WORKSPACE}/project") {
          // clean up previous 'yarn install'
          sh 'rm -rf node_modules yarn.lock'
          sh 'yarn link'
          /* a bit of NPM voodoo. Link to project itself so as not to install
          *  package from NPM repository. */
          sh "yarn link $env.npm_name"
          sh 'yarn install'
        }

        // Build stripes, deploy tenant on backend, run ui regression
        buildStripes("$okapiUrl","$tenant")
        if (runRegression != 'none') { 
          def tenantStatus = deployTenant("$okapiUrl","$tenant") 
          if (tenantStatus != 0) {
            echo "Problem deploying tenant. Skipping UI Regression testing."
          }
          else {
            runUiRegressionPr(runRegression,"${tenant}_admin",'admin','http://localhost:3000')
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
      sendNotifications currentBuild.result
    }
  } // end node
    
} 

