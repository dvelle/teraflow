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
import com.terazyte.flow.config.{JobConfig, ResourceConfig}
import com.terazyte.flow.job.JobController._
import com.terazyte.flow.job.StageExecutor.StartStage
import com.terazyte.flow.steps.ExecutableStep
import com.terazyte.flow.task.actor.BaseActor
import com.terazyte.flow.task.common._

import scala.collection.immutable.Queue

class JobController(project: String, resources: Seq[ResourceConfig], stages: Queue[JobStage]) extends BaseActor {

  val progressActor = context.actorOf(ProgressActor.props(), "task-progress")

  def executionContext(jobContext: JobContext, jobTasks: Queue[JobStage]): Receive = {

    case StartJob =>
      if (jobTasks.nonEmpty) {
        jobTasks.dequeue match {
          case ((stage, taskDefs), queue) =>
            val tasks         = taskDefs.map(_.buildTask(context, stage))
            val stageExecutor = context.actorOf(StageExecutor.props(stage, tasks, jobContext), stage.name)
            stageExecutor ! StartStage()
            context.become(executionContext(jobContext, queue))
        }
      } else {
        self ! ExitJob(0)
      }

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
    executionContext(jobContext = JobContext(self, Session(project, resources)), stages)
  }

}

object JobController {

  type JobTask  = (Stage, Queue[Task])
  type JobStage = (Stage, Queue[ExecutableStep])
  def props(config: JobConfig, resources: Seq[ResourceConfig]): Props = {
    val stages = config.stages.map {
      case (k, v) => (NamedStage(k), Queue(v: _*))
    }.toSeq

    Props(new JobController(config.project, resources, Queue(stages: _*)))
  }

  case class StageCompleted(jobContext: JobContext, exitCode: Int)
  case class ExitJob(exitCode: Int)
  case object StartJob

}
