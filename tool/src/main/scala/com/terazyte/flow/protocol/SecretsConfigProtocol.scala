/*
 * Copyright 2019 Terazyte
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

package com.terazyte.flow.protocol

import com.terazyte.flow.config.ResourceConfig
import com.terazyte.flow.config.remote.{RemoteHost, RemoteHostYamlParser}
import com.terazyte.flow.docker.DockerYamlParser
import com.terazyte.flow.emr.EMRTargetYamlParser
import com.terazyte.flow.s3.{S3Target, S3TargetParser}
import net.jcazevedo.moultingyaml.{DefaultYamlProtocol, YamlArray, YamlFormat, YamlString, YamlValue, deserializationError}

trait SecretConfigProtocol extends DefaultYamlProtocol {

  implicit object SecretConfigFormat extends YamlFormat[Seq[ResourceConfig]] {

    override def write(obj: Seq[ResourceConfig]): YamlValue = ???

    override def read(value: YamlValue): Seq[ResourceConfig] =
      value.asYamlObject.getFields(YamlString("resources")).head match {
        case YamlArray(xs) =>
          val res = xs.flatMap { obj =>
            obj.asYamlObject.fields.map {
              case (YamlString(RemoteHostYamlParser.id), v) => RemoteHostYamlParser.parse(v)
              case (YamlString(S3TargetParser.id), v)       => S3TargetParser.parse(v)
              case (YamlString(EMRTargetYamlParser.id), v)  => EMRTargetYamlParser.parse(v)
              case (YamlString(DockerYamlParser.id), v)  => DockerYamlParser.parse(v)

            }
          }

          res
        case x =>
          deserializationError("Installation steps expected")
      }

  }
}
