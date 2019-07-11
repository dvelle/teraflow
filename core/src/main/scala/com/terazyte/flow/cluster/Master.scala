package com.terazyte.flow.cluster
import akka.actor.Props
import com.terazyte.flow.cluster.Master._
import com.terazyte.flow.cluster.Worker.{GetWorkStatus, InitJob, ShutdownJob}
import com.terazyte.flow.config.{IdGenerator, JobConfig, ResourceConfig}
import com.terazyte.flow.job.{JobState, Running, TaskDef, TaskExecResult}
import com.terazyte.flow.services.JobStateRepository
import com.terazyte.flow.task.actor.BaseActor
import akka.pattern._

import scala.collection.immutable.Queue

/**
  * A stateless master, that manages the workers for running the job
  * @param jobStateRepository
  */
class Master(jobStateRepository: JobStateRepository, resources: Seq[ResourceConfig]) extends BaseActor {

  def running(workers: Seq[String]): Receive = {
    case SubmitJob(name, steps, idGenerator) =>
      //generate a job Id and associate it with a worker
      val workerId = idGenerator.generate()
      val worker =
        context.actorOf(Worker.props(name, resources, steps, jobStateRepository), workerId)
      worker ! InitJob
      val tasksCurrentState = steps.map(s => TaskExecResult.starting(s))
      jobStateRepository.save(JobState(name, workerId, Running, tasksCurrentState, -1L)) pipeTo sender()
      context.become(running(workers :+ workerId))

    case GetJobStatus(id) =>
      //Query the worker for the job status
      jobStateRepository.getJobStatus(id)

    case CancelJob(id) =>
      //Cancel the pending tasks
      val optWorker = workers.find(_.equalsIgnoreCase(id)).flatMap(context.child(_))
      optWorker match {
        case Some(w) => w.ask(ShutdownJob).mapTo[String] pipeTo (sender())
        case None    => sender() ! "Job Worker not found"
      }

    case JobCompleted(id, report) =>
      jobStateRepository.updateJobStatus(id, report)
      context.become(running(workers.filterNot(_.equals(id))))

  }

  override def receive: Receive = running(Seq())

}

object Master {

  def props(jobStateRepository: JobStateRepository, resources: Seq[ResourceConfig]): Props =
    Props(new Master(jobStateRepository, resources))
  sealed trait MasterMessage

  case class SubmitJob(name: String, steps: Queue[TaskDef], idGenerator: IdGenerator) extends MasterMessage
  case class JobCompleted(id: String, report: Seq[TaskExecResult])                    extends MasterMessage
  case class GetJobStatus(id: String)                                                 extends MasterMessage
  case class CancelJob(id: String)                                                    extends MasterMessage

}
