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

import java.io.{InputStream, InputStreamReader}
import java.util.Scanner

import akka.stream.ActorMaterializer
import com.terazyte.flow.config.remote.RemoteHost
import com.terazyte.flow.docker.DockerContainer
import com.terazyte.flow.job._
import com.terazyte.flow.steps.{CopyStep, RemoteCmdStep}

import scala.collection.mutable.ArrayBuffer

case class ExecRemoteCmd(taskDef: RemoteCmdStep) extends TaskExecutor[RemoteCmdStep] {

  type HandleOp = String => Unit
  val cmdLogs: ArrayBuffer[String] = ArrayBuffer.empty

  val defaultHandler: HandleOp = (s: String) => {
    cmdLogs.append(s)
  }
  override def execute(session: Session): Either[Throwable, TaskExecResult] = {

    val secret = taskDef.target.flatMap(t => session.resources.find(_.alias.equalsIgnoreCase(t)))
    val result = secret map {
      case x: RemoteHost => x.execCmd(taskDef.value).map(_ => TaskExecResult.success(taskDef, id()))
      case x: DockerContainer =>
        implicit val mat = ActorMaterializer()
        x.execCmd(taskDef.value).map(s => TaskExecResult.success(taskDef,id(),s))
    }

    result.getOrElse({
      val cmdDef = CmdDef(taskDef.value, System.getProperty("user.home"))
     new ExecLocalCmd(cmdDef).execute(session)
    })
  }

  class StreamLog(stream: InputStream, opHandler: HandleOp) extends Thread {

    var properties: Map[String, Any] = Map.empty

    override def run(): Unit = {
      val scanner = new Scanner(stream, "UTF-8").useDelimiter("\n")
      while (scanner.hasNext) {
        opHandler(scanner.next())

      }
      scanner.close()
    }

  }

}
