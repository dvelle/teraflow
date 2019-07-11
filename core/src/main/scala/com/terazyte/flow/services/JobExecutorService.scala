package com.terazyte.flow.services
import akka.actor.ActorSystem
import akka.pattern._
import akka.util.Timeout
import com.terazyte.flow.cluster.Master
import com.terazyte.flow.cluster.Master.{GetJobStatus, SubmitJob}
import com.terazyte.flow.config.{IdGenerator, ResourceConfig}
import com.terazyte.flow.job.{JobState, TaskDef}

import scala.concurrent.duration._
import scala.collection.immutable.Queue
import scala.concurrent.Future

trait JobExecutorService {

  def submit(name: String, resources: Seq[ResourceConfig], tasks: Queue[TaskDef]): Future[String]

  def getJobStatus(jobId: String): Future[Option[JobState]]

}

class LocalJobExecutor(jobStateRepository: JobStateRepository, actorSystem: ActorSystem) extends JobExecutorService {

  val idGenerator      = new IdGenerator {}
  implicit val timeout = Timeout(1.seconds)
  lazy val master      = actorSystem.actorOf(Master.props(jobStateRepository))

  override def submit(name: String, resources: Seq[ResourceConfig], tasks: Queue[TaskDef]): Future[String] = {
    master.ask(SubmitJob(name, resources, tasks, idGenerator)).mapTo[String]
  }
  override def getJobStatus(jobId: String): Future[Option[JobState]] =
    master.ask(GetJobStatus(jobId)).mapTo[Option[JobState]]
}
