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
import com.terazyte.flow.task.{ExecRemoteCmd, ExecRemoteCopy}
import net.jcazevedo.moultingyaml.{DefaultYamlProtocol, YamlValue}

case class RemoteCmdStep(value: String, target: Option[String])
    extends TaskDef(taskName = s"Exec ${value} @${target.getOrElse("local")}", tailLogs = true) {

  override def buildTask(context: ActorContext): Task = {
    val actor = context.actorOf(RemoteCmdStep.props(this))
    Task(this, actor)
  }

}

object RemoteCmdStep extends Step[RemoteCmdStep] with DefaultYamlProtocol {
  override val id: String = "cmd"
  implicit val remoteCmd  = yamlFormat2(RemoteCmdStep.apply)

  def props(step: RemoteCmdStep): Props = Props(new ExecRemoteCmd(step))

  override def parseStep(value: YamlValue): RemoteCmdStep = value.convertTo[RemoteCmdStep]

}
