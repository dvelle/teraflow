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

package com.terazyte.flow.steps

import akka.actor.{ActorContext, Props}
import com.terazyte.flow.job.{Stage, Task, TaskDef}
import com.terazyte.flow.task.ExecRemoteCopy
import net.jcazevedo.moultingyaml.{DefaultYamlProtocol, YamlValue}

case class CopyStep(from: String, to: String, name : Option[String], target: Option[String] = None)
    extends TaskDef(taskName = name.getOrElse(s"Copy from ${from}, to ${to} located at ${target.getOrElse("local")}")) {

  override def buildTask(context: ActorContext): Task = {
    val actor = context.actorOf(CopyStep.props(this))
    Task(this, actor)
  }
}

object CopyStep extends Step[CopyStep] with DefaultYamlProtocol {

  implicit val copyStep = yamlFormat4(CopyStep.apply)

  override val id: String = "cp"

  def props(step: CopyStep): Props = Props(new ExecRemoteCopy(step))

  override def parseStep(value: YamlValue): CopyStep = value.convertTo[CopyStep]

}
