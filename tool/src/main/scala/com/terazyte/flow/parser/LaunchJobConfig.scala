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
import com.terazyte.flow.job.TaskDef
import com.terazyte.flow.steps._
import net.jcazevedo.moultingyaml._

case class LaunchJobConfig(project: String, secretFile: String, build: BuildConfig, install: InstallSteps)

trait InstallTask
case class BashCmd(bash: String) extends InstallTask
case class RemoteCmd(value: String, target: ExecTarget)
case class CopyCmd(from: String, to: String) extends InstallTask
case class HqlCmd(hql: String)               extends InstallTask

case class InstallSteps(steps: Seq[TaskDef])

trait CommandFormats extends DefaultYamlProtocol {

  implicit val copyCmd = yamlFormat2(CopyCmd)
  implicit val bashCmd = yamlFormat1(BashCmd)
  implicit val hqlCmd  = yamlFormat1(HqlCmd)

}

case class BuildConfig(source: String, workDir: String, cmd: String)

trait JobConfigProtocol extends DefaultYamlProtocol {

  implicit object StageConfigFormat extends YamlFormat[Seq[TaskDef]] {

    override def write(obj: Seq[TaskDef]): YamlValue = ???

    override def read(yaml: YamlValue): Seq[TaskDef] = {
      val tasks = yaml match {
        case YamlArray(xs) =>
          val taskDefns = xs.flatMap { obj =>
            obj.asYamlObject.fields.map {
              case (YamlString(CopyStep.id), v)           => CopyStep.parseStep(v)
              case (YamlString(RemoteCmdStep.id), v)      => RemoteCmdStep.parseStep(v)
              case (YamlString(CreateResourceStep.id), v) => CreateResourceStep.parseStep(v)
              case (YamlString(ScriptStep.id), v)         => ScriptStep.parseStep(v)
              case (YamlString(SparkSubmitStep.id), v)    => SparkSubmitStep.parseStep(v)
            }

          }
          taskDefns

      }

      tasks

    }

  }

  implicit val jc = yamlFormat3(JobConfig)

}
