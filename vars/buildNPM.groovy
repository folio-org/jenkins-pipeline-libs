#!/usr/bin/env groovy

/*
 * Main build script for Maven-based FOLIO projects
 *
 * Configurable parameters: 
 *
 * doDocker:  Build, test, and publish Docker image via 'buildDocker' (Default: 'no')
 * runLint: Run ESLint via 'yarn lint' (Default: 'no')
 * runTest: Run unit tests via 'yarn test' (Default: 'no')
 * runRegression: Run UI regression tests for PRs (Default: 'no') 
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
  def runRegression = config.runRegression ?: 'no'

  // use the smaller nodejs build node since most 
  // Nodejs builds are Stripes.
  def buildNode = config.buildNode ?: 'jenkins-slave-all'

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
          // fix some permissions as a result of above command
          sh 'sudo chown -R jenkins /home/jenkins'
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

      // if (( env.CHANGE_ID ) && 
      if (( env.BRANCH_NAME == 'folio-1043-test' ) && 
         ( runRegression ==~ /(?i)(Y|YES|T|TRUE)/)) {

        // ensure tenant id is unique
        //def tenant = "${env.CHANGE_ID}_${env.BUILD_NUMBER}"
        def tenant = "${env.BRANCH_NAME}_${env.BUILD_NUMBER}"
        env.tenant = foliociLib.replaceHyphen(tenant)
        env.okapiUrl = 'http://folio-snapshot-test.aws.indexdata.com:9130'

        stage('Test Stripes Platform') {
          dir("${env.WORKSPACE}/project") {

            // remove node_modules directory
            sh 'rm -rf node_modules yarn.lock'
            sh 'yarn link'
            sh "yarn link $env.npm_name"
            sh 'yarn install'
          }

          dir("$env.WORKSPACE") { 
            sh 'git clone https://github.com/folio-org/ui-testing'
            sh 'git clone https://github.com/folio-org/folio-testing-platform'
          }
          
          dir("${env.WORKSPACE}/folio-infrastructure") {
            checkout([$class: 'GitSCM', branches: [[name: '*/master']], 
                               doGenerateSubmoduleConfigurations: false, 
                               extensions: [[$class: 'SubmoduleOption', 
                                                      disableSubmodules: false, 
                                                      parentCredentials: false, 
                                                      recursiveSubmodules: true, 
                                                      reference: '', 
                                                      trackingSubmodules: true]], 
                               submoduleCfg: [], 
                               userRemoteConfigs: [[credentialsId: 'folio-jenkins-github-token', 
                                                    url: 'https://github.com/folio-org/folio-infrastructure']]])

          }
            
          dir ("${env.WORKSPACE}/folio-testing-platform") {
            sh "yarn link $env.npm_name"
            sh 'yarn install'

            // publish generated yarn.lock 
            sh 'echo "<html><head><title>folio-testing-platform-yarn-lock</title></head>"' +
               '> ftp-yarnlock.html'
            sh 'echo "<body><pre>" >> ftp-yarnlock.html'
            sh 'cat yarn.lock >> ftp-yarnlock.html'
            sh 'echo "<body><pre>" >> ftp-yarnlock.html' 
            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, 
               keepAll: true, reportDir: '.', 
               reportFiles: 'ftp-yarnlock.html', 
               reportName: 'folio-testing-platform yarn.lock', 
               reportTitles: 'folio-testing-platform yarn.lock'])


            // generate mod descriptors with '--strict' flag for dependencies
            sh 'yarn postinstall --strict'

            // build webpack with stripes-cli 
            sh "stripes build --okapi $env.okapiUrl --tenant $env.tenant stripes.config.js bundle"

            // start simple webserver to serve webpack
            sh 'sudo npm install -g http-server'
              withEnv(['JENKINS_NODE_COOKIE=dontkill']) {
                sh 'http-server -p 3000 ./bundle &'
              }

            def scriptPath="${env.WORKSPACE}/folio-infrastructure/CI/scripts"

            // create tenant
            sh "${scriptPath}/createTenant.sh $env.okapiUrl $env.tenant"

            // post MDs and enable tenant modules
            sh "${scriptPath}/createTenantModuleList.sh $env.okapiUrl $env.tenant ModuleDescriptors " +
               "> tenant_mod_list"
            sh 'cat tenant_mod_list'
            sh "${scriptPath}/enableTenantModules.sh $env.okapiUrl $env.tenant < tenant_mod_list"

            // create tenant admin user which defaults to TENANTNAME_admin with password of 'admin'
            withCredentials([string(credentialsId: 'folio_admin-pgpassword',variable: 'PGPASSWORD')]) {
              sh "${scriptPath}/createTenantAdminUser.sh $env.tenant"
            }
          } 

          // load sample data, reference data, etc for tenant using Ansible
          dir("${env.WORKSPACE}/folio-infrastructure/CI/ansible") { 

            // set vars in include file 
            sh "echo --- > vars_pr.yml"
            sh "echo okapi_url: ${env.okapiUrl} >> vars_pr.yml"
            sh "echo tenant: ${env.tenant} >> vars_pr.yml"
            sh "echo admin_user: { username: ${env.tenant}_admin, password: admin } >> vars_pr.yml"

            // debug
            sh 'cat vars_pr.yml'
       

            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', 
                                       accessKeyVariable: 'AWS_ACCESS_KEY_ID', 
                                       credentialsId: 'jenkins-aws', 
                                       secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

              ansiblePlaybook credentialsId: '11657186-f4d4-4099-ab72-2a32e023cced', 
                           installation: 'Ansible', 
                           inventory: 'inventory', 
                           playbook: 'folioci-pr.yml', 
                           sudoUser: null, vaultCredentialsId: 'ansible-vault-pass'
            }
          }
          

          dir("${env.WORKSPACE}/ui-testing") {  
            sh "yarn link $env.npm_name"
            def testStatus = runUiRegressionPr("${env.tenant}_admin",'admin','http://localhost:3000')
            echo "Regression test status: $testStatus" 

            // publish generated yarn.lock 
            sh 'echo "<html><head><title>ui-testing-yarn-lock</title></head>"' +
               '> uitest-yarnlock.html'
            sh 'echo "<body><pre>" >> uitest-yarnlock.html'
            sh 'cat yarn.lock >> uitest-yarnlock.html'
            sh 'echo "<body><pre>" >> uitest-yarnlock.html'
            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: '.',
               reportFiles: 'uitest-yarnlock.html',
               reportName: 'ui-testing yarn.lock',
               reportTitles: 'ui-testing yarn.lock'])

          }
        } // end stage
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

