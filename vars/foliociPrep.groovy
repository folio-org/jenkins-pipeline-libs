#!/usr/bin/env groovy


def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node {
    echo "hello test message: ${config.message}"
  }
}
 
  

  
