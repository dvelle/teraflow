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
import com.terazyte.flow.bash.BashScriptParser
import com.terazyte.flow.job.{Stage, Task, TaskDef}
import com.terazyte.flow.task.ExecScript
import net.jcazevedo.moultingyaml.{YamlString, YamlValue}

case class ScriptStep(scriptType: String, execDef: ExecDef) extends TaskDef(name = s"Execute ${scriptType} script", tailLogs = true) {

  override def buildTask(context: ActorContext, stage: Stage): Task = {
    val actor = context.actorOf(ScriptStep.props(this))
    Task(stage, this, actor)
  }
}

object ScriptStep extends Step[ScriptStep] with BashScriptParser {
  override val id: String = "exec"

  override def props(step: ScriptStep): Props = Props(new ExecScript(step))

  override def parseStep(value: YamlValue): ScriptStep = {
    val scriptDef = value.asYamlObject.fields.map {
      case (YamlString(bash), v) => v.convertTo[BashScript]
    }.head

    ScriptStep(bash, scriptDef)
  }
}
