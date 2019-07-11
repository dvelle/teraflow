package com.terazyte.flow.task

import akka.stream.ActorMaterializer
import com.terazyte.flow.config.remote.RemoteHost
import com.terazyte.flow.docker.DockerContainer
import com.terazyte.flow.job.{Session, TaskExecResult, TaskExecutor}
import com.terazyte.flow.steps.{CopyStep, SparkSubmitStep}

class ExecSparkSubmit(val taskDef: SparkSubmitStep) extends TaskExecutor[SparkSubmitStep]{

  override def execute(session: Session)
    : Either[Throwable, TaskExecResult] = {

    val resource = session.resources.find(_.alias.equalsIgnoreCase(taskDef.target))
    val result = resource map {
      case r: RemoteHost  => r.execCmd(buildSparkCmd(taskDef)).map(_ => TaskExecResult.success(taskDef))
      case c : DockerContainer =>
        implicit val mat = ActorMaterializer()
        c.execCmd(buildSparkCmd(taskDef))(print).map(s => TaskExecResult.success(taskDef,s))
    }

    result.getOrElse({
     Left(new IllegalArgumentException("Only remote host and docker container are supported as an execution target."))
    })

  }

  private def buildSparkCmd(sparkDef: SparkSubmitStep) : String = {
    val confs = sparkDef.conf.foldLeft(""){
      case (x,y) => s"${x} --conf ${y._1}=${y._2}"
    }
     s"spark-submit --class ${sparkDef.className} ${confs} --name ${sparkDef.appName} ${sparkDef.appPath} ${sparkDef.appParams}"
  }

}
