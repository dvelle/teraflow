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
import java.util.UUID

import com.terazyte.flow.job.{Completed, Session, TaskExecResult}

import scala.collection.mutable.ArrayBuffer
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success, Try}

class ExecLocalCmd(taskDef: CmdDef) {
  val logs: ArrayBuffer[String] = ArrayBuffer.empty

  object CmdLogProcessor extends ProcessLogger {

    override def buffer[T](f: => T): T = f

    override def err(s: => String): Unit = {
      logs.append(s)
      println(s)
    }

    override def out(s: => String): Unit = {
      logs.append(s)
      println(s)
    }

  }

  def execute(session: Session): Either[Throwable, TaskExecResult] = {

    val exitCode = Try {

      val cmds = taskDef.cmd.split("#")
      Process(cmds.head)
      val firstCmd = Process(cmds.head, new File(taskDef.workDir))
      val pipedCmd = cmds.tail.foldLeft(firstCmd) { (cmdBuilder, c) =>
        if (c.startsWith(">")) cmdBuilder.#>(new File(c.substring(2).trim))
        else
          cmdBuilder.#|(c)
      }
      val p = pipedCmd.run(true)
      println(s"Started the cmd: ${taskDef.cmd}")
      p.exitValue()
    }

    exitCode match {

      case Failure(ex) => Left(ex)
      case Success(x) if x != 0 =>
        Left(new RuntimeException(logs.mkString("\n")))
      case _ =>
        Right(TaskExecResult(UUID.randomUUID().toString, taskDef, Completed, taskDef.onSuccessMessage()))
    }

  }
}
