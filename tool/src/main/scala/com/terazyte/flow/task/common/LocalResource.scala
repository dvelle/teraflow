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

package com.terazyte.flow.task.common

import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

import com.terazyte.flow.config.ResourceConfig
import com.terazyte.flow.job.TaskExecResult
import com.terazyte.flow.steps.{BashScript, ScriptStep}

import scala.collection.mutable.ArrayBuffer
import scala.sys.process._
import scala.util.Try

class LocalResource extends ResourceConfig {
  override val alias: String = "local"

  val logs: ArrayBuffer[String] = ArrayBuffer.empty

  class CmdLogProcessor extends ProcessLogger {

    val logQueue = new ConcurrentLinkedQueue[String]()

    def getString(): String = {
      val s = new StringBuilder
      while (!logQueue.isEmpty) {
        val logLine = logQueue.remove()
        s.append(logLine)
      }
      s.mkString
    }

    override def buffer[T](f: => T): T = f

    override def err(s: => String): Unit = {
      println(s)
      logQueue.offer(s + "\n")
    }

    override def out(s: => String): Unit = {
      println(s)
      logQueue.offer(s + "\n")
    }

  }

  private def getResult(exitCode: Int,
                        script: BashScript,
                        logProcessor: CmdLogProcessor): Either[Throwable, TaskExecResult] = {
    if (exitCode != 0) {
      Left(new IOException(logs.mkString("\n") + s"\n Exit Code: ${exitCode}"))
    } else {
      val taskDef = ScriptStep("bash", script)
      Right(TaskExecResult.success(taskDef, detail = "Script execution completed."))
    }
  }

  def exec(script: BashScript): Either[Throwable, TaskExecResult] = {

    Try {
      val logProcessor = new CmdLogProcessor()
      script.value match {
        case Some(v) =>
          val path = FileHelper.writeTo(v)
          FileHelper.setExecutable(path)
          val code = s"bash ${path}".!(logProcessor)
          getResult(code, script, logProcessor)
        case None =>
          script.path match {
            case Some(p) =>
              FileHelper.setExecutable(p)
              val code = s"bash ${p}".!(logProcessor)
              getResult(code, script, logProcessor)
            case None =>
              Left(new IllegalArgumentException("Script path needs to be supplied"))
          }
      }
    }.getOrElse(Left(new IOException("Failed to execute the script")))
  }

}
