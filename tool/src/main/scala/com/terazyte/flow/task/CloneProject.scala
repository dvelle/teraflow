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

package com.terazyte.flow.task

import java.io.{Console, File}
import java.util.UUID

import akka.actor.{ActorContext, Props}
import com.terazyte.flow.job._
import com.terazyte.flow.steps.Step
import com.terazyte.flow.task.common._
import net.jcazevedo.moultingyaml.{DefaultYamlProtocol, YamlValue}
import org.eclipse.jgit.api.Git

import scala.util.Try

case class SourceProject(project: String, workDir: String, uri: Option[String] = None)
    extends TaskDef(
      taskName = s"Clone ${project}"
    ) {

  override def onSkipMessage(): String =
    s"Skipped cloning of the project, ${taskName}"

  override def buildTask(context: ActorContext): Task = ???
}

object SourceProject extends Step[SourceProject] with DefaultYamlProtocol {
  override val id: String                                 = "clone"
  override def props(step: SourceProject): Props          = Props(new CloneProject(step))
  override def parseStep(value: YamlValue): SourceProject = ???
}

case class CloneProject(taskDef: SourceProject) extends TaskExecutor[SourceProject] {

  override def execute(session: Session): Either[Throwable, TaskExecResult] = {

    taskDef.uri match {
      case Some(u) => clone(u)
      case None =>
        Right(TaskExecResult( taskDef, Skipped, taskDef.onSkipMessage()))
    }

  }

  private def clone(uri: String): Either[Throwable, TaskExecResult] = {

    val opsResult = Try {
      Git
        .cloneRepository()
        .setURI(uri)
        .setDirectory(new File(taskDef.workDir))
        .call()
    }
    opsResult.toEither.map(_ =>
      TaskExecResult(taskDef, Completed, taskDef.onSuccessMessage()))
  }

}

//object CloneProject extends TaskBuilder[SourceProject] {
//
//  override def build(stage: Stage, context: ActorContext, taskDef: SourceProject): Task = {
//
//    val actor = context.actorOf(props(taskDef), "clone-project")
//    Task(stage, taskDef, actor)
//  }
//
//  def props(taskDef: SourceProject): Props =
//    Props(new CloneProject(taskDef))
//}

object CloneProject extends App {

  val console = System.console()
  val pass    = console.readPassword()

  println(pass.mkString)
}
