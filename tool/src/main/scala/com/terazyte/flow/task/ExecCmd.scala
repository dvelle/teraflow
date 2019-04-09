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

import java.io.File

import scala.sys.process._
import scala.sys.process.ProcessLogger
import akka.actor.{ActorContext, Props}
import com.terazyte.flow.job._
import com.terazyte.flow.steps.Step
import com.terazyte.flow.task.common._
import net.jcazevedo.moultingyaml.{DefaultYamlProtocol, YamlValue}

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

case class CmdDef(cmd: String, workDir: String) extends TaskDef(name = s"Execute ${cmd}", tailLogs = true) {

  override def buildTask(context: ActorContext, stage: Stage): Task = {
    val actor = context.actorOf(CmdDef.props(this), "exec-cmd")
    Task(stage, this, actor)
  }

}

object CmdDef extends Step[CmdDef] with DefaultYamlProtocol {
  override val id: String                          = "cmd"
  override def props(step: CmdDef): Props          = Props(new ExecCmd(step))
  override def parseStep(value: YamlValue): CmdDef = ???
}

case class ExecCmd(taskDef: CmdDef) extends TaskExecutor[CmdDef] {

  val logs: ArrayBuffer[String] = ArrayBuffer.empty

  object CmdLogProcessor extends ProcessLogger {

    override def buffer[T](f: => T): T = f

    override def err(s: => String): Unit = {
      logs.append(s)
    }

    override def out(s: => String): Unit = {
      logs.append(s)
    }

  }

  override def execute(session: Session): Either[Throwable, TaskExecResult] = {

    val exitCode = Try {

      val cmds     = taskDef.cmd.split("#")
      val firstCmd = Process(taskDef.cmd, new File(taskDef.workDir))
      val pipedCmd = cmds.tail.foldLeft(firstCmd) { (cmdBuilder, c) =>
        cmdBuilder.#|(c)
      }

      pipedCmd
        .!(ProcessLogger(line => logs.append(line)))
    }

    exitCode match {

      case Failure(ex) => Left(ex)
      case Success(x) if x != 0 =>
        Left(new RuntimeException(logs.mkString("\n")))
      case _ =>
        Right(TaskExecResult(id(), taskDef, Completed, taskDef.onSuccessMessage()))
    }

  }

}
