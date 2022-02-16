def checkout() {
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
