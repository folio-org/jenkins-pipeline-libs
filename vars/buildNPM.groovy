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
  def buildNode = config.buildNode ?: 'jenkins-slave-ansible'

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

         echo "Checked out $env.BRANCH_NAME"
         echo "Workspace: $env.WORKSPACE"
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
          echo "Package Simplfied Name: $env.simpleName"
          echo "Package Version: $env.version"

          // project name is the GitHub repo name and is typically
          // different from mod name specified in package.json
          env.project_name = foliociLib.getProjName()
          echo "Project Name: $env.project_name"

          // Install stripes-cli globally
          sh 'sudo yarn global add @folio/stripes-cli --prefix /usr/local'
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
              sh "python3 /usr/local/bin/generate_api_docs.py -r $env.project_name -v -o folio-api-docs"
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

      // if ( env.CHANGE_ID ) {
      if ( env.BRANCH_NAME == 'folio-1043-test' ) {

        //env.tenant = env.CHANGE_ID
        env.tenant = env.BRANCH_NAME
        env.okapi_url = 'http://folio-snapshot-test.aws.indexdata.com:9130'

        stage('Test Stripes Platform') {
          dir("${env.WORKSPACE}/project") {
            sh "yarn link"
          }

          dir("$env.WORKSPACE") { 
            sh 'git clone https://github.com/folio-org/ui-testing'
            sh 'git clone https://github.com/folio-org/folio-testing-platform'
          }

          dir ("${env.WORKSPACE}/folio-testing-platform") {
            sh "yarn link $env.npm_name"
            sh 'yarn install'
            sh 'yarn build bundle'
            withEnv(['JENKINS_NODE_COOKIE=dontkill']) {
              sh "stripes serve stripes.config.js --okapi $env.okapi_url --tenant $env.tenant &"
            }
          }

          dir("${env.WORKSPACE}/folio-infrastructure") {
            checkout([$class: 'GitSCM', branches: [[name: '*/folio-1043']], 
                               doGenerateSubmoduleConfigurations: false, 
                               extensions: [[$class: 'SubmoduleOption', 
                                                      disableSubmodules: false, 
                                                      parentCredentials: false, 
                                                      recursiveSubmodules: true, 
                                                      reference: '', trackingSubmodules: true]], 
                               submoduleCfg: [], 
                               userRemoteConfigs: [[credentialsId: 'folio-jenkins-github-token', 
                                                    url: 'https://github.com/folio-org/folio-infrastructure']]])

            ansiblePlaybook credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced', installation: 'Ansible', 
                           inventory: 'CI/ansible/inventory', 
                           playbook: 'CI/ansible/folio-pr.yml', 
                           sudoUser: null, vaultCredentialsId: 'ansible-vault-pass',
                           extras: '-e "tenant_id=${env.tenant}" -e "tenant_name=${env.tenant}" -e "okapi_url=${env.okapi_url}"'
          }
        } 
      } // end PR Integration tests
    }  // end try
    catch (Exception err) {
      currentBuild.result = 'FAILED'
      println(err.getMessage());
      echo "Build Result: $currentBuild.result"
      throw err
    }
    finally {
      //sendNotifications currentBuild.result
    }
  } // end node
    
} 

