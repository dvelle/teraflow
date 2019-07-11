package com.terazyte.flow.services
import com.terazyte.flow.job.{JobState, TaskExecResult}

import scala.collection.mutable
import scala.concurrent.Future

trait JobStateRepository {

  /**
    * Saves the Job's state and returns the job Id
    * @param state
    * @return
    */
  def save(state: JobState): Future[String]

  def getJobStatus(id: String): Future[Option[JobState]]

  def updateJobStatus(id: String, execResult: Seq[TaskExecResult]): Future[Boolean]

}

class InMemoryJobState extends JobStateRepository {

  val jobStore = mutable.Map[String, JobState]()

  override def save(state: JobState): Future[String] = {
    jobStore += (state.jobId -> state)
    Future.successful(state.jobId)
  }

  override def getJobStatus(id: String): Future[Option[JobState]] = Future.successful(jobStore.get(id))

  override def updateJobStatus(id: String, result: Seq[TaskExecResult]): Future[Boolean] = jobStore.get(id) match {
    case Some(state) =>
      jobStore += (state.jobId -> state.copy(tasks = result))
      Future.successful(true)
    case None => Future.successful(false)
  }
}
