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

import com.terazyte.flow.steps.CopyStep
import com.terazyte.flow.task.common._
import com.terazyte.flow.config.DataTransferable
import com.terazyte.flow.job._
import com.terazyte.flow.parser.RemoteHostParser

import scala.util.Try

case class ExecRemoteCopy(taskDef: CopyStep) extends TaskExecutor[CopyStep] {

  override def execute(session: Session): Either[Throwable, TaskExecResult] = {
    val host = RemoteHostParser.parseRemoteHost(taskDef.to, session.resources)
    session.resources
      .find(_.alias.equals(taskDef.target.get))
      .map {
        case x: DataTransferable =>
          Try(x.execCopy(taskDef.from, taskDef.to)).toEither.map(_ => TaskExecResult.success(taskDef))
        case _ => Left(new RuntimeException("Unsupported resources configuration"))
      }
      .getOrElse(Left(new RuntimeException(s"No resources configured by the alias: ${taskDef.target}")))

  }

}

case object RemoteServer extends TargetType

