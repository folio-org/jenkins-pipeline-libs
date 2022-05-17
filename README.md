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
  publishModDescriptor = true
  runLint = true
  runTest = true
  runTestOptions = '--karma.singleRun --karma.browsers=ChromeDocker'  (for karma-based testing)
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

* 'runTestOptions' - Provide 'yarn test' with additional options

* 'runRegression' - Will execute the UI regression test suite from 'ui-testing' against a real 
FOLIO backend.  'full' will execute the full test suite while 'partial' will execute only tests 
specific to the UI module'.  'none' disable regression testing. 


A typical Maven-based, server-side FOLIO module Jenkinsfile configuration might look like 
the following.

```
buildMvn {
  publishModDescriptor = true
  mvnDeploy = true

  doDocker = {
    buildJavaDocker {
      publishMaster = true
      healthChk = true
      healthChkCmd = 'wget --no-verbose --tries=1 --spider http://localhost:8081/admin/health || exit 1'
    }
  }
}
```

 * 'mvnDeploy' - Deploy Maven artifacts to FOLIO Maven repository.

If we are creating and deploying a Docker image as part of the module's artifacts, specify
'doDocker' with 'buildJavaDocker' and the following options:

 * 'publishMaster' - Publish image to 'folioci' Docker repository on Docker Hub when building
'master' branch.  This is the default. 

 * 'healthChk' - Perform a container healthcheck during build.  See 'healthChkCmd'.  

 * 'healthChkCmd' - Use the specified command to perform container health check.   The
command is run *inside* the container and typically tests a REST endpoint to determine the 
health of the application running inside the container.  Prefer `wget` over `curl` as Alpine
by default ships without `curl` but with [BusyBox](https://www.busybox.net/about.html), a
multi-call binary that contains `wget` with reduced number of options.

Investigate the configuration of other similar repositories, e.g.
[mod-notes](https://github.com/folio-org/mod-notes) and
[ui-checkin](https://github.com/folio-org/ui-checkin).

There are other options available to 'buildNPM', 'buildMvn', and 'buildJavaDocker' for certain 
corner cases.  Check these scripts directly for additional information.



