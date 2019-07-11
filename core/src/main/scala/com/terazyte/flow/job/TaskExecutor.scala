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

import akka.actor.{ActorContext, ActorRef}
import com.terazyte.flow.cluster.Worker.TaskCompleted
import com.terazyte.flow.config.ResourceConfig
import com.terazyte.flow.task.actor.BaseActor

trait TaskExecutor[T <: TaskDef] extends BaseActor {
  val taskDef: T
  def id(): String = UUID.randomUUID().toString

  def execute(session: Session): Either[Throwable, TaskExecResult]

  override def receive: Receive = {

    case ExecCommand(session, manager) =>
      val execResult = execute(session) match {
        case Left(ex) =>
          TaskExecResult(taskDef, Failed, taskDef.onFailureMessage(ex))
        case Right(result) =>
          result
      }

      manager ! TaskCompleted(execResult)

  }

}
case class ExecCommand(session: Session, manager: ActorRef)

abstract class TaskDef(val taskName: String, val tailLogs: Boolean = false) {

  def buildTask(context: ActorContext): Task

  def onSuccessMessage(): String = s"${taskName} completed"

  def onSkipMessage(): String = s"${taskName} skipped"

  def onFailureMessage(throwable: Throwable): String =
    s"Failure cause: ${throwable.getMessage()}"

}

case class Task(taskDef: TaskDef, actor: ActorRef)

case class TaskExecResult(taskDef: TaskDef, status: RunStatus, message: String, detail: String = "")
object TaskExecResult {

  def success(taskDef: TaskDef, detail: String = ""): TaskExecResult =
    TaskExecResult(taskDef, Completed, s"${taskDef.taskName} Completed", detail)

  def starting(taskDef: TaskDef): TaskExecResult = TaskExecResult(taskDef, NotStarted, "Task has not yet started")

  def failed(taskDef: TaskDef, id: String, cause: String) = TaskExecResult(taskDef, Failed, cause)

}

case class Session(project: String,
                   resources: Seq[ResourceConfig],
                   params: Map[String, String] = Map(),
                   execHistory: Seq[TaskExecResult] = Seq())
