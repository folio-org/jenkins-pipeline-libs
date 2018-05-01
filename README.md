# Shared library for FOLIO Jenkins Pipeline

This repository contains the Jenkins pipeline shared library used for FOLIO CI/CD by projects
located at https://github.com/folio-org

The library assumes a lot about the configuration of the Jenkins server and the FOLIO
development workflow, so it's probably of limited use outside the FOLIO Jenkins context.  

To utilize this library for https://github.com/folio-org projects,  add a 'Jenkinsfile' to
the top-level directory of the project repository.   The library primarily supports two types
of development environments at this time - Java-based Maven projects and Nodejs-based projects. 

A typical Stripes or UI module Jenkinsfile configuration might look like the following.

```
buildNPM {
  publishModDescriptor = 'yes'
  runLint = 'yes'
  runTest = 'yes'
  runRegression = 'partial'
}
```

All parameters listed above are optional or specific to the project.

* 'publishModDescriptor' - If a FOLIO Module Descriptor is defined package.json, the Module
Descriptor will be generated and published to the FOLIO Module Descriptor registry at
http://folio-registry.aws.indexdata.com

* 'runLint' - Will execute 'yarn lint' as part of the build.  Ensure a 'lint' run script is
defined in package.json before enabling this option. 

* 'runTest' - Will execute 'yarn test' as part of the build.  Ensure a 'test' run script is
defined in package.json.  'test' is typically used for unit tests.

* 'runRegression' - Will execute the UI regression test suite from 'ui-testing' against a real 
FOLIO backend.  'full' will execute the full test suite while 'partial' will execute only tests 
specific to the UI module'.  'none' disable regression testing. 


A typical Maven-based, server-side FOLIO module Jenkinsfile configuration might look like 
the following.

```
buildMvn {
  publishModDescriptor = 'yes'
  publishAPI = 'yes'
  mvnDeploy = 'yes'

  doDocker = {
    buildJavaDocker {
      publishMaster = 'yes'
      healthChk = 'yes'
      healthChkCmd = 'curl -sS --fail -o /dev/null  http://localhost:8081/apidocs/ || exit 1'
    }
  }
}
```

 * 'publishAPI' - Convert RAML-based API to HTML and publish to http://dev.folio.org/doc/api/ 

 * 'mvnDeploy' - Deploy Maven artifacts to FOLIO Maven repository.

If we are creating and deploying a Docker image as part of the module's artifacts, specify
'doDocker' with 'buildJavaDocker' and the following options:

 * 'publishMaster' - Publish image to 'folioci' Docker repository on Docker Hub when building
'master' branch.  This is the default. 

 * 'healthChk' - Perform a container healthcheck during build.  See 'healthChkCmd'.  

 * 'healthChkCmd' - Use the specified command to perform container health check.   The
command is run *inside* the container and typically tests a REST endpoint to determine the 
health of the application running inside the container. 

There are other options available to 'buildNPM', 'buildMvn', and 'buildJavaDocker' for certain 
corner cases.  Check these scripts directly for additional information.



