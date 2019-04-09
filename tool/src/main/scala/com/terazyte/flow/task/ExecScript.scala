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

package com.terazyte.flow.task
import com.terazyte.flow.config.remote.RemoteHost
import com.terazyte.flow.emr.EMRTarget
import com.terazyte.flow.job.{Session, TaskExecResult, TaskExecutor}
import com.terazyte.flow.steps.{BashScript, ScriptStep}
import com.terazyte.flow.task.common.{IdGenerator, LocalResource}

case class ExecScript(taskDef: ScriptStep) extends TaskExecutor[ScriptStep] {
  override def execute(session: Session): Either[Throwable, TaskExecResult] = {

    taskDef.execDef match {
      case x: BashScript =>
        val resource = x.target match {
          case Some(t) => session.resources.find(_.alias.equalsIgnoreCase(t))
          case _       => Some(new LocalResource)
        }
        val opsResult = resource map {
          case r: LocalResource => r.exec(x)
          case r: RemoteHost =>
            r.exec(x).map(s => TaskExecResult.success(taskDef, IdGenerator.id(taskDef.scriptType), s))
          case r: EMRTarget =>
            r.exec(x).map(s => TaskExecResult.success(taskDef, IdGenerator.id(taskDef.scriptType), s))
          case _ => Left(new IllegalArgumentException("Eligible targets are local, remote server and EMR cluster"))
        }

        opsResult.getOrElse(Left(new IllegalArgumentException(s"Resouce, ${x.target.get} not found.")))

    }

  }
}
