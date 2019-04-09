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

package com.terazyte.flow.parser

import com.terazyte.flow.config.{ExecTarget, JobConfig}
import com.terazyte.flow.steps._
import net.jcazevedo.moultingyaml._

case class LaunchJobConfig(project: String, secretFile: String, build: BuildConfig, install: InstallSteps)

trait InstallTask
case class BashCmd(bash: String) extends InstallTask
case class RemoteCmd(value: String, target: ExecTarget)
case class CopyCmd(from: String, to: String) extends InstallTask
case class HqlCmd(hql: String)               extends InstallTask

case class InstallSteps(steps: Seq[ExecutableStep])

trait CommandFormats extends DefaultYamlProtocol {

  implicit val copyCmd = yamlFormat2(CopyCmd)
  implicit val bashCmd = yamlFormat1(BashCmd)
  implicit val hqlCmd  = yamlFormat1(HqlCmd)

}

case class BuildConfig(source: String, workDir: String, cmd: String)

trait JobConfigProtocol extends DefaultYamlProtocol {

  implicit object StageConfigFormat extends YamlFormat[Map[String, Seq[ExecutableStep]]] {

    override def write(obj: Map[String, Seq[ExecutableStep]]): YamlValue = ???

    override def read(yaml: YamlValue): Map[String, Seq[ExecutableStep]] = {
      val steps = yaml match {
        case YamlArray(xs) =>
          xs.flatMap { x =>
            x match {
              case YamlObject(stg) =>
                stg.map {
                  case (YamlString(stgName), YamlArray(tsks)) =>
                    val taskDefns = tsks.flatMap { obj =>
                      obj.asYamlObject.fields.map {
                        case (YamlString(CopyStep.id), v)           => CopyStep.parseStep(v)
                        case (YamlString(RemoteCmdStep.id), v)      => RemoteCmdStep.parseStep(v)
                        case (YamlString(CreateResourceStep.id), v) => CreateResourceStep.parseStep(v)
                        case (YamlString(ScriptStep.id), v)         => ScriptStep.parseStep(v)

                      }

                    }
                    (stgName -> taskDefns)

                }
            }

          }
      }
      steps.toMap

    }

  }

  implicit val jc = yamlFormat3(JobConfig)

}

//trait LauchConfigProtocol extends DefaultYamlProtocol {
//
//  implicit val buildConfig = yamlFormat3(BuildConfig)
//
//  implicit object InstallerYamlFormat extends YamlFormat[InstallSteps] with CommandFormats {
//
//    override def write(obj: InstallSteps): YamlValue = ???
//
//    def read(value: YamlValue) = {
//      value match {
//        case YamlArray(xs) =>
//          val res = xs.flatMap { obj =>
//            obj.asYamlObject.fields.map {
//              case (YamlString(CopyStep.id), v)      => CopyStep.parseStep(v)
//              case (YamlString(RemoteCmdStep.id), v) => RemoteCmdStep.parseStep(v)
//
//            }
//
//          }
//
//          InstallSteps(res)
//        case x =>
//          println(x)
//          deserializationError("Installation steps expected")
//      }
//    }
//  }
//
//  implicit val launchConfig = yamlFormat4(LaunchJobConfig)
//}
