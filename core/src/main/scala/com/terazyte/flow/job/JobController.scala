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

import akka.actor.CoordinatedShutdown.JvmExitReason
import akka.actor.{ActorContext, ActorRef, CoordinatedShutdown, Props}
import com.terazyte.flow.cluster.Worker.TaskCompleted
import com.terazyte.flow.config.{JobConfig, ResourceConfig}
import com.terazyte.flow.job.JobController._
import com.terazyte.flow.task.actor.BaseActor
import com.terazyte.flow.task.common.ProgressActor.{ShowFailure, ShowProgress, ShowSkipped, ShowSuccess}
import com.terazyte.flow.task.common._

import scala.collection.immutable.Queue

class JobController(project: String, resources: Seq[ResourceConfig], tasks: Queue[TaskDef]) extends BaseActor {

  val progressActor = context.actorOf(ProgressActor.props(), "task-progress")

  def executionContext(jobContext: JobContext, jobTasks: Queue[TaskDef]): Receive = {

    case StartJob(historyResults) =>
      if (jobTasks.nonEmpty) {
        jobTasks.dequeue match {
          case (executableStep, queue) =>
            val task = executableStep.buildTask(context)
            progressActor ! ShowProgress(task.taskDef.taskName, task.taskDef.tailLogs)
            task.actor ! ExecCommand(jobContext.session, self)
            context.become(
              executionContext(
                jobContext.copy(
                  session = jobContext.session.copy(execHistory = jobContext.session.execHistory ++ historyResults)),
                queue))
        }
      } else {
        self ! ExitJob(0)
      }

    case TaskCompleted(result) =>
      result.status match {
        case Completed => progressActor ! ShowSuccess(result.message, result.detail)
        case Failed =>
          progressActor ! ShowFailure(result.taskDef.taskName, result.message)
        case Skipped => progressActor ! ShowSkipped(result.message)
        case _       => progressActor ! ShowSuccess(result.message)
      }
      self ! StartJob(Seq(result))

    case StageCompleted(jobContext, exitCode) =>
      if (exitCode != 0) {
        self ! ExitJob(exitCode)
      } else {
        self ! StartJob
      }

    case ExitJob(exitCode) =>
      stopChildren()
      CoordinatedShutdown(context.system).run(JvmExitReason).onComplete { _ =>
        sys.exit(exitCode)
      }

  }

  override def receive: Receive = {
    executionContext(JobContext(self, Session(project, resources)), tasks)
  }

}

object JobController {

  type JobTask  = (Stage, Queue[Task])
  type JobStage = (Stage, Queue[TaskDef])
  def props(config: JobConfig, resources: Seq[ResourceConfig]): Props = {

    Props(new JobController(config.project, resources, Queue(config.tasks: _*)))
  }

  case class StageCompleted(jobContext: JobContext, exitCode: Int)
  case class ExitJob(exitCode: Int)
  case class StartJob(history: Seq[TaskExecResult] = Seq())

}
