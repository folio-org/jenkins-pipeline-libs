#!/usr/bin/env groovy


/*
 * Deploy files to public web-enabled S3 in us-east-1 AWS region.  

 * Requires a type Map with the following vars defined:
 *  - s3Bucket - name of S3 bucket
 *  - srcPath - /path/to/srcdir)
 *
 * The following Map opts are optional:
 *  - s3Tags 'Key=value,Value=value'
 */


def call(Map s3Opts = [:]) {

  def s3Region = 'us-east-1'
  def cliOpts = '--region ' + s3Region + ' --output text'
  def s3Uri = 'https://' + s3Opts.s3Bucket + '.s3.amazonaws.com'

  sh "aws $cliOpts s3 mb s3://${s3Opts.s3Bucket} || true" 
  sh "aws $cliOpts s3api put-bucket-acl --acl public-read --bucket $s3Opts.s3Bucket"
  sh "aws $cliOpts s3 sync --acl public-read --delete $s3Opts.srcPath s3://${s3Opts.s3Bucket}"
  sh "aws $cliOpts s3 website s3://${s3Opts.s3Bucket}/ --index-document index.html"

  if (s3Opts.s3Tags) {
    sh "aws $cliOpts s3api put-bucket-tagging --tagging 'TagSet=[{${s3Opts.s3Tags}}]' " +
       "--bucket $s3Opts.s3Bucket"
  }

  return s3Uri

}
