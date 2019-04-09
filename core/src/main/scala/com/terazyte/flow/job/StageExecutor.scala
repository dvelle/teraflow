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

import akka.actor.Props
import com.terazyte.flow.job.JobController.StageCompleted
import com.terazyte.flow.job.StageExecutor.{ExitStage, StartStage, TaskCompleted}
import com.terazyte.flow.task.actor.BaseActor
import com.terazyte.flow.task.common.ProgressActor.{ShowFailure, ShowProgress, ShowSkipped, ShowSuccess}
import com.terazyte.flow.task.common._

import scala.collection.immutable.Queue

sealed trait Stage {
  val name: String
}
case class NamedStage(name: String) extends Stage

case object Build extends Stage {
  override val name: String = "Build"
}
case object Install extends Stage {
  override val name: String = "Install"
}

class StageExecutor(stage: Stage, tasks: Queue[Task], jobContext: JobContext) extends BaseActor {

  StageExecutor.show(stage, jobContext.session.project)

  val progressActor = context.actorOf(ProgressActor.props(), "task-progress")

  def executeTasks(jobContext: JobContext, tasks: Queue[Task]): Receive = {

    case StartStage(execResults) =>
      if (tasks.nonEmpty) {
        tasks.dequeue match {
          case (task, queue) =>

            progressActor ! ShowProgress(task.taskDef.name,task.taskDef.tailLogs)
            task.actor ! ExecCommand(jobContext.session, self)
            context.become(
              executeTasks(jobContext.copy(session = jobContext.session.copy(execHistory = execResults ++ execResults)),
                           queue))
        }
      } else {
        self ! ExitStage(0)
      }

    case TaskCompleted(result) =>
      result.status match {
        case Completed => progressActor ! ShowSuccess(result.message, result.detail)
        case Failed =>
          progressActor ! ShowFailure(result.taskDef.name, result.message)
        case Skipped => progressActor ! ShowSkipped(result.message)
        case _       => progressActor ! ShowSuccess(result.message)
      }
      self ! StartStage(Seq(result))

    case ExitStage(exitCode) =>
      context.parent ! StageCompleted(jobContext, exitCode)

  }

  override def receive: Receive = executeTasks(jobContext, tasks)

}

object StageExecutor {

  def show(stage: Stage, project: String): Unit = {
    println("--------------------------------------------------------------------------------------")
    println(s"${stage.name} ${project}")
    println("--------------------------------------------------------------------------------------")
  }

  def props(stage: Stage, tasks: Queue[Task], jobContext: JobContext): Props =
    Props(new StageExecutor(stage, tasks, jobContext))
  case class TaskCompleted(result: TaskExecResult)
  case class ExitStage(exitCode: Int)
  case class StartStage(execResults: Seq[TaskExecResult] = Seq())

}
