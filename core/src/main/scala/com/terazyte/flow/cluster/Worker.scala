package com.terazyte.flow.cluster
import akka.actor.{PoisonPill, Props}
import com.terazyte.flow.cluster.Master.JobCompleted
import com.terazyte.flow.cluster.Worker.{GetWorkStatus, InitJob, ShutdownJob, TaskCompleted}
import com.terazyte.flow.config.{Resource, ResourceConfig}
import com.terazyte.flow.job.{ExecCommand, Session, TaskDef, TaskExecResult}
import com.terazyte.flow.services.JobStateRepository
import com.terazyte.flow.task.actor.BaseActor

import scala.collection.immutable.Queue

class Worker(name: String,
             resources: Seq[ResourceConfig],
             tasks: Queue[TaskDef],
             jobStateRepository: JobStateRepository)
    extends BaseActor {

  override def receive: Receive = runTasks(tasks, Session(name, resources))

  def execTasks(tasks: Queue[TaskDef], session: Session)(ifEmpty: Session => Unit) = {
    if (tasks.nonEmpty) {
      tasks.dequeue match {
        case (step, queue) =>
          val task = step.buildTask(context)
          log.info(s"Running the task : ${task.taskDef.taskName}")
          task.actor ! ExecCommand(session, self)
          context.become(runTasks(queue, session))
      }
    } else ifEmpty(session)
  }

  def runTasks(pendingTasks: Queue[TaskDef], session: Session): Receive = {
    case InitJob =>
      // Do something
      execTasks(pendingTasks, session) { updatedSession =>
        context.parent ! JobCompleted(self.path.toStringWithoutAddress, updatedSession.execHistory)
      }

    case TaskCompleted(result) =>
      val updatedSession = session.copy(execHistory = session.execHistory :+ result)
      execTasks(pendingTasks, updatedSession) { sess =>
        context.parent ! JobCompleted(self.path.toStringWithoutAddress, sess.execHistory)
      }

    case GetWorkStatus => sender() ! session.execHistory

    case ShutdownJob =>
      context.children.foreach(_ ! PoisonPill)
    sender() ! name


  }

}

object Worker {

  def props(name: String,
            resources: Seq[ResourceConfig],
            tasks: Queue[TaskDef],
            jobStateRepository: JobStateRepository): Props =
    Props(new Worker(name, resources, tasks, jobStateRepository))

  sealed trait WorkerMessage
  case object InitJob                              extends WorkerMessage
  case class TaskCompleted(result: TaskExecResult) extends WorkerMessage
  case object GetWorkStatus extends WorkerMessage
  case object ShutdownJob extends WorkerMessage

}
