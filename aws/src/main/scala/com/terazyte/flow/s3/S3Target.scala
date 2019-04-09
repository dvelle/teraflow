/*
 * Copyright 2019 terazyte
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terazyte.flow.s3

import java.io.File

import com.terazyte.flow.config._
import com.amazonaws.{ClientConfiguration, Protocol}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import net.jcazevedo.moultingyaml.YamlValue

case class S3Target(alias: String, awsProfile: Option[String]) extends ResourceConfig with DataTransferable {

  override def execCopy(from: String, to: String): Unit = {

    val s3client = AmazonS3ClientBuilder
      .standard()
      .withCredentials(new ProfileCredentialsProvider(awsProfile.getOrElse("default")))
      .withRegion("US-WEST-2".toLowerCase)
      .build()

    getBucketAndKey(to) match {
      case Some(s3Obj) =>
        val sourceFile = new File(from)
        s3client.putObject(s3Obj.bucket, s3Obj.key + sourceFile.getName, sourceFile)
      case None => throw new IllegalArgumentException(s"Unable to extract the S3 path, ${to} for the target: ${alias}")
    }

  }

  def getBucketAndKey(path: String): Option[S3Obj] = {
    if (path.startsWith("s3://")) {
      val excludeProtocol = path.substring(5)
      val bucket          = excludeProtocol.substring(0, excludeProtocol.indexOf('/'))
      val key             = excludeProtocol.substring(bucket.length + 1)
      Some(S3Obj(bucket, key))
    } else
      None
  }

}

import net.jcazevedo.moultingyaml.DefaultYamlProtocol._
object S3TargetParser extends Resource[S3Target] {

  implicit val s3TargetFormat = yamlFormat2(S3Target.apply)
  override val id: String     = "s3"

  override def parse(v: YamlValue): S3Target = {
    v.convertTo[S3Target]
  }
}

case class S3Obj(bucket: String, key: String)
