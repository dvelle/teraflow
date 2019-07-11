package com.terazyte.flow.job

case class JobState(name: String, jobId: String, currentStatus: RunStatus, tasks: Seq[TaskExecResult], startedAt: Long)

sealed trait RunStatus {
  def toReadable: String = {
    val objectStr = this.toString
    "_?[A-Z][a-z\\d]+".r
      .findAllMatchIn(objectStr)
      .map(_.group(0).toLowerCase)
      .mkString(" ")
  }

}
case object Starting   extends RunStatus
case object Running    extends RunStatus
case object Skipped    extends RunStatus
case object Stopped    extends RunStatus
case object NotRunning extends RunStatus
case object Completed  extends RunStatus
case object Started    extends RunStatus
case object NotStarted extends RunStatus
case object Failed     extends RunStatus
