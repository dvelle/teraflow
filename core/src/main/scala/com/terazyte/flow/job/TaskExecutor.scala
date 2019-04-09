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

package com.terazyte.flow.job

import java.util.UUID

import akka.actor.ActorRef
import com.terazyte.flow.config.ResourceConfig
import com.terazyte.flow.job.StageExecutor.TaskCompleted
import com.terazyte.flow.steps.ExecutableStep
import com.terazyte.flow.task.actor.BaseActor

trait TaskExecutor[T <: TaskDef] extends BaseActor {
  val taskDef: T
  def id(): String = UUID.randomUUID().toString

  def execute(session: Session): Either[Throwable, TaskExecResult]

  override def receive: Receive = {

    case ExecCommand(session, manager) =>
      val execResult = execute(session) match {
        case Left(ex) =>
          TaskExecResult("01", taskDef, Failed, taskDef.onFailureMessage(ex))
        case Right(result) =>
          result
      }

      manager ! TaskCompleted(execResult)

  }

}
case class ExecCommand(session: Session, manager: ActorRef)

abstract class TaskDef(val name: String, val tailLogs: Boolean = false) extends ExecutableStep {

  def onSuccessMessage(): String = s"${name} completed"

  def onSkipMessage(): String = s"${name} skipped"

  def onFailureMessage(throwable: Throwable): String =
    s"Failure cause: ${throwable.getMessage()}"

}

case class Task(stage: Stage, taskDef: TaskDef, actor: ActorRef)

sealed trait TaskStatus
case object Starting   extends TaskStatus
case object Running    extends TaskStatus
case object Stopping   extends TaskStatus
case object Stopped    extends TaskStatus
case object Failed     extends TaskStatus
case object Skipped    extends TaskStatus
case object Completed  extends TaskStatus
case object Restarting extends TaskStatus

case class TaskExecResult(execId: String, taskDef: TaskDef, status: TaskStatus, message: String, detail: String = "")
object TaskExecResult {

  def success(taskDef: TaskDef, id: String, detail: String = ""): TaskExecResult =
    TaskExecResult(id, taskDef, Completed, s"${taskDef.name} Completed", detail)

  def failed(taskDef: TaskDef, id: String, cause: String) = TaskExecResult(id, taskDef, Failed, cause)

}

case class Session(project: String,
                   resources: Seq[ResourceConfig],
                   params: Map[String, String] = Map(),
                   execHistory: Seq[TaskExecResult] = Seq())
