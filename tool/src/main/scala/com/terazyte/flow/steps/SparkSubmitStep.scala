package com.terazyte.flow.steps

import akka.actor.{ActorContext, Props}
import com.terazyte.flow.job.{Stage, Task, TaskDef}
import com.terazyte.flow.task.{ExecRemoteCopy, ExecSparkSubmit}
import net.jcazevedo.moultingyaml.{DefaultYamlProtocol, YamlValue}

case class SparkSubmitStep(appName: String,
                      className: String,
                      appPath: String,
                      appParams: String,
                      target: String,
                      conf: Map[String, String])
    extends TaskDef(taskName = s"Run spark application, ${appName} in ${target}", tailLogs = true) {

  override def buildTask(context: ActorContext,
                         ): Task = {
    val actor = context.actorOf(SparkSubmitStep.props(this))
    Task(this, actor)
  }

}

object SparkSubmitStep extends Step[SparkSubmitStep] with DefaultYamlProtocol {

  implicit val sparkSubmitStep = yamlFormat6(SparkSubmitStep.apply)

  override val id: String = "sparkSubmit"

  def props(step: SparkSubmitStep): Props = Props(new ExecSparkSubmit(step))

  override def parseStep(value: YamlValue): SparkSubmitStep = value.convertTo[SparkSubmitStep]

}
