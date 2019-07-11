package com.terazyte.flow.services
import java.util.concurrent.ConcurrentHashMap

import com.terazyte.flow.job._

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

  val jobStore = new ConcurrentHashMap[String,JobState]()

  override def save(state: JobState): Future[String] = {
    jobStore.put(state.jobId,state)
    Future.successful(state.jobId)
  }

  private def getFromJobHashMap(id: String) = if(jobStore.containsKey(id)) Some(jobStore.get(id)) else None

  override def getJobStatus(id: String): Future[Option[JobState]] = Future.successful {
    val status = getFromJobHashMap(id)
    status
  }

  override def updateJobStatus(id: String, result: Seq[TaskExecResult]): Future[Boolean] = getFromJobHashMap(id) match {
    case Some(state) =>
      val isRunning = result.find(_.status.equals(Running)).nonEmpty
      val currentStatus = if(isRunning) Running else Completed
      jobStore.put(state.jobId ,state.copy(tasks = result, currentStatus = currentStatus))
      Future.successful(true)
    case None => Future.successful(false)
  }
}
